package com.github.bsideup.jabel;

import com.sun.source.util.*;
import com.sun.tools.javac.api.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.util.*;

import sun.misc.*;

import java.lang.reflect.*;

@SuppressWarnings({"deprecation", "removal"})
public class JabelCompilerPlugin implements Plugin{
    static{
        try{
            Field field = Source.Feature.class.getDeclaredField("minLevel");
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe)unsafeField.get(null);
            long staticFieldOffset = unsafe.objectFieldOffset(field);
            String[] feats = {
                // Java 9
                "PRIVATE_SAFE_VARARGS",
                "DIAMOND_WITH_ANONYMOUS_CLASS_CREATION",
                "EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES",
                "PRIVATE_INTERFACE_METHODS",
                //"MODULES",               // Impossible: cannot make a module-info.java in Java 8
                // Java 10
                "LOCAL_VARIABLE_TYPE_INFERENCE",
                // Java 11
                "VAR_SYNTAX_IMPLICIT_LAMBDAS",
                // Java 14
                "SWITCH_MULTIPLE_CASE_LABELS",
                "SWITCH_RULE",
                "SWITCH_EXPRESSION",
                "NO_TARGET_ANNOTATION_APPLICABILITY",
                // Java 15
                "TEXT_BLOCKS",
                // Java 16
                "PATTERN_MATCHING_IN_INSTANCEOF",
                "REIFIABLE_TYPES_INSTANCEOF",
                "RECORDS",
                // Java 17
                "SEALED_CLASSES",
                "REDUNDANT_STRICTFP",
                // Java 19
                "PRIVATE_MEMBERS_IN_PERMITS_CLAUSE", //appeared in Java24+
                // Java 21
                "CASE_NULL",
                "PATTERN_SWITCH",
                "UNCONDITIONAL_PATTERN_IN_INSTANCEOF",
                "RECORD_PATTERNS",
                //"STRING_TEMPLATES",      // Not relevant: removed in Java 23 because of a confusing design
                //"WARN_ON_ILLEGAL_UTF8",  // Not relevant: was just a warning and removed since Java 21
                // Java 22
                "UNNAMED_VARIABLES",
                // Java 23
                "PRIMITIVE_PATTERNS",      // TODO: still a preview but i keep it
                // Java 24
                "ERASE_POLY_SIG_RETURN_TYPE",
                // Java 25
                //"FLEXIBLE_CONSTRUCTORS", // Not relevant: very hard to implement
                "IMPLICIT_CLASSES",
                //"MODULE_IMPORTS",        // Impossible: needs the modules system
                //"JAVA_BASE_TRANSITIVE",  // Impossible: needs the modules system
            };

            for(String name : feats){
                try{
                    Source.Feature feat = Source.Feature.valueOf(name);
                    unsafe.putObject(feat, staticFieldOffset, Source.JDK8);
                }catch(IllegalArgumentException e){
                    String msg = e.getMessage() == null ? name : e.getMessage().replace("No enum constant ", "");
                    System.err.println("WARNING: Unknown feature: " + msg);
                }
            }

        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(JavacTask task, String... args){
        Context context = ((BasicJavacTask)task).getContext();
        task.addTaskListener(new InstanceofRetrofittingTaskListener(context));
        task.addTaskListener(new RecordsRetrofittingTaskListener(context));
        task.addTaskListener(new SwitchRetrofittingTaskListener(context));
    }

    @Override
    public String getName(){
        return "jabel";
    }

    // Make it auto start on Java 14+
    public boolean autoStart(){
        return true;
    }
}