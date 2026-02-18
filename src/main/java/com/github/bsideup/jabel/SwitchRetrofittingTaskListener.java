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
 * Transforms modern switch constructs (Java 17+) into Java 8 compatible bytecode. <br>
 * Note that since compiler api may change between versions, the class automatically adapts his process.
 * <p>
 * Handles:
 * <ul>
 * <li>Pattern matching in switch (case String s ->)</li>
 * <li>When guards (case String s when s.length() > 5 ->)</li>
 * <li>Case null (case null ->)</li>
 * <li>Record patterns (case Point(int x, int y) ->)</li>
 * <li>Switch expressions (var x = switch(...) { ... })</li>
 * </ul>
 */
public class SwitchRetrofittingTaskListener implements TaskListener{
    // region compiler compatibility

    private static Boolean GUARD_PRESENT, LABELS_RESENT;

    private static String getClassName(Object o){
        return o.getClass().getSimpleName();
    }

    /** Get the guard expression from a case. (Java 21+) */
    private static JCExpression getGuard(JCCase caseNode){
        // Optimization in case the method is not present
        if(GUARD_PRESENT != null){
            return GUARD_PRESENT ? caseNode.getGuard() : null;
        }
        try{
            JCExpression exp = caseNode.getGuard();
            GUARD_PRESENT = true;
            return exp;
        }catch(NoSuchMethodError ignored){
            GUARD_PRESENT = false;
            return null;
        }
    }

    /** Get case labels. */
    @SuppressWarnings("unchecked")
    private static List<JCTree> getCaseLabels(JCCase caseNode){
        // Optimization in case the method is not present
        if(LABELS_RESENT == null){
            // Java 17+
            try{
                List<JCCaseLabel> labels = caseNode.getLabels();
                LABELS_RESENT = true;
                return (List<JCTree>)(List<?>)labels; // I know what i'm doing
            }catch(NoSuchMethodError ignored){
                LABELS_RESENT = false;
            }
        }
        // I know what i'm doing
        if(LABELS_RESENT) return (List<JCTree>)(List<?>)caseNode.getLabels();
        List<JCExpression> labels = caseNode.getExpressions();
        if(labels == null) return List.nil();
        List<JCTree> result = List.nil();
        for(Object label : labels){
            if(label instanceof JCTree) result = result.append((JCTree)label);
        }
        return result;
    }

    /** Get the body of an arrow case. (Java 14+) */
    private static JCTree getArrowCaseBody(JCCase caseNode){
        try{
            return caseNode.getBody();
        }catch(NoSuchMethodError e){
            return null;
        }
    }

    /** Check if tree is a JCSwitchExpression */
    private static boolean isSwitchExpression(JCTree tree){
        return tree != null && getClassName(tree).equals("JCSwitchExpression");
    }

    /** Check if label is a pattern (JCBindingPattern, JCRecordPattern, etc.) */
    private static boolean isPatternLabel(JCTree label){
        if(label == null) return false;
        String name = getClassName(label);
        return name.contains("Pattern") || name.contains("Binding");
    }

    /** Check if label is the default case. */
    private static boolean isDefaultLabel(JCTree label){
        return label != null && getClassName(label).equals("JCDefaultCaseLabel");
    }

    /** Check if label is constant. */
    private static boolean isConstantLabel(JCTree label){
        return label != null && getClassName(label).equals("JCConstantCaseLabel");
    }

    /** Check if label is null literal */
    private static boolean isNullLabel(JCTree label){
        // Direct null literal
        if(label instanceof JCLiteral){
            return ((JCLiteral)label).typetag == TypeTag.BOT;
        }
        // JCConstantCaseLabel wrapping null
        if(isConstantLabel(label)){
            JCExpression expr = ((JCConstantCaseLabel)label).expr;
            return expr instanceof JCLiteral && ((JCLiteral)expr).typetag == TypeTag.BOT;
        }
        return false;
    }

    private static boolean containsDefaultLabel(JCCase caseNode) {
        List<JCTree> labels = getCaseLabels(caseNode);
        return labels.isEmpty() || labels.stream().anyMatch(SwitchRetrofittingTaskListener::isDefaultLabel);
    }

    /** Extract the binding variable from a pattern label */
    private static JCVariableDecl getPatternVar(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCBindingPattern":
                return ((JCBindingPattern)label).var;
            case "JCPatternCaseLabel":
                return getPatternVar(((JCPatternCaseLabel)label).pat);
            default:
                return null;
        }
    }

    /** Extract the type from a record pattern */
    private static JCExpression getRecordPatternType(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCRecordPattern":
                return ((JCRecordPattern)label).deconstructor;
            case "JCPatternCaseLabel":
                return getRecordPatternType(((JCPatternCaseLabel)label).pat);
            default:
                return null;
        }
    }

    /** Get nested patterns from a record pattern */
    private static List<JCPattern> getRecordPatternNested(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCRecordPattern":
                return ((JCRecordPattern)label).nested;
            case "JCPatternCaseLabel":
                return getRecordPatternNested(((JCPatternCaseLabel)label).pat);
            default:
                return null;
        }
    }

    /** Extract constant expression from a case label */
    private static JCExpression getLabelExpression(JCTree label){
        if(label instanceof JCExpression) return (JCExpression)label;
        if(isConstantLabel(label)) return ((JCConstantCaseLabel)label).expr;
        return null;
    }

    /** Get selector from a switch expression */
    private static JCExpression getSwitchExprSelector(JCTree switchExpr){
        if(!isSwitchExpression(switchExpr)) return null;
        return ((JCSwitchExpression)switchExpr).selector;
    }

    /** Set selector on a switch expression */
    private static void setSwitchExprSelector(JCTree switchExpr, JCExpression selector){
        if(isSwitchExpression(switchExpr)) ((JCSwitchExpression)switchExpr).selector = selector;
    }

    /** Get cases from a switch expression */
    private static List<JCCase> getSwitchExprCases(JCTree switchExpr){
        if(!isSwitchExpression(switchExpr)) return null;
        return ((JCSwitchExpression)switchExpr).cases;
    }

    /** Set cases on a switch expression */
    private static void setSwitchExprCases(JCTree switchExpr, List<JCCase> cases){
        if(isSwitchExpression(switchExpr)) ((JCSwitchExpression)switchExpr).cases = cases;
    }

    /** Get value from a JCYield statement */
    private static JCExpression getYieldValue(JCStatement stmt){
        if(!getClassName(stmt).equals("JCYield")) return null;
        return ((JCYield)stmt).value;
    }

    // end region
    // region task listener

    final Context context;
    final TreeMaker make;
    final Names names;
    private int tempVarCounter = 0;

    public SwitchRetrofittingTaskListener(Context context){
        this.context = context;
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        CompilationUnitTree cu = e.getCompilationUnit();
        if(!(cu instanceof JCCompilationUnit)) return;
        new SwitchTranslator().translate((JCCompilationUnit)cu);
    }

    @Override
    public void finished(TaskEvent e){

    }

    // endregion
    // region Tree Translator

    class SwitchTranslator extends TreeTranslator{
        @SuppressWarnings("unchecked")
        @Override
        public <T extends JCTree> T translate(T tree){
            if(!isSwitchExpression(tree)) return super.translate(tree);
            return (T)handleSwitchExpression(tree);
        }

        @Override
        public void visitSwitch(JCSwitch tree){
            super.visitSwitch(tree);
            if(needsTransformation(tree.cases)){
                result = transformPatternSwitch(tree);
            }
        }

        private JCTree handleSwitchExpression(JCTree switchExpr){
            make.at(switchExpr.pos);
            JCExpression selector = translate(getSwitchExprSelector(switchExpr));
            List<JCCase> cases = translate(getSwitchExprCases(switchExpr));
            if(needsTransformation(cases)) return buildTernaryChain(cases, selector);
            setSwitchExprSelector(switchExpr, selector);
            setSwitchExprCases(switchExpr, cases);
            return switchExpr;
        }

        private boolean needsTransformation(List<JCCase> cases){
            for(JCCase caseNode : cases){
                if(getGuard(caseNode) != null) return true;
                for(JCTree label : getCaseLabels(caseNode)){
                    if(isPatternLabel(label) || isNullLabel(label)) return true;
                }
            }
            return false;
        }

        // ========== Ternary chain conversion ==========

        private JCExpression buildTernaryChain(List<JCCase> cases, JCExpression selector){
            java.util.List<JCCase> nonDefaultCases = new ArrayList<>();
            JCExpression defaultExpr = null;

            for(JCCase caseNode : cases){
                if(containsDefaultLabel(caseNode)) defaultExpr = extractCaseExpression(caseNode);
                else nonDefaultCases.add(caseNode);
            }

            JCExpression result = defaultExpr != null ? defaultExpr : make.Literal(TypeTag.BOT, null);
            for(int i = nonDefaultCases.size() - 1; i >= 0; i--){
                JCCase caseNode = nonDefaultCases.get(i);
                List<JCTree> labels = getCaseLabels(caseNode);

                JCExpression condition = buildCondition(labels, selector, caseNode);
                if(condition == null) continue;

                JCExpression thenExpr = extractCaseExpression(caseNode);
                if(thenExpr == null) thenExpr = make.Literal(TypeTag.BOT, null);

                thenExpr = substituteBindings(thenExpr, labels, selector);
                result = make.Conditional(condition, thenExpr, result);
            }
            return result;
        }

        private JCExpression extractCaseExpression(JCCase caseNode){
            // Try body field first (arrow cases)
            JCTree body = getArrowCaseBody(caseNode);
            if(body instanceof JCExpression){
                return (JCExpression)body;
            }else if(body instanceof JCExpressionStatement){
                return ((JCExpressionStatement)body).expr;
            // Try stats (colon cases with yield)
            }else if(caseNode.stats != null){
                for(JCStatement stmt : caseNode.stats){
                    JCExpression yieldValue = getYieldValue(stmt);
                    if(yieldValue != null) return yieldValue;
                }
            }
            return null;
        }

        private JCExpression buildCondition(List<JCTree> labels, JCExpression selector, JCCase caseNode){
            JCExpression condition = null;
            for(JCTree label : labels){
                JCExpression labelCond = buildLabelCondition(label, selector);
                if(labelCond == null) continue;
                condition = condition == null ? labelCond : make.Binary(JCTree.Tag.OR, condition, labelCond);
            }

            // Add guard
            JCExpression guard = getGuard(caseNode);
            if(guard != null){
                guard = substituteBindings(copy(guard), labels, selector);
                condition = condition == null ? guard : make.Binary(JCTree.Tag.AND, condition, guard);
            }

            return condition;
        }

        private JCExpression buildLabelCondition(JCTree label, JCExpression selector){
            if(isNullLabel(label)){
                return make.Binary(JCTree.Tag.EQ, copy(selector), make.Literal(TypeTag.BOT, null));
            }else if(isPatternLabel(label)){
                return buildPatternCondition(label, selector);
            }else if(!isDefaultLabel(label)){
                JCExpression expr = getLabelExpression(label);
                if(expr == null) return null;
                return make.Apply(List.nil(), make.Select(copy(selector), names.equals), List.of(expr));
            }
            return null;
        }

        private JCExpression buildPatternCondition(JCTree label, JCExpression selector){
            JCVariableDecl var = getPatternVar(label);
            if(var != null) return make.TypeTest(copy(selector), var.vartype);
            JCExpression recordType = getRecordPatternType(label);
            if(recordType == null) return null;
            return make.TypeTest(copy(selector), recordType);
        }

        private JCExpression substituteBindings(JCExpression expr, List<JCTree> labels, JCExpression selector){
            Map<Name, JCExpression> bindings = collectBindings(labels, selector);
            if(bindings.isEmpty()) return expr;
            return new TreeTranslator(){
                @Override
                public void visitIdent(JCIdent tree){
                    JCExpression repl = bindings.get(tree.name);
                    result = repl != null ? copy(repl) : tree;
                }
            }.translate(expr);
        }

        private Map<Name, JCExpression> collectBindings(List<JCTree> labels, JCExpression selector){
            Map<Name, JCExpression> bindings = new HashMap<>();
            for(JCTree label : labels){
                collectBindingsFromLabel(label, selector, bindings);
            }
            return bindings;
        }

        private void collectBindingsFromLabel(JCTree label, JCExpression selector, Map<Name, JCExpression> bindings){
            JCVariableDecl var = getPatternVar(label);
            if(var != null){
                bindings.put(var.name, make.TypeCast(var.vartype, copy(selector)));
                return;
            }

            // Record pattern
            JCExpression recordType = getRecordPatternType(label);
            List<JCPattern> nested = getRecordPatternNested(label);
            if(recordType == null || nested == null) return;
            for(JCPattern nestedPat : nested){
                JCVariableDecl nestedVar = getPatternVar(nestedPat);
                if(nestedVar == null) continue;

                JCExpression casted = make.TypeCast(recordType, copy(selector));
                JCExpression accessor = make.Apply(List.nil(), make.Select(casted, nestedVar.name), List.nil());
                bindings.put(nestedVar.name, accessor);
            }
        }

        private JCExpression copy(JCExpression expr){
            return new TreeCopier<Void>(make).copy(expr);
        }

        // ========== If-Else chain conversion ==========

        private JCBlock transformPatternSwitch(JCSwitch switchNode){
            make.at(switchNode.pos);
            Name varName = names.fromString("$obj" + (tempVarCounter++));
            JCExpression varType = switchNode.selector.type != null ?
                make.Type(switchNode.selector.type) : make.Ident(names.fromString("Object"));
            ListBuffer<JCStatement> stmts = new ListBuffer<>();

            stmts.append(make.VarDef(make.Modifiers(Flags.FINAL), varName, varType, switchNode.selector));
            JCStatement ifChain = buildIfElseChain(switchNode.cases, varName);

            if(ifChain != null) stmts.append(ifChain);
            return make.Block(0, stmts.toList());
        }

        private JCStatement buildIfElseChain(List<JCCase> cases, Name varName){
            java.util.List<JCCase> caseList = java.util.List.copyOf(cases);

            // Find default
            JCStatement defaultStmt = null;
            for(JCCase caseNode : cases){
                if(!containsDefaultLabel(caseNode)) continue;
                defaultStmt = buildCaseBody(caseNode, varName);
                break;
            }

            JCStatement elseStmt = defaultStmt;
            // TODO: remake
            for(int i = caseList.size() - 1; i >= 0; i--){
                JCCase caseNode = caseList.get(i);
                if(containsDefaultLabel(caseNode)) continue;

                List<JCTree> labels = getCaseLabels(caseNode);
                JCExpression condition = buildConditionForVar(labels, varName, caseNode);
                if(condition == null) continue;

                JCStatement thenStmt = buildCaseBody(caseNode, varName);
                elseStmt = make.If(condition, thenStmt, elseStmt);
            }

            return elseStmt;
        }

        private JCExpression buildConditionForVar(List<JCTree> labels, Name varName, JCCase caseNode){
            JCExpression condition = null;
            for(JCTree label : labels){
                JCExpression labelCond = buildLabelConditionForVar(label, varName);
                if(labelCond == null) continue;
                condition = condition == null ? labelCond : make.Binary(JCTree.Tag.OR, condition, labelCond);
            }

            // Add guard
            JCExpression guard = getGuard(caseNode);
            if(guard != null){
                Map<Name, JCExpression> bindings = collectBindingsForVar(labels, varName);
                if(!bindings.isEmpty()){
                    guard = new TreeTranslator(){
                        @Override
                        public void visitIdent(JCIdent tree){
                            JCExpression repl = bindings.get(tree.name);
                            result = repl != null ? repl : tree;
                        }
                    }.translate(guard);
                }
                condition = condition == null ? guard : make.Binary(JCTree.Tag.AND, condition, guard);
            }
            return condition;
        }

        private JCExpression buildLabelConditionForVar(JCTree label, Name varName){
            if(isNullLabel(label)){
                return make.Binary(JCTree.Tag.EQ, make.Ident(varName), make.Literal(TypeTag.BOT, null));
            } else if(isPatternLabel(label)){
                JCVariableDecl var = getPatternVar(label);
                if(var != null) return make.TypeTest(make.Ident(varName), var.vartype);
                JCExpression recordType = getRecordPatternType(label);
                if(recordType != null) return make.TypeTest(make.Ident(varName), recordType);
            } else if(!isDefaultLabel(label)){
                JCExpression expr = getLabelExpression(label);
                if(expr == null) return null;
                return make.Apply(List.nil(), make.Select(make.Ident(varName), names.equals), List.of(expr));
            }
            return null;
        }

        private Map<Name, JCExpression> collectBindingsForVar(List<JCTree> labels, Name varName){
            Map<Name, JCExpression> bindings = new HashMap<>();
            for(JCTree label : labels){
                JCVariableDecl var = getPatternVar(label);
                if(var == null) continue;
                bindings.put(var.name, make.TypeCast(var.vartype, make.Ident(varName)));
            }
            return bindings;
        }

        private JCStatement buildCaseBody(JCCase caseNode, Name varName){
            ListBuffer<JCStatement> stmts = new ListBuffer<>();

            // Add binding declarations
            for(JCTree label : getCaseLabels(caseNode)){
                if(!isPatternLabel(label)) continue;
                JCVariableDecl var = getPatternVar(label);
                if(var == null) continue;
                stmts.append(make.VarDef(make.Modifiers(Flags.FINAL), var.name, var.vartype,
                             make.TypeCast(var.vartype, make.Ident(varName))));
            }

            // Add body
            JCTree body = getArrowCaseBody(caseNode);
            if(body instanceof JCStatement){
                stmts.append((JCStatement)body);
            }else if(caseNode.stats != null){
                for(JCStatement stmt : caseNode.stats) stmts.append(stmt);
            }

            return stmts.isEmpty() ? make.Block(0, List.nil()) : make.Block(0, stmts.toList());
        }
    }

    // endregion
}
