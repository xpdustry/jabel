package com.github.bsideup.jabel;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.sun.source.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.jvm.PoolConstant.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;


/**
 * Replaces {@link SwitchBootstraps#typeSwitch} and {@link SwitchBootstraps#enumSwitch} by a ternary chains.
 * <p><br>
 * Another method to adapt switches, by letting the compiler doing it's job,
 * and adapting the condition according to the switch restart behavior.
 * <p>
 * This is not very efficient because SwitchBootstraps will generate a method that uses another switch
 * to easily restart at the next case. <br>
 * In fact, it's better to rewrite entirely the switch, because this "restart" behavior is poorly designed. <br>
 * And can be better, avoiding any kind of "restart" system and dynamic method generation,
 * just by adding a little more bytecode at compilation, as shown in {@link SwitchRetrofittingTaskListener}.
 * <p>
 * Also this implementation is very sensitive, as it needs to unwrap {@link LoadableConstant}s,
 * that can be implemented a lot of ways.
 */
public class SwitchRetrofittingTaskListener2 implements TaskListener{
    // region compiler compatibility
    private static boolean SWITCH_BOOTSTRAPS_PRESENT;
    {
        try{
            Symtab.class.getDeclaredField("switchBootstrapsType");
            SWITCH_BOOTSTRAPS_PRESENT = true;
        }catch(Exception ignored){}
    }

    private static boolean isSwitchExpression(JCTree tree){
        return tree != null && tree.getClass().getSimpleName().equals("JCSwitchExpression");
    }

    private static boolean isLetExpr(JCTree tree){
        return tree != null && tree.getClass().getSimpleName().equals("LetExpr");
    }

    // end region
    // region reflection

    static Method resolveBinary;
    static {
        try{
            resolveBinary = Operators.class.getDeclaredMethod(
                "resolveBinary",
                JCDiagnostic.DiagnosticPosition.class,
                Tag.class,
                Type.class,
                Type.class
            );
            resolveBinary.setAccessible(true);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /** Forced to use reflection here because everything using this method is package-private. */
    static OperatorSymbol resolveBinary(Operators ops, JCDiagnostic.DiagnosticPosition pos,
                                        Tag tag, Type op1, Type op2){
        if(resolveBinary != null){
            try{
                return (OperatorSymbol)resolveBinary.invoke(ops, pos, tag, op1, op2);
            }catch(Exception e){
                //TODO: debug
                System.err.println("[jabel] Failed to find operator " + tag + " with " + op1 + "," + op2);
            }
        }
        return null;
    }

    // end region

    final TreeMaker make;
    final Symtab syms;
    final Names names;
    final Types types;
    final Operators ops;

    MethodSymbol object_equals, enum_ordinal;
    // Cached operators
    final OperatorSymbol opEQ, opNE, opLE, opAND;


    public SwitchRetrofittingTaskListener2(Context context){
        make = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        ops = Operators.instance(context);

        JCLiteral dummy = makeNull();
        opEQ = resolveBinary(ops, dummy, Tag.EQ, syms.objectType, syms.objectType);
        opNE = resolveBinary(ops, dummy, Tag.NE, syms.objectType, syms.objectType);
        opLE = resolveBinary(ops, dummy, Tag.LE, syms.intType,  syms.intType);
        opAND = resolveBinary(ops, dummy, Tag.AND, syms.booleanType, syms.booleanType);
    }

    @Override
    public void started(TaskEvent e){
        if(!SWITCH_BOOTSTRAPS_PRESENT) return;
        if(e.getKind() != TaskEvent.Kind.GENERATE) return;
        if(!(e.getCompilationUnit() instanceof JCCompilationUnit)) return;
        new SwitchFixer().translate((JCCompilationUnit)e.getCompilationUnit());
    }

    @Override
    public void finished(TaskEvent e){
        if(!SWITCH_BOOTSTRAPS_PRESENT) return;
        if(e.getKind() != TaskEvent.Kind.ANALYZE) return;
        injectSwitchBootstraps();
    }


    public class SwitchFixer extends TreeTranslator{
        @Override
        public void visitSwitch(JCSwitch tree){
            super.visitSwitch(tree);
            JCExpression fixed = replaceSwitchSelector(tree.selector);
            if(fixed != null) tree.selector = fixed;
        }

        /** Manually translate {@link JCSwitchExpression} and {@link LetExpr} to keep source compatibility. */
        @Override
        public <T extends JCTree> T translate(T tree){
            if(tree == null) return null;
            if(isSwitchExpression(tree)){
                JCSwitchExpression se = (JCSwitchExpression)tree;
                se.selector = translate(se.selector);
                se.cases = translate(se.cases);
                JCExpression fixed = replaceSwitchSelector(se.selector);
                if(fixed != null) se.selector = fixed;
                return tree;
            }
            if(isLetExpr(tree)){
                LetExpr let = (LetExpr)tree;
                let.defs = translate(let.defs);
                let.expr = translate(let.expr);
                return tree;
            }
            return super.translate(tree);
        }
    }


    /** Registers virtual symbol of missing types to make the compiler happy. */
    public void injectSwitchBootstraps(){
        ClassSymbol cs = (ClassSymbol)syms.switchBootstrapsType.tsym;
        if(cs == null) return;
        if(cs.classfile != null) {
            // Compiling to a recent version, no modification needed
            SWITCH_BOOTSTRAPS_PRESENT = false;
            return;
        }
        if(cs.members_field != null && !cs.members_field.isEmpty()) return;

        // java.lang.runtime.SwitchBootstraps.[typeSwitch/enumSwitch](Lookup, String, MethodType, Object...)
        injectClass(cs, Flags.FINAL);
        MethodType mt = new MethodType(
            List.of(
                syms.methodHandleLookupType,
                syms.stringType,
                syms.methodTypeType,
                new ArrayType(syms.objectType, syms.arrayClass)
            ),
            syms.objectType,
            List.nil(),
            syms.methodClass
        );
        addMethod(cs, names.typeSwitch, mt, Flags.VARARGS);
        addMethod(cs, names.enumSwitch, mt, Flags.VARARGS);

        // java.lang.constant.ClassDesc.of(String)
        cs = (ClassSymbol)syms.classDescType.tsym;
        injectClass(cs, Flags.INTERFACE);
        mt = new MethodType(List.of(syms.stringType), cs.type, List.nil(), syms.methodClass);
        addMethod(cs, names.of, mt, 0);

        // java.lang.Enum$EnumDesc.of()
        cs = (ClassSymbol)syms.enumDescType.tsym;
        injectClass(cs, Flags.FINAL);
        mt = new MethodType(List.of(syms.classDescType, syms.stringType), cs.type, List.nil(), syms.methodClass);
        addMethod(cs, names.of, mt, 0);

        // java.lang.invoke.ConstantBootstraps.invoke(Lookup, String, Class<?>, MethodHandle, Object...)
        cs = (ClassSymbol)syms.constantBootstrapsType.tsym;
        injectClass(cs, Flags.FINAL);
        mt = new MethodType(
            List.of(
                syms.methodHandleLookupType,
                syms.stringType,
                syms.classType,
                syms.methodHandleType,
                new ArrayType(syms.objectType, syms.arrayClass)
            ),
            syms.objectType,
            List.nil(),
            syms.methodClass
        );
        addMethod(cs, names.invoke, mt, 0);
    }

    /** Fills a ClassSymbol with a minimal ClassType and an empty member scope. */
    private void injectClass(ClassSymbol cs, long addFlags){
        cs.flags_field = Flags.PUBLIC | addFlags;
        ClassType ct = new ClassType(Type.noType, List.nil(), cs);
        ct.supertype_field = syms.objectType;
        cs.type = ct;
        cs.members_field = Scope.WriteableScope.create(cs);
        cs.completer = Completer.NULL_COMPLETER;
    }

    private void addMethod(ClassSymbol cs, Name name, MethodType sig, long addFlags){
        long flags = Flags.PUBLIC | Flags.STATIC | addFlags;
        cs.members_field.enter(new MethodSymbol(flags, name, sig, cs));
    }

    /**
     * @return the ternary-chain replacement if {@code selector} is a {@code typeSwitch}
     *         or {@code enumSwitch} dynamic invocation; otherwise {@code null}.
     */
    public JCExpression replaceSwitchSelector(JCExpression selector){
        JCExpression inner = unwrap(selector);
        if(!(inner instanceof JCMethodInvocation)) return null;

        JCMethodInvocation call = (JCMethodInvocation)inner;
        Symbol sym = methodSym(call.meth);
        if(!(sym instanceof DynamicMethodSymbol)) return null;

        DynamicMethodSymbol dms = ((DynamicMethodSymbol)sym);
        //TODO: check for Symtab#switchBootstrapsType too?
        if(sym.name != names.typeSwitch && sym.name != names.enumSwitch) return null;
        if(dms.staticArgs == null) return null;
        if(call.args.size() < 2) return null;

        make.at(selector.pos);
        JCExpression sel = call.args.head;
        JCExpression restart = call.args.tail.head;
        Type enumType = sym.name == names.enumSwitch ? sel.type : null;
        JCExpression ternary;

        if(enumType != null){
            if(enum_ordinal == null) enum_ordinal = findMethod(syms.enumSym.type, "ordinal", 0);
            JCFieldAccess msel = make.Select(sel, enum_ordinal.name);
            msel.sym = enum_ordinal;
            msel.type = enum_ordinal.type;
            ternary = make.Apply(List.nil(), msel, List.nil()).setType(syms.intType);

        }else{
            ternary = make.Literal(dms.staticArgs.length);
            boolean noRestart = isLiteralZero(restart);

            for(int i = dms.staticArgs.length - 1; i >= 0; i--){
                Object v = unwrapLoadable(dms.staticArgs[i]);
                JCExpression cond = buildArgCondition(copy(sel), v, enumType);
                if(cond == null) continue;
                if(!noRestart){
                    cond = makeBinary(Tag.AND, makeBinary(Tag.LE, copy(restart), make.Literal(i)), cond);
                }
                ternary = make.Conditional(cond, make.Literal(i), ternary).setType(syms.intType);
            }
        }

        if(hasNullLabel(dms.staticArgs)) return ternary;
        return make.Conditional(
            makeBinary(Tag.EQ, copy(sel), makeNull()),
            make.Literal(-1),
            ternary
        ).setType(syms.intType);
    }

    public JCExpression buildArgCondition(JCExpression sel, Object arg, Type enumType) {
        if(arg == null || (arg instanceof Type && ((Type)arg).hasTag(TypeTag.BOT))){
            return makeBinary(Tag.EQ, sel, makeNull());

        }else if(arg instanceof Type){
            return make.TypeTest(sel, make.Type((Type)arg)).setType(syms.booleanType);

        }else if(arg instanceof String[]){
            String[] ref = (String[])arg;
            ClassSymbol ec = syms.enterClass(syms.java_base, names.fromString(ref[0]));
            Symbol cs = lookupMember(ec, names.fromString(ref[1]));
            if(cs == null){
                System.err.println("[jabel] Cannot resolve EnumDesc: " + ref[0] + "." + ref[1]);
                return null;
            }
            JCFieldAccess field = make.Select(make.QualIdent(ec), cs.name);
            field.sym = cs;
            field.type = cs.type;
            return makeBinary(Tag.EQ, sel, field);

        }else if(enumType != null && arg instanceof String){
            ClassSymbol ec = (ClassSymbol)enumType.tsym;
            Symbol cs = lookupMember(ec, names.fromString((String)arg));
            if(cs == null){
                System.err.println("[jabel] Cannot resolve enum constant: " + enumType + "." + arg);
                return null;
            }
            JCFieldAccess field = make.Select(make.QualIdent(ec), cs.name);
            field.sym = cs;
            field.type = cs.type;
            return makeBinary(Tag.EQ, sel, field);

        }else{
            //TODO: optimize literal checks?
            if(object_equals == null) object_equals = findMethod(syms.objectType, "equals", 1);
            JCFieldAccess msel = make.Select(make.Literal(arg), object_equals.name);
            msel.sym = object_equals;
            msel.type = object_equals.type;
            JCMethodInvocation app = make.Apply(List.nil(), msel, List.of(sel));
            app.type = syms.booleanType;
            return app;
        }
    }

    private static boolean hasNullLabel(Object[] args){
        for(Object a : args){
            if(a == null || (a instanceof Type && ((Type)a).hasTag(TypeTag.BOT))) return true;
        }
        return false;
    }

    private MethodSymbol findMethod(Type owner, String name, int paramCount){
        Name n = names.fromString(name);
        for(Symbol s : owner.tsym.members().getSymbolsByName(n)){
            if(!(s instanceof MethodSymbol)) continue;
            MethodSymbol ms = (MethodSymbol)s;
            if(ms.type.getParameterTypes().size() == paramCount) return ms;
        }
        return null;
    }

    private Symbol lookupMember(ClassSymbol cs, Name n){
        for(Symbol s : cs.members().getSymbolsByName(n)) return s;
        return null;
    }

    private Object unwrapLoadable(Object arg){
        if(arg instanceof Type) return arg;
        if(arg instanceof Dynamic){
            LoadableConstant[] sa = ((DynamicVarSymbol)arg).staticArgs();
            // EnumDesc structure: [handle, ClassDesc_DynVar, "CONST_NAME"]
            if(sa.length == 3 && sa[1] instanceof DynamicVarSymbol){
                LoadableConstant[] inner = ((DynamicVarSymbol)sa[1]).staticArgs();
                // ClassDesc structure: [handle, "flat.ClassName"]
                if(inner.length == 2){
                    Object cls = inner[1].poolKey(types);
                    Object cst = sa[2].poolKey(types);
                    if(cls instanceof String && cst instanceof String){
                        return new String[] {(String)cls, (String)cst};
                    }
                }
            }
            System.err.println("[jabel] Unknown DynamicVarSymbol structure, staticArgs=" + Arrays.deepToString(sa));
            return null;
        }
        if(arg instanceof LoadableConstant){
            Object key = ((LoadableConstant)arg).poolKey(types);
            if(key != arg) return unwrapLoadable(key); // recurse for nested wrappers
        }
        return arg;
    }

    private JCExpression copy(JCExpression e){
        if(e instanceof JCIdent){
            JCIdent c = make.Ident(((JCIdent)e).sym);
            c.type = e.type;
            return c;
        }else if(e instanceof JCLiteral){
            JCLiteral s = (JCLiteral)e, c = make.Literal(s.typetag, s.value);
            c.type = s.type;
            return c;
        }else if(isLetExpr(e)){
            return copy(((LetExpr)e).expr);
        }
        return e;
    }

    private static Symbol methodSym(JCExpression meth){
        if(meth instanceof JCIdent) return ((JCIdent)meth).sym;
        if(meth instanceof JCFieldAccess) return ((JCFieldAccess)meth).sym;
        return null;
    }

    private static JCExpression unwrap(JCExpression e){
        while(e instanceof JCParens) e = ((JCParens)e).expr;
        return e;
    }

    private static boolean isLiteralZero(JCExpression e){
        e = unwrap(e);
        return e instanceof JCLiteral && ((Integer)0).equals(((JCLiteral)e).value);
    }

    /** @see TransPatterns#makeBinary */
    private JCBinary makeBinary(Tag optag, JCExpression lhs, JCExpression rhs){
        JCBinary tree = make.Binary(optag, lhs, rhs);
        switch(optag){
            case EQ: tree.operator = opEQ; break;
            case NE: tree.operator = opNE; break;
            case LE: tree.operator = opLE; break;
            case AND: tree.operator = opAND; break;
            default: tree.operator = resolveBinary(ops, tree, optag, lhs.type, rhs.type); break;
        }
        tree.type = tree.operator == null ? null : tree.operator.type.getReturnType();
        return tree;
    }

    private JCLiteral makeNull(){
        return make.Literal(TypeTag.BOT, null).setType(syms.botType);
    }
}