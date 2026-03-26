package com.github.bsideup.jabel;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.util.*;


/** Utilities for record pattern extraction and tree manipulation. */
class RecordPatternHelper{

    static String getClassName(Object obj){
        return obj == null ? "" : obj.getClass().getSimpleName();
    }

    /** Set the arrow-style body of a case tree. No-op if the field doesn't exist. */
    static void setCaseBody(JCCase caseTree, JCTree body){
        try{
            caseTree.body = body;
        }catch(NoSuchFieldError ignored){}
    }

    static boolean isBindingPattern(JCTree pattern){
        return pattern != null && getClassName(pattern).equals("JCBindingPattern");
    }

    static boolean isRecordPattern(JCTree pattern){
        return pattern != null && getClassName(pattern).equals("JCRecordPattern");
    }

    /** Check if this tree node is a pattern (binding, record, case label, or any). */
    static boolean isPattern(JCTree label){
        if(label == null) return false;
        String name = getClassName(label);
        return name.contains("Pattern") || name.contains("Binding");
    }

    /**
     * Because {@link JCPatternCaseLabel#pat} type is {@link JCPattern},
     * we need to delay access to an inner class. <br>
     * Like that, the class initialization error can be catched easily.
     */
    private static final class PatternCaseLabelAccess{
        static JCTree pat(JCTree label){ return ((JCPatternCaseLabel)label).pat; }
    }

    /** Get the binding variable from a pattern (binding pattern or pattern case label). */
    static JCVariableDecl getPatternVar(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCBindingPattern": return ((JCBindingPattern)label).var;
            case "JCPatternCaseLabel": return getPatternVar(PatternCaseLabelAccess.pat(label));
            default: return null;
        }
    }

    /**
     * Get the type from a pattern label (for instanceof check).
     * Works for binding patterns, pattern case labels, and unnamed patterns.
     */
    static JCExpression getPatternType(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCBindingPattern":
                JCVariableDecl var = ((JCBindingPattern)label).var;
                return var != null ? var.vartype : null;
            case "JCPatternCaseLabel": return getPatternType(PatternCaseLabelAccess.pat(label));
            case "JCAnyPattern": return getAnyPatternType(label);
            default: return null;
        }
    }

    /**
     * Get the type from a {@code JCAnyPattern}.
     * <p>
     * Reflection is needed because APIs varies across JDK versions:
     * {@link JCTree#type} and {@link JCTree#getType()} differ between Java 22 and 23+.
     */
    static JCExpression getAnyPatternType(JCTree label){
        //TODO: remake
        try{
            Object type = label.getClass().getField("type").get(label);
            if(type instanceof JCExpression) return (JCExpression)type;
        }catch(Exception ignored){}
        try{
            Object type = label.getClass().getMethod("getType").invoke(label);
            if(type instanceof JCExpression) return (JCExpression)type;
        }catch(Exception ignored){}
        return null;
    }

    /** Get the record type (deconstructor) from a record pattern or pattern case label. */
    static JCExpression getRecordType(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCRecordPattern": return ((JCRecordPattern)label).deconstructor;
            case "JCPatternCaseLabel": return getRecordType(PatternCaseLabelAccess.pat(label));
            default: return null;
        }
    }

    /** Get nested patterns from a record pattern or pattern case label. */
    static List<? extends JCTree> getRecordNested(JCTree label){
        if(label == null) return null;
        switch(getClassName(label)){
            case "JCRecordPattern": return ((JCRecordPattern)label).nested;
            case "JCPatternCaseLabel": return getRecordNested(PatternCaseLabelAccess.pat(label));
            default: return null;
        }
    }

    ///

    final TreeMaker make;
    final Names names;
    /** Cache of record declarations for component name lookup. */
    final Map<String, JCClassDecl> records = new HashMap<>();
    private static int tempVarCounter = 0;

    RecordPatternHelper(Context context){
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    /** Collect a record declaration for later component name lookup. Call during tree traversal. */
    void collectRecord(JCTree tree){
        if(!(tree instanceof JCClassDecl)) return;
        JCClassDecl classDecl = (JCClassDecl)tree;
        if(!"RECORD".equals(classDecl.getKind().toString())) return;
        records.put(classDecl.name.toString(), classDecl);
    }

    /** Get record component names from cached record declarations or fallback to binding names. */
    List<Name> getRecordComponentNames(JCTree pattern){
        JCExpression deconstructor = getRecordType(pattern);
        if(deconstructor == null) return List.nil();

        // Try to find record declaration
        JCClassDecl recordDecl = records.get(deconstructor.toString());
        if(recordDecl != null){
            List<Name> result = List.nil();
            for(JCTree def : recordDecl.defs){
                if(!(def instanceof JCVariableDecl)) continue;
                JCVariableDecl varDecl = (JCVariableDecl)def;
                if((varDecl.mods.flags & Flags.RECORD) == 0) continue;
                result = result.append(varDecl.name);
            }
            if(!result.isEmpty()) return result;
        }

        // Fallback: use binding pattern names
        List<? extends JCTree> nested = getRecordNested(pattern);
        if(nested == null) return List.nil();

        List<Name> result = List.nil();
        for(JCTree p : nested){
            JCVariableDecl var = getPatternVar(p);
            result = result.append(var != null ? var.name : null);
        }
        return result;
    }

    /**
     * Recursively extract bindings from nested record pattern components.
     * <p>
     * Generates variable declarations like:
     * {@code final Point $tmp = parent.b(); final int bx = $tmp.x(); final int by = $tmp.y();}
     */
    void extractRecordBindings(List<? extends JCTree> nested, JCExpression baseAccessor,
                               List<Name> componentNames, ListBuffer<JCVariableDecl> out){
        int index = 0;
        for(JCTree nestedPattern : nested){
            Name componentName = index < componentNames.size() ? componentNames.get(index++) : null;

            if(isBindingPattern(nestedPattern)){
                JCVariableDecl var = ((JCBindingPattern)nestedPattern).var;
                if(var == null) continue;
                Name accessorName = componentName != null ? componentName : var.name;
                out.append(makeVarDef(var.name, var.vartype, makeMethodCall(baseAccessor, accessorName)));
                continue;
            }

            if(!isRecordPattern(nestedPattern)) continue;
            JCExpression nestedRecordType = getRecordType(nestedPattern);
            List<? extends JCTree> deepNested = getRecordNested(nestedPattern);
            if(nestedRecordType == null || deepNested == null || componentName == null) continue;
            Name tempVar = tempName();
            JCExpression accessor = makeMethodCall(baseAccessor, componentName);
            out.append(makeVarDef(tempVar, nestedRecordType, accessor));
            extractRecordBindings(deepNested, make.Ident(tempVar), getRecordComponentNames(nestedPattern), out);
        }
    }


    Name tempName(){
        return names.fromString("$record$" + (tempVarCounter++));
    }

    JCVariableDecl makeVarDef(Name name, JCExpression type, JCExpression init){
        return make.VarDef(make.Modifiers(Flags.FINAL), name, copy(type), init);
    }

    JCTypeCast makeCast(JCExpression type, JCExpression expr){
        return make.TypeCast(copy(type), copy(expr));
    }

    JCMethodInvocation makeMethodCall(JCExpression receiver, Name method){
        return make.Apply(List.nil(), make.Select(copy(receiver), method), List.nil());
    }

    JCExpression copy(JCExpression expr){
        return new TreeCopier<Void>(make).copy(expr);
    }
}
