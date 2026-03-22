package com.github.bsideup.jabel;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;

import static com.github.bsideup.jabel.RecordPatternHelper.*;


/**
 * Transforms record patterns and binding patterns in instanceof (Java 16+/21+) into Java 8 compatible bytecode.
 * <p>
 * Handles:
 * <ul>
 * <li>Binding patterns (if obj instanceof String s).</li>
 * <li>Record patterns (if obj instanceof Point(int x, int y)).</li>
 * <li>Nested record patterns (if obj instanceof Line(Point(int x1, int y1), Point end)).</li>
 * <li>Unconditional patterns (if str instanceof CharSequence cs).</li>
 * </ul>
 */
public class InstanceofRetrofittingTaskListener implements TaskListener{
    final RecordPatternHelper helper;

    public InstanceofRetrofittingTaskListener(Context context){
        this.helper = new RecordPatternHelper(context);
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        CompilationUnitTree cu = e.getCompilationUnit();
        if(!(cu instanceof JCCompilationUnit)) return;
        new TreeTranslator(){
            @Override
            public <T extends JCTree> T translate(T tree){
                if(tree == null) return null;
                helper.collectRecord(tree);
                if(tree instanceof JCIf) {
                    JCIf ifStmt = (JCIf)tree;
                    JCExpression cond = unwrapParenthesis(ifStmt.cond);
                    if(!(cond instanceof JCInstanceOf)) return super.translate(tree);

                    JCInstanceOf instanceOf = (JCInstanceOf)cond;
                    JCTree pattern = instanceOf.pattern;
                    if(pattern == null) return super.translate(tree);

                    if(isRecordPattern(pattern)){
                        transformRecordPattern(ifStmt, instanceOf, pattern);
                    }else if(isBindingPattern(pattern)){
                        transformBindingPattern(ifStmt, instanceOf, pattern);
                    }
                }
                return super.translate(tree);
            }
        }.translate((JCCompilationUnit)cu);
    }

    @Override
    public void finished(TaskEvent e){

    }

    public static JCExpression unwrapParenthesis(JCExpression expr){
        while(expr instanceof JCParens) expr = ((JCParens)expr).expr;
        return expr;
    }

    /**
     * Transform: {@code if (obj instanceof Point(int x, int y)) { body } } <br>
     * Into: {@code if (obj instanceof Point) { Point $rec = (Point)obj; int x = $rec.x(); ... body } }
     */
    public void transformRecordPattern(JCIf ifStmt, JCInstanceOf instanceOf, JCTree pattern){
        JCExpression recordType = getRecordType(pattern);
        if(recordType == null) return;

        helper.make.at(ifStmt.pos);
        Name tempVar = helper.tempName();

        ListBuffer<JCVariableDecl> declarations = new ListBuffer<>();
        declarations.append(helper.makeVarDef(tempVar, recordType,
            helper.makeCast(recordType, instanceOf.expr)));
        helper.extractRecordBindings(getRecordNested(pattern), helper.make.Ident(tempVar),
            helper.getRecordComponentNames(pattern), declarations);

        instanceOf.pattern = recordType;
        ifStmt.thenpart = buildBlock(declarations.toList(), ifStmt.thenpart);
    }

    /**
     * Transform: {@code if (obj instanceof String s) { body } } <br>
     * Into: {@code if (obj instanceof String) { String s = (String)obj; body } }
     */
    public void transformBindingPattern(JCIf ifStmt, JCInstanceOf instanceOf, JCTree pattern){
        JCVariableDecl var = ((JCBindingPattern)pattern).var;
        if(var == null || var.vartype == null) return;

        helper.make.at(ifStmt.pos);
        instanceOf.pattern = helper.copy(var.vartype);

        ListBuffer<JCVariableDecl> declarations = new ListBuffer<>();
        declarations.append(helper.makeVarDef(var.name, var.vartype,
            helper.makeCast(var.vartype, instanceOf.expr)));
        ifStmt.thenpart = buildBlock(declarations.toList(), ifStmt.thenpart);
    }

    public JCBlock buildBlock(List<JCVariableDecl> declarations, JCStatement body){
        ListBuffer<JCStatement> stmts = new ListBuffer<>();
        for(JCVariableDecl decl : declarations) stmts.append(decl);
        if(body instanceof JCBlock){
            for(JCStatement stmt : ((JCBlock)body).stats) stmts.append(stmt);
        }else if(body != null){
            stmts.append(body);
        }
        return helper.make.Block(0, stmts.toList());
    }
}