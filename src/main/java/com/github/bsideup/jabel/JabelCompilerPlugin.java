package com.github.bsideup.jabel;

import com.sun.source.util.*;
import com.sun.tools.javac.api.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.util.*;

import sun.misc.*;

import java.lang.reflect.*;


@SuppressWarnings({"deprecation", "removal"})
public class JabelCompilerPlugin implements Plugin{
    static Unsafe unsafe;
    static{
        try{
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = (Unsafe)unsafeField.get(null);
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        forceSourceFeatures();
    }

    @Override
    public void init(JavacTask task, String... args){
        Context context = ((BasicJavacTask)task).getContext();
        patchCachedFeatures(context);
        patchPreview(context);
        removeUnderscoreWarnings(context);
        task.addTaskListener(new RecordsRetrofittingTaskListener(context));
        task.addTaskListener(new InstanceofRetrofittingTaskListener(context));
        task.addTaskListener(new SwitchRetrofittingTaskListener(context));
        task.addTaskListener(new ImplicitClassRetrofittingTaskListener(context));
    }

    @Override
    public String getName(){
        return "jabel";
    }

    /** Make it auto starts on Java 14+. */
    public boolean autoStart(){
        return true;
    }

    private static void forceSourceFeatures() {
        // We cannot easily force features bellow Java 10.35
        try {
            Class.forName("com.sun.tools.javac.code.Source$Feature");
        }catch(Throwable ignored) {
            return;
        }

        try{
            Field featureField = Source.Feature.class.getDeclaredField("minLevel");
            long featureFieldOffset = unsafe.objectFieldOffset(featureField);

            // List of features that are impossible or too difficult to adapt.
            String[] blacklist = {
                "MODULES",               // Impossible: cannot make a module-info.java on Java 8
                "STRING_TEMPLATES",      // Not relevant: removed in Java 23 because of a confusing design
                "MODULE_IMPORTS",        // Impossible: needs the modules system
                "JAVA_BASE_TRANSITIVE",  // Impossible: needs the modules system
            };

            // We don't care, enable everything except few ones
            for(Source.Feature feat : Source.Feature.values()){
                if(Arrays_contains(blacklist, feat.name())) continue;
                Source current = (Source)unsafe.getObject(feat, featureFieldOffset);
                if(current.ordinal() > Source.JDK8.ordinal())
                    unsafe.putObject(feat, featureFieldOffset, Source.JDK8);
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Several compiler components cache {@code Feature.allowedInSource()} results. <br>
     * Since these objects may be created <em>before</em> Jabel,
     * this method will try to force all {@code allow*} fields to {@code true}.
     */
    private static void patchCachedFeatures(Context context){
        Object[] comps = {
            Attr.instance(context),
            Check.instance(context),
            Resolve.instance(context),
        };

        for(Object comp : comps){
            for(Field f : comp.getClass().getDeclaredFields()){
                try{
                    if(f.getType() != boolean.class || !f.getName().startsWith("allow")) continue;
                    unsafe.putBoolean(comp, unsafe.objectFieldOffset(f), true);
                }catch(Exception ignored){}
             }
        }
    }

    /** Removes warnings about {@code '_'}. */
    private static void removeUnderscoreWarnings(Context context) {
        // Need to inherit a class instead.
        // This is due to DeferredDiagnosticHandler(Predicate) being DeferredDiagnosticHandler(Filter) on Java 16-
        Log.instance(context).new DiscardDiagnosticHandler() {
            @Override
            public void report(JCDiagnostic diag){
                String code = diag.getCode();
                if (code.contains("underscore.as.identifier") ||
                    code.contains("use.of.underscore.not.allowed")) return;
                prev.report(diag);
            }
        };
    }

    private static boolean patchPreview(Context context){
        try {
            Class.forName("com.sun.tools.javac.code.Preview");
        }catch(Throwable ignored) {
            return false; // the class doesn't exists bellow Java 11.10
        }

        Preview preview = Preview.instance(context);

        try{
            // Enable preview features
            Field enabledField = Preview.class.getDeclaredField("enabled");
            unsafe.putBoolean(preview, unsafe.objectFieldOffset(enabledField), true);
        }catch(Exception e){
            System.err.println("WARNING: Failed to enable preview features.");
            return false;
        }

        // Disable preview for TypeEnter, to avoid implicit StringTemplate import on Java 21-23
        try{
            Symtab.class.getDeclaredField("stringTemplateType"); // Simple check
            Field previewField = TypeEnter.class.getDeclaredField("preview");
            TypeEnter enter = TypeEnter.instance(context);
            unsafe.putObject(enter, unsafe.objectFieldOffset(previewField), Preview.instance(new Context()));
        }catch(Exception ignored){}

        try{
            // Disable preview removal warning
            try{
                Field verboseField = Preview.class.getDeclaredField("verbose");
                unsafe.putBoolean(preview, unsafe.objectFieldOffset(verboseField), false);
            }catch(NoSuchFieldException e){
                Field handlerField = Preview.class.getDeclaredField("previewHandler");
                MandatoryWarningHandler handler =
                    (MandatoryWarningHandler)unsafe.getObject(preview, unsafe.objectFieldOffset(handlerField));
                Field handlerVerbose = MandatoryWarningHandler.class.getDeclaredField("verbose");
                unsafe.putBoolean(handler, unsafe.objectFieldOffset(handlerVerbose), false);
            }
        }catch(Exception e){
            System.err.println("WARNING: Failed to suppress preview feature warnings. " +
                               "Don't worry if you see weird messages.");
            return false;
        }

        return true;
    }

    private static <T> boolean Arrays_contains(T[] arr, T item){
        for(T e : arr){
            if(e == item || e != null && e.equals(item))
                return true;
        }
        return false;
    }
}