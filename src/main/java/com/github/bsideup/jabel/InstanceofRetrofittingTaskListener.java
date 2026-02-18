package com.github.bsideup.jabel;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.util.*;

/**
 * Transforms record patterns and binding patterns in instanceof (Java 16+/21+) into Java 8 compatible bytecode.
 * <p>
 * Handles:
 * <ul>
 * <li>Binding patterns (if obj instanceof String s)</li>
 * <li>Record patterns (if obj instanceof Point(int x, int y))</li>
 * <li>Nested record patterns (if obj instanceof Line(Point(int x1, int y1), Point end))</li>
 * <li>Unconditional patterns (if str instanceof CharSequence cs)</li>
 * </ul>
 */
public class InstanceofRetrofittingTaskListener implements TaskListener{
    // region compiler compatibility

    private static String getClassName(Object obj){
        return obj == null ? "" : obj.getClass().getSimpleName();
    }

    /** Check if pattern is a JCBindingPattern. */
    private static boolean isBindingPattern(JCTree pattern){
        return pattern != null && getClassName(pattern).equals("JCBindingPattern");
    }

    /** Check if pattern is a JCRecordPattern. */
    private static boolean isRecordPattern(JCTree pattern){
        return pattern != null && getClassName(pattern).equals("JCRecordPattern");
    }

    /** Get record pattern type (deconstructor). */
    private static JCExpression getRecordPatternType(JCTree pattern){
        if(!isRecordPattern(pattern)) return null;
        return ((JCRecordPattern)pattern).deconstructor;
    }

    /** Get nested patterns from record pattern. */
    private static List<JCPattern> getRecordPatternNested(JCTree pattern){
        if(!isRecordPattern(pattern)) return null;
        return ((JCRecordPattern)pattern).nested;
    }

    // end region
    // region task listener

    final Context context;
    final TreeMaker make;
    final Names names;
    private int tempVarCounter = 0;

    public InstanceofRetrofittingTaskListener(Context context){
        this.context = context;
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        CompilationUnitTree cu = e.getCompilationUnit();
        if(!(cu instanceof JCCompilationUnit)) return;
        new InstanceofTranslator().translate((JCCompilationUnit)cu);
    }

    @Override
    public void finished(TaskEvent e){

    }


    class InstanceofTranslator extends TreeTranslator{
        /** Cache of record declarations for component name lookup. */
        private final Map<String, JCClassDecl> records = new HashMap<>();

        @Override
        public <T extends JCTree> T translate(T tree){
            if(tree == null) return null;

            // Collect record declarations
            if(tree instanceof JCClassDecl){
                JCClassDecl classDecl = (JCClassDecl)tree;
                if("RECORD".equals(classDecl.getKind().toString())){
                    records.put(classDecl.name.toString(), classDecl);
                }
            }

            // Handle instanceof with patterns
            if(tree instanceof JCIf) handleIf((JCIf)tree);
            return super.translate(tree);
        }

        private void handleIf(JCIf ifStmt){
            JCExpression cond = unwrapParenthesis(ifStmt.cond);
            if(!(cond instanceof JCInstanceOf)) return;

            JCInstanceOf instanceOf = (JCInstanceOf)cond;
            JCTree pattern = instanceOf.pattern;
            if(pattern == null) return;

            if(isRecordPattern(pattern)){
                transformRecordPattern(ifStmt, instanceOf, pattern);
            }else if(isBindingPattern(pattern)){
                transformBindingPattern(ifStmt, instanceOf, (JCBindingPattern)pattern);
            }
        }

        private JCExpression unwrapParenthesis(JCExpression expr){
            while(expr instanceof JCParens) expr = ((JCParens)expr).expr;
            return expr;
        }

        /**
         * Transform: {@code if (obj instanceof Point(int x, int y)) { body } } <br>
         * Into: {@code if (obj instanceof Point) { Point $rec = (Point)obj; int x = $rec.x(); ... body } }
         */
        private void transformRecordPattern(JCIf ifStmt, JCInstanceOf instanceOf, JCTree pattern){
            JCExpression recordType = getRecordPatternType(pattern);
            if(recordType == null) return;

            make.at(ifStmt.pos);
            Name tempVar = names.fromString("$rec" + (tempVarCounter++));

            ListBuffer<JCVariableDecl> declarations = new ListBuffer<>();
            declarations.append( makeVarDef(tempVar, recordType, makeCast(recordType, instanceOf.expr)));
            extractBindings(pattern, make.Ident(tempVar), declarations);

            instanceOf.pattern = recordType;
            ifStmt.thenpart = buildBlock(declarations.toList(), ifStmt.thenpart);
        }

        /**
         * Transform: {@code if (obj instanceof String s) { body } } <br>
         * Into: {@code if (obj instanceof String) { String s = (String)obj; body } }
         */
        private void transformBindingPattern(JCIf ifStmt, JCInstanceOf instanceOf, JCBindingPattern pattern){
            JCVariableDecl var = pattern.var;
            if(var == null || var.vartype == null) return;

            make.at(ifStmt.pos);
            instanceOf.pattern = copy(var.vartype);

            ListBuffer<JCVariableDecl> declarations = new ListBuffer<>();
            declarations.append(makeVarDef(var.name, var.vartype, makeCast(var.vartype, instanceOf.expr)));
            ifStmt.thenpart = buildBlock(declarations.toList(), ifStmt.thenpart);
        }

        /** Recursively extract bindings from record pattern. */
        private void extractBindings(JCTree pattern, JCExpression baseAccessor, ListBuffer<JCVariableDecl> out){
            List<JCPattern> nested = getRecordPatternNested(pattern);
            if(nested == null) return;

            List<Name> componentNames = getRecordComponentNames(pattern);
            int index = 0;

            for(JCPattern nestedPattern : nested){
                Name componentName = index < componentNames.size() ? componentNames.get(index++) : null;

                if(isBindingPattern(nestedPattern)){
                    extractBinding((JCBindingPattern)nestedPattern, baseAccessor, componentName, out);
                }else if(isRecordPattern(nestedPattern)){
                    extractNestedRecord(nestedPattern, baseAccessor, componentName, out);
                }
                // JCAnyPattern (_) - no binding needed
            }
        }

        private void extractBinding(JCBindingPattern bp, JCExpression baseAccessor, Name componentName, ListBuffer<JCVariableDecl> out){
            if(bp.var == null) return;
            Name accessorName = componentName != null ? componentName : bp.var.name;
            JCExpression accessor = makeMethodCall(baseAccessor, accessorName);
            out.append(makeVarDef(bp.var.name, bp.var.vartype, accessor));
        }

        private void extractNestedRecord(JCTree nestedPattern, JCExpression baseAccessor, Name componentName, ListBuffer<JCVariableDecl> out){
            JCExpression nestedRecordType = getRecordPatternType(nestedPattern);
            if(nestedRecordType == null || componentName == null) return;

            Name tempVar = names.fromString("$rec" + (tempVarCounter++));
            JCExpression accessor = makeMethodCall(baseAccessor, componentName);
            out.append(makeVarDef(tempVar, nestedRecordType, accessor));
            extractBindings(nestedPattern, make.Ident(tempVar), out);
        }

        /** Get record component names from cached record declarations or fallback to binding names. */
        private List<Name> getRecordComponentNames(JCTree pattern){
            JCExpression deconstructor = getRecordPatternType(pattern);
            if(deconstructor == null) return List.nil();

            // Try to find record declaration
            List<Name> result = List.nil();
            JCClassDecl recordDecl = records.get(deconstructor.toString());
            if(recordDecl != null){
                for(JCTree def : recordDecl.defs){
                    if(!(def instanceof JCVariableDecl)) continue;
                    JCVariableDecl varDecl = (JCVariableDecl)def;
                    if((varDecl.mods.flags & Flags.RECORD) != 0){
                        result = result.append(varDecl.name);
                    }
                }
                if(!result.isEmpty()) return result;
            }

            // Fallback: use binding pattern names
            List<JCPattern> nested = getRecordPatternNested(pattern);
            if(nested == null) return result;

            for(JCPattern p : nested){
                if(!isBindingPattern(p)){
                    result = result.append(null);
                    continue;
                }
                JCBindingPattern bp = (JCBindingPattern)p;
                result = result.append(bp.var != null ? bp.var.name : null);
            }
            return result;
        }

        private JCVariableDecl makeVarDef(Name name, JCExpression type, JCExpression init){
            return make.VarDef(make.Modifiers(Flags.FINAL), name, copy(type), init);
        }

        private JCTypeCast makeCast(JCExpression type, JCExpression expr){
            return make.TypeCast(copy(type), copy(expr));
        }

        private JCMethodInvocation makeMethodCall(JCExpression receiver, Name method){
            return make.Apply(List.nil(), make.Select(copy(receiver), method), List.nil());
        }

        private JCExpression copy(JCExpression expr){
            return new TreeCopier<Void>(make).copy(expr);
        }

        private JCBlock buildBlock(List<JCVariableDecl> declarations, JCStatement body){
            ListBuffer<JCStatement> stmts = new ListBuffer<>();
            for(JCVariableDecl decl : declarations) stmts.append(decl);
            if(body instanceof JCBlock){
                for(JCStatement stmt : ((JCBlock)body).stats) stmts.append(stmt);
            }else if(body != null){
                stmts.append(body);
            }
            return make.Block(0, stmts.toList());
        }
    }

    // end region
}
