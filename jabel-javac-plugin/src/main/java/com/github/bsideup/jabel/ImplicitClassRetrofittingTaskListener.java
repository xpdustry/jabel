package com.github.bsideup.jabel;

import javax.tools.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;


/**
 * To adapt implicit classes, the listener will make everything private static (except main methods)
 * and will add {@code String[] args} to existing main. <br>
 * This will not throw the right error, but will protect against instances and references.
 * (not perfect but better than nothing)
 * <p>
 * And to adapt flexible entry point ({@code void main();, void main(String[]);, static void main();}),
 * the listener will attempt to create a bridge.
 */
class ImplicitClassRetrofittingTaskListener implements TaskListener{
    final TreeMaker make;
    final Names names;
    final Symtab syms;
    final Log log;
    final JCDiagnostic.Factory diagFactory;
    final Name mainName;

    ImplicitClassRetrofittingTaskListener(Context context){
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.syms = Symtab.instance(context);
        this.log = Log.instance(context);
         this.diagFactory = JCDiagnostic.Factory.instance(context);
        this.mainName = names.fromString("main"); //syms.main;
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        CompilationUnitTree cu = e.getCompilationUnit();
        if(!(cu instanceof JCCompilationUnit)) return;
        JavaFileObject old = log.useSource(e.getSourceFile());

        for(JCTree def : ((JCCompilationUnit)e.getCompilationUnit()).defs){
            if(!(def instanceof JCClassDecl)) continue;
            transformClass((JCClassDecl)def);
        }

        log.useSource(old);
    }

    @Override
    public void finished(TaskEvent e){

    }

    public void transformClass(JCClassDecl classDecl){
        make.at(classDecl.pos);

        boolean implicit = (classDecl.mods.flags & Flags.IMPLICIT_CLASS) != 0;
        boolean canInstanciate = implicit, hasEntryPoint = false;
        boolean needsInstanceBridge = false, needsStaticBridge = false;
        boolean mainWithArgs = false;

        if(implicit) classDecl.mods.flags = (classDecl.mods.flags & ~Flags.IMPLICIT_CLASS) | Flags.FINAL;

        for(JCTree def : classDecl.defs){
            if(implicit){
                if(def instanceof JCVariableDecl) setAccess(((JCVariableDecl)def).mods, Flags.PRIVATE);
                else if(def instanceof JCClassDecl) setAccess(((JCClassDecl)def).mods, Flags.PRIVATE);
            }

            if(!(def instanceof JCMethodDecl)) continue;
            JCMethodDecl method = (JCMethodDecl)def;

            if(method.name == names.init && method.params.isEmpty()){
                canInstanciate = true;
                continue;
            }

            boolean main = isMain(method);
            if (implicit) setAccess(method.mods, main ? Flags.PUBLIC : Flags.PRIVATE);

            if(!main){
                if(implicit) setAccess(method.mods, Flags.PRIVATE);
                continue;
            }else if(hasEntryPoint){
                continue;
            }else if((method.mods.flags & Flags.STATIC) != 0){
                if(method.params.isEmpty()) needsStaticBridge = true;
                else {
                    hasEntryPoint = mainWithArgs = true;
                }
            }else{
                if(method.params.isEmpty()) needsInstanceBridge = true;
                else{
                    mainWithArgs = true;
                    if(implicit) continue;
                    //TODO: make it only printed when the class is instanciable?
                    log.report(diagFactory.warning(
                        null, log.currentSource(), method, "proc.messager",
                        "[jabel] possible entry point cannot be adapted due to a signature duplication. " +
                        classDecl.name + " cannot therefore be used as an entry point in a JVM bellow Java25."));
                }
            }
        }

        if(!hasEntryPoint && !mainWithArgs){
            if(needsStaticBridge){
                classDecl.defs = classDecl.defs.append(makeMainBridge(classDecl, false));
            }else if(needsInstanceBridge && canInstanciate){
                classDecl.defs = classDecl.defs.append(makeMainBridge(classDecl, true));
            }
        }

        if(implicit) classDecl.defs = classDecl.defs.prepend(makeConstructor());
    }

    void setAccess(JCModifiers mods, long visibility){
        mods.flags = (mods.flags & ~Flags.PUBLIC & ~Flags.PROTECTED & ~Flags.PRIVATE) | visibility | Flags.STATIC;
    }

    /** Check if this is a valid main method signature. */
    public boolean isMain(JCMethodDecl method){
        if(mainName != method.name) return false;
        if(!(method.restype instanceof JCPrimitiveTypeTree)) return false;
        if(((JCPrimitiveTypeTree)method.restype).typetag != TypeTag.VOID) return false;
        if(method.params.isEmpty()) return true;
        if(method.params.size() != 1) return false;
        String param = method.params.get(0).vartype.toString();
        return param.equals("String[]") || param.equals("java.lang.String[]");
    }

    /** Create a bridge {@code public static void main(String[] args)}. */
    public JCMethodDecl makeMainBridge(JCClassDecl classDecl, boolean toLocalMain){
        return make.MethodDef(
            make.Modifiers(Flags.PUBLIC | Flags.STATIC),
            mainName,
            make.TypeIdent(TypeTag.VOID),
            List.nil(),
            List.of(make.VarDef(
                make.Modifiers(Flags.PARAMETER), names.fromString("args"),
                make.TypeArray(make.Type(syms.stringType)), null
            )),
            List.nil(),
            make.Block(0, List.of(make.Exec(make.Apply(
                List.nil(),
                toLocalMain ? make.Select(
                    make.NewClass(null, List.nil(), make.Ident(classDecl.name), List.nil(), null),
                    mainName
                ) : make.Select(make.Ident(classDecl.name), mainName),
                List.nil()
            )))),
            null
        );
    }

    /** Create a private no-args constructor. */
    public JCMethodDecl makeConstructor(){
        return make.MethodDef(
            make.Modifiers(Flags.PRIVATE), names.init, null,
            List.nil(), List.nil(), List.nil(),
            make.Block(0, List.nil()), null
        );
    }
}
