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
 * The fix temporarily clears the {@code Preview.enabled} flag during the ENTER phase for
 * each compilation unit so that {@code TypeEnter.staticImports()} skips the injection.
 * The flag is restored to its original value immediately after ENTER completes, so all
 * other preview-feature checks remain unaffected.
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

    /** The {@code Preview} instance for this compilation, or {@code null} if not applicable. */
    private final Object preview;

    /** Unsafe offset of {@code Preview.enabled}, or {@code -1} if not applicable. */
    private final long enabledOffset;

    /** Captured value of {@code Preview.enabled} before we clear it for each ENTER event. */
    private boolean originalEnabled;

    PreviewImportCleanupTaskListener(Context context){
        Object previewInstance = null;
        long offset = -1;
        try{
            // STRING_TEMPLATES was introduced in JDK 21 and removed in JDK 23.
            // Using reflection so the class compiles on any JDK version.
            Class<?> featureClass = Class.forName("com.sun.tools.javac.code.Source$Feature");
            featureClass.getField("STRING_TEMPLATES"); // throws NoSuchFieldException on other JDKs

            Class<?> previewClass = Class.forName("com.sun.tools.javac.code.Preview");
            previewInstance = previewClass.getMethod("instance", Context.class).invoke(null, context);
            Field enabledField = previewClass.getDeclaredField("enabled");
            offset = UNSAFE.objectFieldOffset(enabledField);
        }catch(Exception ignored){
            // Not JDK 21-22 or reflection unavailable — no action required.
        }
        this.preview = previewInstance;
        this.enabledOffset = offset;
    }

    @Override
    public void started(TaskEvent e){
        if(preview == null || e.getKind() != TaskEvent.Kind.ENTER) return;
        originalEnabled = UNSAFE.getBoolean(preview, enabledOffset);
        if(originalEnabled){
            UNSAFE.putBoolean(preview, enabledOffset, false);
        }
    }

    @Override
    public void finished(TaskEvent e){
        if(preview == null || e.getKind() != TaskEvent.Kind.ENTER) return;
        if(originalEnabled){
            UNSAFE.putBoolean(preview, enabledOffset, true);
        }
    }
}
