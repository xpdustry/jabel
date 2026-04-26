package com.github.bsideup.jabel;

import java.util.*;

import com.sun.source.util.*;
import com.sun.tools.javac.api.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.util.*;

import net.bytebuddy.*;
import net.bytebuddy.agent.*;
import net.bytebuddy.asm.*;
import net.bytebuddy.description.type.*;
import net.bytebuddy.dynamic.*;
import net.bytebuddy.dynamic.loading.*;
import net.bytebuddy.dynamic.scaffold.*;
import net.bytebuddy.pool.*;
import net.bytebuddy.utility.*;

import static net.bytebuddy.matcher.ElementMatchers.*;


public class JabelCompilerPlugin implements Plugin {
    static final boolean JABEL_INITIALIZED = initJabel();

    @SuppressWarnings("resource")
    private static boolean initJabel() {
        // We cannot easily force features bellow Java 10.35
        try {
            Class.forName("com.sun.tools.javac.code.Source$Feature");
        } catch (Exception e) {
            return false;
        }

        // Install ByteBuddy
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
        ByteBuddy byteBuddy = new ByteBuddy().with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);


        // Hook classes
        ClassLoader classLoader = JavacTask.class.getClassLoader();
        ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
        TypePool typePool = TypePool.ClassLoading.of(classLoader);
        TypeDescription clazz;

        // Lower features source level
        clazz = typePool.describe("com.sun.tools.javac.code.Source$Feature").resolve();
        byteBuddy.decorate(clazz, classFileLocator)
                 .visit(Advice.to(AllowedInSourceAdvice.class).on(named("allowedInSource").and(takesArguments(1))))
                 .make()
                 .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());

        // Force enable preview features and suppress its warnings
        clazz = typePool.describe("com.sun.tools.javac.code.Preview").resolve();
        byteBuddy.decorate(clazz, classFileLocator)
                 .visit(Advice.to(IsEnabledAdvice.class).on(named("isEnabled").and(takesArguments(0))))
                 .visit(Advice.to(IsPreviewAdvice.class).on(named("isPreview").and(takesArguments(1))))
                 .visit(Advice.to(WarnPreviewAdvice.class).on(named("warnPreview")))
                 .make()
                 .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());


        // Open internal compiler packages
        Set<JavaModule> jabelModule = Collections.singleton(JavaModule.ofType(JabelCompilerPlugin.class));
        ClassInjector.UsingInstrumentation.redefineModule(
                ByteBuddyAgent.getInstrumentation(),
                JavaModule.ofType(JavacTask.class),
                Collections.emptySet(),
                Collections.emptyMap(),
                new HashMap<String, Set<JavaModule>>() {{
                    put("com.sun.tools.javac.api", jabelModule);
                    put("com.sun.tools.javac.tree", jabelModule);
                    put("com.sun.tools.javac.code", jabelModule);
                    put("com.sun.tools.javac.comp", jabelModule);
                    put("com.sun.tools.javac.util", jabelModule);
                }},
                Collections.emptySet(),
                Collections.emptyMap()
        );

        return true;
    }

    @Override
    public void init(JavacTask task, String... args) {
        // Useless to continue if Jabel was not initialized correctly
        if (!JABEL_INITIALIZED) return;

        Context context = ((BasicJavacTask) task).getContext();
        removeUnderscoreWarnings(context);

        task.addTaskListener(new RecordsRetrofittingTaskListener(context));
        task.addTaskListener(new RecordPatternRetrofittingTaskListener(context));
        task.addTaskListener(new SwitchRetrofittingTaskListener(context));
        task.addTaskListener(new FlexibleMainRetrofittingTaskListener(context));
        task.addTaskListener(new ImplicitClassesFixerTaskListener(context));
    }

    @Override
    public String getName() {
        return "jabel";
    }

    /** Make it auto starts on Java 14+. */
    @Override
    public boolean autoStart() {
        return true;
    }

    /** Removes warnings about {@code '_'}. */
    static void removeUnderscoreWarnings(Context context){
        Log.instance(context).new DiscardDiagnosticHandler(){
            @Override
            public void report(JCDiagnostic diag){
                String code = diag.getCode();
                if (code.contains("underscore.as.identifier") ||
                    code.contains("use.of.underscore.not.allowed")) return;
                prev.report(diag);
            }
        };
    }

    /** Makes all {@link Source.Feature} available in all source levels, except few ones. */
    static class AllowedInSourceAdvice {
        @Advice.OnMethodEnter
        static void allowedInSource(
                @Advice.This Source.Feature feature,
                @Advice.Argument(value = 0, readOnly = false) Source source
        ) {
            switch (feature.name()) {
                case "MODULES":               // Extremely difficult as initialization is done very early
                case "STRING_TEMPLATES":      // Appeared on Java 21 and removed on Java 23 because of a confusing design
                case "MODULE_IMPORTS":        // Needs the modules system
                case "JAVA_BASE_TRANSITIVE":  // Needs the modules system
                    break;
                default:
                    //noinspection UnusedAssignment
                    source = Source.DEFAULT;
            }
        }
    }

    /** Makes {@link Preview#isEnabled()} always return {@code true}. */
    static class IsEnabledAdvice {
        @Advice.OnMethodExit
        static void isEnabled(@Advice.Return(readOnly = false) boolean result) {
            //noinspection UnusedAssignment
            result = true;
        }
    }

    /** Makes {@link Preview#isPreview(Feature)} always return {@code false}. */
    static class IsPreviewAdvice {
        @Advice.OnMethodExit
        static void isPreview(@Advice.Return(readOnly = false) boolean result) {
            //noinspection UnusedAssignment
            result = false;
        }
    }

    /** Makes {@link Preview#warnPreview(DiagnosticPosition, Feature)} a no-op. */
    static class WarnPreviewAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean warnPreview() {
            return true; // skip method body
        }
    }
}
