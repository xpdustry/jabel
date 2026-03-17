package com.github.bsideup.jabel;

import com.sun.source.util.*;
import com.sun.tools.javac.util.*;

import sun.misc.*;

import java.lang.reflect.*;

/**
 * Prevents the Java compiler from auto-injecting {@code import static java.lang.StringTemplate.STR;}
 * when {@code --enable-preview} is used on JDK 21-22.
 * <p>
 * On JDK 21-22, when preview features are enabled, {@code TypeEnter.staticImports()} injects
 * {@code import static java.lang.StringTemplate.STR;} into every compilation unit so that
 * string template syntax works without an explicit import.  Since Jabel targets Java 8 and
 * the Java 8 bootclasspath does not contain {@code StringTemplate}, this auto-import causes
 * a compilation error whenever {@code --enable-preview} is combined with a Java 8 target.
 * <p>
 * The fix works by temporarily replacing {@code TypeEnter}'s own {@code preview} field with
 * an uninitialized {@code Preview} stub (created via {@code Unsafe.allocateInstance}) whose
 * {@code enabled} flag is {@code false}.  Because the only use of {@code preview} inside
 * {@code TypeEnter} is the single {@code isEnabled()} guard in {@code staticImports()}, the
 * injection is skipped without touching the shared {@code Preview.instance(context)} singleton
 * that all other compiler components use.  The original field value is restored immediately
 * after the ENTER phase for each compilation unit completes.
 * <p>
 * This listener is a no-op on JDK versions that do not have a {@code STRING_TEMPLATES}
 * feature constant (i.e. JDK&nbsp;&lt;&nbsp;21 and JDK&nbsp;&ge;&nbsp;23).
 */
@SuppressWarnings({"deprecation", "removal"})
class PreviewImportCleanupTaskListener implements TaskListener{

    private static final Unsafe UNSAFE;

    static{
        try{
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe)f.get(null);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     * The {@code TypeEnter} singleton for this compilation, or {@code null} if not applicable.
     * We swap its {@code preview} field temporarily during ENTER to block the injection.
     */
    private final Object typeEnterInstance;

    /** Unsafe offset of {@code TypeEnter.preview}, or {@code -1} if not applicable. */
    private final long typeEnterPreviewOffset;

    /** The real {@code Preview} object that lives in {@code TypeEnter.preview}. */
    private final Object originalPreview;

    /**
     * An uninitialized {@code Preview} instance (all fields zero/false/null) used as the
     * temporary replacement.  Its {@code enabled} field is {@code false}, so
     * {@code TypeEnter.staticImports()} sees {@code preview.isEnabled() == false} and skips
     * the {@code StringTemplate.STR} injection entirely.
     */
    private final Object disabledPreview;

    PreviewImportCleanupTaskListener(Context context){
        Object typeEnterInst = null;
        long previewOffset = -1;
        Object origPreview = null;
        Object disabledPrev = null;
        try{
            // STRING_TEMPLATES was introduced in JDK 21 and removed in JDK 23.
            // Using reflection so the class compiles on any JDK version.
            Class<?> featureClass = Class.forName("com.sun.tools.javac.code.Source$Feature");
            featureClass.getField("STRING_TEMPLATES"); // throws NoSuchFieldException on other JDKs

            // Obtain the TypeEnter singleton and locate its `preview` field.
            Class<?> typeEnterClass = Class.forName("com.sun.tools.javac.comp.TypeEnter");
            typeEnterInst = typeEnterClass.getMethod("instance", Context.class).invoke(null, context);
            Field previewField = typeEnterClass.getDeclaredField("preview");
            previewOffset = UNSAFE.objectFieldOffset(previewField);
            origPreview = UNSAFE.getObject(typeEnterInst, previewOffset);

            // Build a zero-initialised Preview stub: isEnabled() returns false (boolean default).
            Class<?> previewClass = Class.forName("com.sun.tools.javac.code.Preview");
            disabledPrev = UNSAFE.allocateInstance(previewClass);
        }catch(Exception ignored){
            // Not JDK 21-22 or reflection unavailable — no action required.
        }
        this.typeEnterInstance = typeEnterInst;
        this.typeEnterPreviewOffset = previewOffset;
        this.originalPreview = origPreview;
        this.disabledPreview = disabledPrev;
    }

    @Override
    public void started(TaskEvent e){
        if(typeEnterInstance == null || e.getKind() != TaskEvent.Kind.ENTER) return;
        UNSAFE.putObject(typeEnterInstance, typeEnterPreviewOffset, disabledPreview);
    }

    @Override
    public void finished(TaskEvent e){
        if(typeEnterInstance == null || e.getKind() != TaskEvent.Kind.ENTER) return;
        UNSAFE.putObject(typeEnterInstance, typeEnterPreviewOffset, originalPreview);
    }
}
