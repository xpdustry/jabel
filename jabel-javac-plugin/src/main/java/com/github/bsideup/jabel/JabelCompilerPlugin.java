package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.util.*;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.*;


public class JabelCompilerPlugin implements Plugin {
    static {
        Map<String, AsmVisitorWrapper> visitors = new HashMap<String, AsmVisitorWrapper>() {{
            // Disable the preview feature check
            AsmVisitorWrapper checkSourceLevelAdvice = Advice.to(CheckSourceLevelAdvice.class)
                    .on(named("checkSourceLevel").and(takesArguments(2)));

            // Allow features that were introduced together with Records (local enums, static inner members, ...)
            AsmVisitorWrapper allowRecordsEraFeaturesAdvice = new FieldAccessStub("allowRecords", true);

            put("com.sun.tools.javac.parser.JavacParser",
                    new AsmVisitorWrapper.Compound(
                            checkSourceLevelAdvice,
                            allowRecordsEraFeaturesAdvice
                    )
            );
            put("com.sun.tools.javac.parser.JavaTokenizer", checkSourceLevelAdvice);

            put("com.sun.tools.javac.comp.Check", allowRecordsEraFeaturesAdvice);
            put("com.sun.tools.javac.comp.Attr", allowRecordsEraFeaturesAdvice);
            put("com.sun.tools.javac.comp.Resolve", allowRecordsEraFeaturesAdvice);

            // Lower the source requirement for supported features
            put(
                    "com.sun.tools.javac.code.Source$Feature",
                    Advice.to(AllowedInSourceAdvice.class)
                            .on(named("allowedInSource").and(takesArguments(1)))
            );
        }};

        try {
            ByteBuddyAgent.install();
        } catch (Exception e) {
            ByteBuddyAgent.install(
                    new ByteBuddyAgent.AttachmentProvider.Compound(
                            ByteBuddyAgent.AttachmentProvider.ForJ9Vm.INSTANCE,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JVM_ROOT,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JDK_ROOT,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.MACINTOSH,
                            ByteBuddyAgent.AttachmentProvider.ForUserDefinedToolsJar.INSTANCE,
                            ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE
                    )
            );
        }

        ByteBuddy byteBuddy = new ByteBuddy()
                .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);

        ClassLoader classLoader = JavacTask.class.getClassLoader();
        ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
        TypePool typePool = TypePool.ClassLoading.of(classLoader);

        visitors.forEach((className, visitor) -> {
            byteBuddy
                    .decorate(
                            typePool.describe(className).resolve(),
                            classFileLocator
                    )
                    .visit(visitor)
                    .make()
                    .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());
        });

        JavaModule jabelModule = JavaModule.ofType(JabelCompilerPlugin.class);
        ClassInjector.UsingInstrumentation.redefineModule(
                ByteBuddyAgent.getInstrumentation(),
                JavaModule.ofType(JavacTask.class),
                Collections.emptySet(),
                Collections.emptyMap(),
                new HashMap<String, java.util.Set<JavaModule>>() {{
                    put("com.sun.tools.javac.api", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.tree", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.code", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.comp", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.util", Collections.singleton(jabelModule));
                }},
                Collections.emptySet(),
                Collections.emptyMap()
        );
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context context = ((BasicJavacTask) task).getContext();
        removeUnderscoreWarnings(context);

        task.addTaskListener(new RecordsRetrofittingTaskListener(context));
        task.addTaskListener(new InstanceofRetrofittingTaskListener(context));
        task.addTaskListener(new SwitchRetrofittingTaskListener(context));
        task.addTaskListener(new ImplicitClassRetrofittingTaskListener(context));
    }

    @Override
    public String getName() {
        return "jabel";
    }

    // Make it auto start on Java 14+
    public boolean autoStart() {
        return true;
    }

    /** Removes warnings about {@code '_'}. */
    private static void removeUnderscoreWarnings(Context context) {
        // Need to inherit a class instead.
        // This is due to DeferredDiagnosticHandler(Predicate) being DeferredDiagnosticHandler(Filter) on Java 16-
        Log.instance(context).new DiscardDiagnosticHandler() {
            @Override
            public void report(JCDiagnostic diag) {
                String code = diag.getCode();
                if (code.contains("underscore.as.identifier") ||
                    code.contains("use.of.underscore.not.allowed")) return;
                prev.report(diag);
            }
        };
    }

    static class AllowedInSourceAdvice {
        @Advice.OnMethodEnter
        static void allowedInSource(
                @Advice.This Source.Feature feature,
                @Advice.Argument(value = 0, readOnly = false) Source source
        ) {
            switch (feature.name()) {
                case "MODULES":               // Impossible: cannot make a module-info.java on Java 8
                case "STRING_TEMPLATES":      // Not relevant: removed in Java 23 because of a confusing design
                case "MODULE_IMPORTS":        // Impossible: needs the modules system
                case "JAVA_BASE_TRANSITIVE":  // Impossible: needs the modules system
                    break;
                default:
                    //noinspection UnusedAssignment
                    source = Source.DEFAULT;
            }
        }
    }

    static class CheckSourceLevelAdvice {
        @Advice.OnMethodEnter
        static void checkSourceLevel(
                @Advice.Argument(value = 1, readOnly = false) Source.Feature feature
        ) {
            if (feature.allowedInSource(Source.JDK8)) {
                // This must be one of the cases from "AllowedInSourceAdvice"
                //noinspection UnusedAssignment
                feature = Source.Feature.PRIVATE_SAFE_VARARGS;
            }
        }
    }

    private static class FieldAccessStub extends AsmVisitorWrapper.AbstractBase {
        final String fieldName;
        final Object value;

        public FieldAccessStub(String fieldName, Object value) {
            this.fieldName = fieldName;
            this.value = value;
        }

        @Override
        public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext, TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
            return new ClassVisitor(Opcodes.ASM9, classVisitor) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.GETFIELD && fieldName.equalsIgnoreCase(name)) {
                                super.visitInsn(Opcodes.POP);
                                super.visitLdcInsn(value);
                            } else {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        }
                    };
                }
            };
        }
    }
}
