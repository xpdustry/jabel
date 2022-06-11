package com.github.bsideup.jabel;

import com.sun.source.util.*;
import com.sun.tools.javac.code.*;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.*;
import java.lang.reflect.*;

public class JabelCompilerPlugin implements Plugin{
    static{
        try{
            Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
            field.setAccessible(true);

            Lookup lookup = (Lookup)field.get(null);
            MethodHandle set = lookup.findSetter(Source.Feature.class, "minLevel", Source.class);

            String[] feats = {
            "PRIVATE_SAFE_VARARGS", "SWITCH_EXPRESSION", "SWITCH_RULE", "SWITCH_MULTIPLE_CASE_LABELS",
            "LOCAL_VARIABLE_TYPE_INFERENCE", "VAR_SYNTAX_IMPLICIT_LAMBDAS", "DIAMOND_WITH_ANONYMOUS_CLASS_CREATION",
            "EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES", "TEXT_BLOCKS", "PATTERN_MATCHING_IN_INSTANCEOF",
            "REIFIABLE_TYPES_INSTANCEOF"
            };

            for(String name : feats){
                try{
                    Source.Feature feat = Source.Feature.valueOf(name);
                    set.invokeExact(feat, Source.JDK8);
                }catch(IllegalArgumentException e){
                    System.err.println("Unknown feature: " + e.getMessage());
                }
            }

        }catch(Throwable e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(JavacTask task, String... args){

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