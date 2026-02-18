package com.github.bsideup.jabel;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;

import javax.lang.model.element.Modifier;

import java.util.Iterator;
import java.util.stream.Stream;


class RecordsRetrofittingTaskListener implements TaskListener{
    final Context context;
    final TreeMaker make;
    final Symtab syms;
    final Names names;

    public RecordsRetrofittingTaskListener(Context context){
        this.context = context;
        make = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        names = Names.instance(context);
    }

    @Override
    public void started(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ENTER) return;
        new TreeScanner<Void, Void>(){
            @Override
            public Void visitClass(ClassTree node, Void aVoid){
                if("RECORD".equals(node.getKind().toString())){
                    JCClassDecl classDecl = (JCClassDecl)node;
                    if(classDecl.extending == null){
                        // Prevent implicit "extends java.lang.Record"
                        classDecl.extending = make.Type(syms.objectType);
                    }

                    generateToStringIfNeeded(classDecl);
                    generateHashcodeIfNeeded(classDecl);
                    generateEqualsIfNeeded(classDecl);
                }
                return super.visitClass(node, aVoid);
            }
        }.scan(e.getCompilationUnit(), null);
    }

    @Override
    public void finished(TaskEvent e){
        if(e.getKind() != TaskEvent.Kind.ANALYZE) return;
        new TreeScanner<Void, Void>(){
            @Override
            public Void visitClass(ClassTree node, Void aVoid){
                if("RECORD".equals(node.getKind().toString())){
                    JCClassDecl classDecl = (JCClassDecl)node;
                    if(classDecl.sym != null){
                        // Remove RECORD flag to avoid invalid ASM reading
                        classDecl.sym.flags_field &= ~Flags.RECORD;
                    }
                }
                return super.visitClass(node, aVoid);
            }
        }.scan(e.getCompilationUnit(), null);
    }

    public void generateToStringIfNeeded(JCClassDecl classDecl) {
        if(containsMethod(classDecl, names.toString)) return;
        classDecl.defs = classDecl.defs.append(make.MethodDef(
            new Symbol.MethodSymbol(
                Flags.PUBLIC,
                names.toString,
                new Type.MethodType(
                    List.nil(),
                    syms.stringType,
                    List.nil(),
                    syms.methodClass
                ),
                syms.objectType.tsym
            ),
            make.Block(0, generateToString(classDecl)))
        );
    }

    public void generateHashcodeIfNeeded(JCClassDecl classDecl) {
        if(containsMethod(classDecl, names.hashCode)) return;
        classDecl.defs = classDecl.defs.append(make.MethodDef(
            new Symbol.MethodSymbol(
                Flags.PUBLIC,
                names.hashCode,
                new Type.MethodType(
                    List.nil(),
                    syms.intType,
                    List.nil(),
                    syms.methodClass
                ),
                syms.objectType.tsym
            ),
            make.Block(0, generateHashCode(classDecl)))
        );
    }

    public void generateEqualsIfNeeded(JCClassDecl classDecl) {
        if(containsMethod(classDecl, names.equals)) return;
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(
            Flags.PUBLIC | Flags.FINAL, names.equals,
            new Type.MethodType(
                List.of(syms.objectType),
                syms.booleanType,
                List.nil(),
                syms.methodClass
            ),
            syms.objectType.tsym
        );
        JCMethodDecl methodDecl = make.MethodDef(
            methodSymbol,
            make.Block(0, generateEquals(classDecl, methodSymbol.params().head.name))
        );

        // THIS ONE IS IMPORTANT! Otherwise, Flow.AssignAnalyzer#visitVarDef will have track=false
        methodDecl.params.head.pos = classDecl.pos;
        classDecl.defs = classDecl.defs.append(methodDecl);
    }

    public boolean containsMethod(JCClassDecl classDecl, Name name){
        for(Iterator<JCTree> iterator = classDecl.defs.iterator(); iterator.hasNext();){
            JCTree next = iterator.next();
            if (!(next instanceof JCMethodDecl)) continue;
            JCMethodDecl def = (JCMethodDecl)next;
            if (def.getName() == name) return true;
            if(name != names.equals || def.params.size() != 1) continue;
            // TODO find a better way?
            switch(def.params.get(0).getType().toString()){
                case "java.lang.Object":
                case "Object":
                    return true;
                default:
            }
        }
        return false;
    }

    public Stream<JCVariableDecl> getRecordComponents(JCClassDecl classDecl){
        return classDecl.getMembers().stream()
                        .filter(JCVariableDecl.class::isInstance)
                        .map(JCVariableDecl.class::cast)
                        .filter(it -> !it.getModifiers().getFlags().contains(Modifier.STATIC));
    }

    public List<JCStatement> generateToString(JCClassDecl classDecl){
        JCExpression stringBuilder = make.NewClass(
            null,
            null,
            make.QualIdent(syms.stringBuilderType.tsym),
            List.of(make.Literal(classDecl.name + "[")),
            null
        );

        for(Iterator<JCVariableDecl> iterator = getRecordComponents(classDecl).iterator(); iterator.hasNext();){
            JCVariableDecl fieldDecl = iterator.next();
            Name fieldName = fieldDecl.name;

            stringBuilder = make.App(
                make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                List.of(make.Literal(fieldName + "="))
            );

            stringBuilder = make.App(
                make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                List.of(make.Select(make.This(Type.noType), fieldName))
            );

            if(iterator.hasNext()){
                stringBuilder = make.App(
                    make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                    List.of(make.Literal(","))
                );
            }
        }

        stringBuilder = make.App(
            make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
            List.of(make.Literal("]"))
        );

        return List.of(make.Return(make.App(make.Select(stringBuilder, names.toString).setType(syms.stringType))));
    }

    public List<JCStatement> generateEquals(JCClassDecl classDecl, Name otherName){
        ListBuffer<JCStatement> statements = new ListBuffer<>();

        // if (o == this) return true;
        statements.add(make.If(
            make.Binary(
                Tag.EQ,
                make.This(Type.noType),
                make.Ident(otherName)
            ),
            make.Return(make.Literal(true)),
            null
        ));

        // if (o == null) return false;
        statements.add(make.If(
            make.Binary(
                Tag.EQ,
                make.Ident(otherName),
                make.Literal(TypeTag.BOT, null)
            ),
            make.Return(make.Literal(false)),
            null
        ));

        // if (o.getClass() != getClass()) return false;
        statements.add(make.If(
            make.Binary(
                Tag.EQ,
                make.App(make.Select(make.Ident(otherName), names.getClass).setType(syms.classType)),
                make.App(make.Select(make.This(Type.noType), names.getClass).setType(syms.classType))
            ),
            make.Block(0, List.nil()),
            make.Return(make.Literal(false))
        ));

        // Create casted variable: ClassName other = (ClassName)o;
        Name thatName = names.fromString("other");
        statements.add(make.VarDef(
            make.Modifiers(0L),
            thatName,
            make.Ident(classDecl.name),
            make.TypeCast(make.Ident(classDecl.name), make.Ident(otherName))
        ));

        // fields - use the casted variable
        for(Iterator<JCVariableDecl> iterator = getRecordComponents(classDecl).iterator(); iterator.hasNext();){
            JCVariableDecl fieldDecl = iterator.next();
            JCExpression myFieldAccess = make.Select(make.This(Type.noType), fieldDecl.name);
            JCExpression otherFieldAccess = make.Select(make.Ident(thatName), fieldDecl.name);

            final JCExpression condition;
            if(fieldDecl.getType() instanceof JCPrimitiveTypeTree){
                condition = make.Binary(Tag.EQ, otherFieldAccess, myFieldAccess);
            }else{
                condition = make.App(
                    // call Objects.equals
                    make.Select(
                        make.QualIdent(syms.objectsType.tsym),
                        names.equals
                    ).setType(syms.objectsType),
                    List.of(otherFieldAccess, myFieldAccess)
                );
            }
            statements.add(make.If(
                condition,
                make.Block(0, List.nil()),
                make.Return(make.Literal(false))
            ));
        }

        // return true;
        statements.add(make.Return(make.Literal(true)));

        return statements.toList();
    }


    public List<JCStatement> generateHashCode(JCClassDecl classDecl) {
        ListBuffer<JCExpression> expressions = new ListBuffer<>();

        for(Iterator<JCVariableDecl> iterator = getRecordComponents(classDecl).iterator(); iterator.hasNext();){
            JCVariableDecl fieldDecl = iterator.next();

            JCTree fType = fieldDecl.getType();
            JCExpression myFieldAccess = make.Select(make.This(Type.noType), fieldDecl.name);

            if(fType instanceof JCPrimitiveTypeTree){
                switch(((JCPrimitiveTypeTree)fType).getPrimitiveTypeKind()){
                    case BOOLEAN:
                        /* this.fieldName ? 1 : 0 */
                        expressions.append(
                            make.Conditional(
                                myFieldAccess,
                                make.Literal(TypeTag.INT, 1),
                                make.Literal(TypeTag.INT, 0)
                            )
                        );
                        break;

                    case LONG:
                        expressions.append(make.TypeCast(
                            make.TypeIdent(syms.intType.getTag()),
                            make.Parens(
                                make.Binary(
                                    Tag.BITXOR,
                                    myFieldAccess,
                                    make.Parens(make.Binary(Tag.USR, myFieldAccess, make.Literal(32)))
                                )
                            )
                        ));
                        break;

                    case FLOAT:
                        /* this.fieldName != 0f ? Float.floatToIntBits(this.fieldName) : 0 */
                        expressions.append(
                            make.Conditional(
                                make.Binary(Tag.NE, myFieldAccess, make.Literal(0f)),
                                make.App(
                                    make.Select(
                                        make.Ident(names.fromString("Float")),
                                        names.fromString("floatToIntBits")).setType(syms.intType),
                                    List.of(myFieldAccess)
                                ),
                                make.Literal(TypeTag.INT, 0)
                            )
                        );
                        break;

                    case DOUBLE:
                        /* Double.hashCode(this.fieldName) */
                        expressions.append(
                            make.App(
                                make.Select(
                                    make.Ident(names.fromString("Double")),
                                    names.fromString("hashCode")).setType(syms.intType),
                                List.of(myFieldAccess)
                            )
                        );
                        break;

                    case BYTE:
                    case SHORT:
                    case INT:
                    case CHAR:
                    default:
                        /* just the field */
                        expressions.append(myFieldAccess);
                        break;
                }

            }else if(fType instanceof JCArrayTypeTree){
                expressions.append(
                    make.App(
                        make.Select(
                            make.Select(
                                make.Select(
                                    make.Ident(names.fromString("java")),
                                    names.fromString("util")
                                ),
                                names.fromString("Arrays")
                            ),
                            names.fromString("hashCode")
                        ).setType(syms.intType),
                        List.of(myFieldAccess)
                    )
                );

            }else{
                /* (this.fieldName != null ? this.fieldName.hashCode() : 0) */
                expressions.append(
                    make.Conditional(
                        make.Binary(Tag.NE, myFieldAccess, make.Literal(TypeTag.BOT, null)),
                        make.App(make.Select(myFieldAccess, names.hashCode).setType(syms.intType)),
                        make.Literal(0)
                    )
                );
            }
        }

        ListBuffer<JCStatement> statements = new ListBuffer<>();
        Name resultName = names.fromString("result");
        statements.append(
            make.VarDef(
                make.Modifiers(0L),
                resultName,
                make.TypeIdent(syms.intType.getTag()),
                make.Literal(0)
            )
        );

        for(JCExpression expression : expressions){
            // result = 31 * result + ${expr}
            statements.append(make.Exec(
                make.Assign(
                    make.Ident(resultName),
                    make.Binary(
                        Tag.PLUS,
                        make.Binary(Tag.MUL, make.Literal(TypeTag.INT, 31), make.Ident(resultName)),
                        expression
                    )
                )
            ));
        }

        statements.append(make.Return(make.Ident(resultName)));
        return statements.toList();
    }
}
