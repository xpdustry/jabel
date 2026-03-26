package com.github.bsideup.jabel;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.lang.reflect.*;
import java.util.*;

import static com.github.bsideup.jabel.RecordPatternHelper.*;


//TODO: the class need some reworking since JCSwitchExpression doesn't exists in Java 12-
/**
 * Transforms modern switch constructs (Java 17+) into Java 8 compatible code.
 * <p>
 * This will extract {@code null} case to a {@code if(sel==null)} statement. <br>
 * And will move (record) pattern/guard switch labels, to the switch condition.
 * Letting the compiler optimize it.
 */
public class SwitchRetrofittingTaskListener implements TaskListener{
    // region compiler compatibility

    // Because we're compiling with Java25, the old method (without guards) doens't exists.
    private static Method LEGACY_MAKE_CASE;
    // Some internal states to avoid getting errors everytimes we trying to use a found feature.
    private static Boolean GUARDS, LABELS, BODIES, DEFAULT_CASES, CONSTANT_CASES;

    /** Get the guard expression from a case, or null if guards are unsupported. */
    private static JCExpression getGuard(JCCase caseTree){
        if(GUARDS != null) return GUARDS ? caseTree.getGuard() : null;
        try{
            JCExpression guard = caseTree.getGuard();
            GUARDS = true;
            return guard;
        }catch(NoSuchMethodError ignored){
            GUARDS = false;
            return null;
        }
    }

    /** Get the labels from a case, handling both old and new compiler APIs. */
    @SuppressWarnings("unchecked")
    private static List<JCTree> getLabels(JCCase caseTree){
        if(LABELS == null){
            try{
                caseTree.getLabels();
                LABELS = true;
            }catch(NoSuchMethodError ignored){
                LABELS = false;
            }
        }
        if(LABELS) return (List<JCTree>)(List<?>)caseTree.getLabels();

        List<JCExpression> labels = caseTree.getExpressions();
        if(labels == null) return List.nil();
        List<JCTree> result = List.nil();
        for(Object label : labels){
            if(!(label instanceof JCTree)) continue;
            result = result.append((JCTree)label);
        }
        return result;
    }

    /** Get the arrow-style body of a case, or null if unsupported. */
    private static JCTree getBody(JCCase caseTree){
        if (BODIES != null) return BODIES ? caseTree.getBody() : null;
        try{
            JCTree body = caseTree.getBody();
            BODIES = true;
            return body;
        }catch(NoSuchMethodError ignored){
            BODIES = false;
            return null;
        }
    }

    //TODO: CaseTree.CaseKind doesn't exists in Java 12-
    /**
     * Create a new case tree, handling different JDK signatures. <br>
     * JDK 21+: Case(CaseKind, List labels, JCExpression guard, List stats, JCTree body) <br>
     * JDK 17-20: Case(CaseKind, List labels, List stats, JCTree body)
     */
    @SuppressWarnings("unchecked")
    private JCCase makeCase(CaseTree.CaseKind kind, List<? extends JCTree> labels, JCExpression guard,
                            List<JCStatement> stats, JCTree body){
        if (GUARDS != false) {
            try{
                JCCase c = make.Case(kind, (List<JCCaseLabel>)labels, guard, stats, body);
                GUARDS = true;
                return c;
            }catch(NoSuchMethodError ignored){}
        }

        try{
            GUARDS = false;
            if(LEGACY_MAKE_CASE == null){
                LEGACY_MAKE_CASE = TreeMaker.class.getMethod("Case", CaseTree.CaseKind.class, List.class,
                                                             List.class, JCTree.class);
            }
            return (JCCase)LEGACY_MAKE_CASE.invoke(make, kind, labels, stats, body);
        }catch(Exception ignored){} // Hope this never happen...
        return null;
    }

    // Because the return type of these two methods may not exist, we need delay the call to an inner class.
    // Like that, the class initialization error can be catched easily.
    private static final class DefaultCaseLabelFactory{
        static JCTree make(TreeMaker m){ return m.DefaultCaseLabel(); }
    }
    private static final class ConstantCaseLabelFactory{
        static JCTree make(TreeMaker m, JCExpression lit){ return m.ConstantCaseLabel(lit); }
    }

    /** Create a default case label. Returns null if unsupported (JDK < 17). */
    private JCTree makeDefaultCaseLabel(){
        if (DEFAULT_CASES != null) return DEFAULT_CASES ? DefaultCaseLabelFactory.make(make) : null;
        try{
            JCTree c = DefaultCaseLabelFactory.make(make);
            DEFAULT_CASES = true;
            return c;
        }catch(NoSuchMethodError | NoClassDefFoundError ignored){
            DEFAULT_CASES = false;
            return null;
        }
    }

    /** Creates a case label for an {@code Integer}, handling JDK 17-20 and 21+ APIs. */
    private JCTree makeLabel(int i){
        if (CONSTANT_CASES != null)
            return CONSTANT_CASES ? ConstantCaseLabelFactory.make(make, make.Literal(i)) : make.Literal(i);
        try{
            JCTree tree = ConstantCaseLabelFactory.make(make, make.Literal(i)); // JDK 21+
            CONSTANT_CASES = true;
            return tree;
        }catch(NoSuchMethodError | NoClassDefFoundError ignored){
            CONSTANT_CASES = false;
            return make.Literal(i); // JDK 17-20
        }
    }

    private static boolean isSwitchExpression(Tree tree){
        return tree != null && getClassName(tree).equals("JCSwitchExpression");
    }

    private static boolean isDefault(JCTree label){
        return label != null && getClassName(label).equals("JCDefaultCaseLabel");
    }

    private static boolean isConstant(JCTree label){
        return label != null && getClassName(label).equals("JCConstantCaseLabel");
    }

    private static boolean isNull(JCTree label){
        if(label instanceof JCLiteral) return ((JCLiteral)label).typetag == TypeTag.BOT;
        if(!isConstant(label)) return false;
        JCExpression expr = ((JCConstantCaseLabel)label).expr;
        return expr instanceof JCLiteral && ((JCLiteral)expr).typetag == TypeTag.BOT;
    }

    private static boolean hasDefault(JCCase caseTree){
        List<JCTree> labels = getLabels(caseTree);
        if(labels.isEmpty()) return true;
        for(JCTree label : labels){
            if(!isDefault(label)) continue;
            return true;
        }
        return false;
    }

    private static JCExpression getLabelExpression(JCTree label){
        if(label instanceof JCExpression) return (JCExpression)label;
        if(isConstant(label)) return ((JCConstantCaseLabel)label).expr;
        return null;
    }

    private static boolean hasPatterns(List<JCCase> cases){
        if(cases == null) return false;
        for(JCCase caseTree : cases){
            if(caseTree == null) continue;
            if(getGuard(caseTree) != null) return true;
            for(JCTree label : getLabels(caseTree)){
                if(!isPattern(label)) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean hasNull(List<JCCase> cases){
        if(cases == null) return false;
        for(JCCase caseTree : cases){
            if(caseTree == null) continue;
            for(JCTree label : getLabels(caseTree)){
                if(!isNull(label)) continue;
                return true;
            }
        }
        return false;
    }

    // end region
    // region task listener

    final RecordPatternHelper helper;
    final TreeMaker make;
    final Symtab syms;
    final Names names;
    private int tempVarCounter = 0;

    public SwitchRetrofittingTaskListener(Context context){
        this.helper = new RecordPatternHelper(context);
        this.make = TreeMaker.instance(context);
        this.syms = Symtab.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        if(!(e.getCompilationUnit() instanceof JCCompilationUnit)) return;
        new SwitchTranslator().translate((JCCompilationUnit)e.getCompilationUnit());
    }

    @Override
    public void finished(TaskEvent e){

    }


    private class SwitchTranslator extends TreeTranslator{
        private final Map<JCSwitchExpression, JCExpression> captures = new HashMap<>();

        @Override
        public void visitSwitch(JCSwitch tree){
            super.visitSwitch(tree);
            if(!needsTransform(tree.cases)){
                tree.cases = injectDefault(tree.cases);
                return;
            }

            make.at(tree.pos);
            if(isComplex(tree.selector)){
                Name sv = names.fromString("$switch$" + (tempVarCounter++));
                result = make.Block(0, List.of(
                    makeFinalVar(sv, make.Type(syms.objectType), tree.selector),
                    transformSwitch(make.Ident(sv), tree.cases, false, null)
                ));
            }else{
                result = transformSwitch(tree.selector, tree.cases, false, null);
            }
        }


        @Override
        public void visitBlock(JCBlock tree){
            ListBuffer<JCStatement> buffer = null;
            for(JCStatement stmt : tree.stats){
                List<JCSwitchExpression> found = findPatternSwitches(stmt);
                for(JCSwitchExpression sw : found){
                    if(!isComplex(sw.selector)) continue;
                    if(buffer == null) {
                        ListBuffer<JCStatement> buf = new ListBuffer<>();
                        for(JCStatement s : tree.stats){
                            if(s == stmt) break;
                            buf.append(s);
                        }
                        buffer = buf;
                    }

                    Name sv = names.fromString("$switch$" + (tempVarCounter++));
                    buffer.append(make.VarDef(make.Modifiers(0), sv, make.Type(syms.objectType), null));
                    captures.put(sw, sw.selector);
                    sw.selector = make.Ident(sv);
                }
                if(buffer != null) buffer.append(stmt);
            }
            if(buffer != null) tree.stats = buffer.toList();
            super.visitBlock(tree);
        }

        @Override
        public <T extends JCTree> T translate(T tree){
            if(tree == null) return null;
            helper.collectRecord(tree);
            if(!isSwitchExpression(tree)) return super.translate(tree);

            JCSwitchExpression sw = (JCSwitchExpression)tree;
            make.at(sw.pos);
            JCExpression rawSel = captures.remove(sw); // consume capture if registered
            sw.selector = translate(sw.selector);
            sw.cases = translate(sw.cases);

            if(needsTransform(sw.cases)){
                JCSwitch ns = transformSwitch(sw.selector, sw.cases, true, rawSel);
                sw.selector = ns.selector;
                sw.cases = ns.cases;
            }else{
                sw.cases = injectDefault(sw.cases);
            }
            return tree;
        }

        /** TreeTranslator skips the arrow body field, translate it manually. */
        @Override
        public void visitCase(JCCase tree){
            super.visitCase(tree);
            JCTree body = getBody(tree);
            if(body == null) return;
            JCTree saved = result;
            setCaseBody(tree, translate(body));
            result = saved;
        }

        /** Recursively collects all JCSwitchExpression nodes inside a statement. */
        private List<JCSwitchExpression> findPatternSwitches(JCTree node){
            ListBuffer<JCSwitchExpression> out = new ListBuffer<>();
            new TreeScanner<Void, Void>(){
                @Override
                public Void scan(Tree t, Void v){
                    if(t == null) return null;
                    if(isSwitchExpression(t)){
                        JCSwitchExpression se = (JCSwitchExpression)t;
                        if(needsTransform(se.cases)) out.append(se);
                        // Don't recurse into the switch itself, nested ones handled separately.
                        return null;
                    }
                    return super.scan(t, v);
                }
            }.scan(node, null);
            return out.toList();
        }
    }

    /**
     * Replaces a pattern/null switch to a standard switch using a ternary-chain dispatcher in the condition.
     * <p>
     * The ternary chain maps each case condition to an index which becomes the new selector. <br>
     * Each case label is rewritten with this index, plus the record pattern, if any, is lowered to the case. <br>
     *
     * @param sel the selector expression
     * @param cases already-translated case list
     * @param expression whether to build a switch expression or statement
     * @param rawSel original selector to inject as a capture, or {@code null}
     */
    public JCSwitch transformSwitch(JCExpression sel, List<JCCase> cases, boolean expression,
                                    JCExpression rawSel){
        java.util.List<JCCase> nonDefs = new ArrayList<>();
        JCCase defCase = null;
        for(JCCase c : cases){
            if(hasDefault(c)) defCase = c;
            else nonDefs.add(c);
        }
        int n = nonDefs.size();

        // Ternary chain
        final Name selName = sel instanceof JCIdent ? ((JCIdent)sel).name : null;
        JCExpression ternary = make.Literal(n);
        for(int i = n - 1; i >= 0; i--){
            JCCase c = nonDefs.get(i);
            JCExpression cond = buildCondition(getLabels(c), sel, c);
            if(cond == null) continue;
            if(i == 0 && rawSel != null && selName != null){
                // Replace the first reference to the selector ident with (sv = rawSel).
                final boolean[] done = {false};
                cond = new TreeTranslator(){
                    @Override public void visitIdent(JCIdent id){
                        if(!done[0] && id.name == selName){
                            done[0] = true;
                            result  = make.Parens(make.Assign(make.Ident(selName), rawSel));
                        }else result = id;
                    }
                }.translate(cond);
            }
            ternary = make.Conditional(cond, make.Literal(i), ternary);
        }
        // In case of
        if(n == 0 && rawSel != null && selName != null) {
            ternary = make.Parens(make.Assign(make.Ident(selName), rawSel));
        }

        // Rebuild cases with int label
        ListBuffer<JCCase> newCases = new ListBuffer<>();
        for(int i = 0; i < n; i++){
            JCCase nc = makeCase(
                CaseTree.CaseKind.STATEMENT,
                List.of(makeLabel(i)),
                null,
                List.of(make.Block(0, buildCaseBody(nonDefs.get(i), sel, expression))),
                null
            );
            if(nc != null) newCases.append(nc);
        }

        // Default case
        JCTree dl = makeDefaultCaseLabel();
        if(dl != null){
            JCCase dc = makeCase(
                CaseTree.CaseKind.STATEMENT,
                List.of(dl),
                null,
                List.of(make.Block(0, defCase != null
                    ? buildCaseBody(defCase, sel, expression)
                    : List.of(makeMatchExceptionThrow())
                )),
                null
            );
            if(dc != null) newCases.append(dc);
        }

        return make.Switch(ternary, newCases.toList());
    }

    public List<JCStatement> buildCaseBody(JCCase c, JCExpression sel, boolean expression){
        ListBuffer<JCStatement> out = new ListBuffer<>();
        addBindings(c, sel, out);
        JCTree body = getBody(c);

        if(body instanceof JCBlock){
            for(JCStatement s : ((JCBlock)body).stats) out.append(s);
        }else if(body != null){
            JCExpression expr = extractExpression(body);
            if(expr != null){
                if(expression) out.append(make.Yield(expr));
                else{
                    out.append(make.Exec(expr));
                    out.append(make.Break(null));
                }
            }else if(body instanceof JCStatement){
                out.append((JCStatement)body);
            }
        }else if(c.stats != null){
            for(JCStatement s : c.stats) out.append(s);
        }

        return out.toList();
    }

    /**
     * Injects {@code default: throw new UnsupportedOperationException("MatchException");}
     * for exhaustive switches that have no explicit default.
     */
    public List<JCCase> injectDefault(List<JCCase> cases){
        if(cases == null) return cases;
        for(JCCase c : cases){
            if(c != null && hasDefault(c)) return cases;
        }

        CaseTree.CaseKind kind = CaseTree.CaseKind.STATEMENT;
        for(JCCase c : cases){
            if(c != null){
                kind = c.getCaseKind();
                break;
            }
        }

        JCTree defaultLabel = makeDefaultCaseLabel();
        if(defaultLabel == null) return cases;

        JCStatement throwStmt = makeMatchExceptionThrow();
        JCCase defaultCase = makeCase(
            kind,
            List.of(defaultLabel),
            null,
            List.of(throwStmt),
            kind == CaseTree.CaseKind.RULE ? throwStmt : null
        );
        return defaultCase != null ? cases.append(defaultCase) : cases;
    }


    public JCExpression buildCondition(List<JCTree> labels, JCExpression sel, JCCase caseTree){
        JCExpression condition = null;
        for(JCTree label : labels){
            JCExpression lc = buildLabelCondition(label, sel);
            if(lc == null) continue;
            condition = condition == null ? lc : make.Binary(JCTree.Tag.OR, condition, lc);
        }

        JCExpression guard = getGuard(caseTree);
        if(guard == null) return condition;

        Map<Name, JCExpression> bindingMap = collectBindings(labels, sel);
        if(!bindingMap.isEmpty()){
            guard = new TreeTranslator(){
                @Override
                public void visitIdent(JCIdent ident){
                    JCExpression replacement = bindingMap.get(ident.name);
                    result = replacement != null ? replacement : ident;
                }
            }.translate(guard);
        }

        return condition == null ? guard : make.Binary(JCTree.Tag.AND, condition, guard);
    }

    public JCExpression buildLabelCondition(JCTree label, JCExpression sel){
        if(isNull(label)){
            return make.Binary(JCTree.Tag.EQ, helper.copy(sel), make.Literal(TypeTag.BOT, null));
        }else if(isPattern(label)){
            JCVariableDecl pv = getPatternVar(label);
            if(pv != null) return make.TypeTest(helper.copy(sel), pv.vartype);
            JCExpression rt = getRecordType(label);
            if(rt != null) return make.TypeTest(helper.copy(sel), rt);
            JCExpression pt = getPatternType(label);
            if(pt != null) return make.TypeTest(helper.copy(sel), pt);
        }else if(!isDefault(label)){
            JCExpression expr = getLabelExpression(label);
            if(expr != null) return make.Apply(
                List.nil(),
                make.Select(expr, names.equals),
                List.of(helper.copy(sel))
            );
        }
        return null;
    }

    public Map<Name, JCExpression> collectBindings(List<JCTree> labels, JCExpression sel){
        Map<Name, JCExpression> map = new HashMap<>();
        for(JCTree label : labels){
            JCVariableDecl pv = getPatternVar(label);
            if(pv != null){
                map.put(pv.name, make.TypeCast(pv.vartype, helper.copy(sel)));
                continue;
            }

            JCExpression rt = getRecordType(label);
            List<? extends JCTree> nested = getRecordNested(label);
            if(rt == null || nested == null) continue;

            List<Name> componentNames = helper.getRecordComponentNames(label);
            int i = 0;
            for(JCTree np : nested){
                Name cn = i < componentNames.size() ? componentNames.get(i++) : null;
                JCVariableDecl nv = getPatternVar(np);
                if(nv == null) continue;

                map.put(nv.name, make.Apply(
                    List.nil(),
                    make.Select(make.TypeCast(rt, helper.copy(sel)), cn != null ? cn : nv.name),
                    List.nil()
                ));
            }
        }
        return map;
    }

    private void addBindings(JCCase caseTree, JCExpression sel, ListBuffer<JCStatement> out){
        for(JCTree label : getLabels(caseTree)){
            if(!isPattern(label)) continue;

            JCVariableDecl pv = getPatternVar(label);
            if(pv != null){
                out.append(make.VarDef(
                    make.Modifiers(Flags.FINAL),
                    pv.name,
                    pv.vartype,
                    make.TypeCast(pv.vartype, helper.copy(sel))
                ));
                continue;
            }

            JCExpression rt = getRecordType(label);
            List<? extends JCTree> nested = getRecordNested(label);
            if(rt == null || nested == null) continue;

            Name cv = helper.tempName();
            out.append(helper.makeVarDef(cv, rt, helper.makeCast(rt, helper.copy(sel))));
            ListBuffer<JCVariableDecl> bindings = new ListBuffer<>();
            helper.extractRecordBindings(
                nested,
                make.Ident(cv),
                helper.getRecordComponentNames(label),
                bindings
            );
            for(JCVariableDecl decl : bindings) out.append(decl);
        }
    }

    private boolean isComplex(JCExpression expr){
        if(expr instanceof JCIdent || expr instanceof JCLiteral) return false;
        if(expr instanceof JCFieldAccess) return isComplex(((JCFieldAccess)expr).selected);
        if(expr instanceof JCParens) return isComplex(((JCParens)expr).expr);
        return true; // method calls, new, binary ops, etc.
    }

    private static boolean needsTransform(List<JCCase> cases){
        return hasPatterns(cases) || hasNull(cases);
    }

    private JCExpression extractExpression(JCTree body){
        if(body instanceof JCExpressionStatement) return ((JCExpressionStatement)body).expr;
        if(body instanceof JCExpression) return (JCExpression)body;
        return null;
    }

    private JCStatement makeFinalVar(Name name, JCExpression type, JCExpression init){
        return make.VarDef(make.Modifiers(Flags.FINAL), name, type, init);
    }

    private JCStatement makeMatchExceptionThrow(){
        return make.Throw(make.NewClass(
            null,
            List.nil(),
            make.Ident(names.fromString("UnsupportedOperationException")),
            List.of(make.Literal("MatchException")),
           null
        ));
    }

    // end region
}