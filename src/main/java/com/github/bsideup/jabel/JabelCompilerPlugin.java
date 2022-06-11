package com.github.bsideup.jabel;

import com.sun.source.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Source.*;

import java.lang.reflect.*;

public class JabelCompilerPlugin implements Plugin{
    static{
        try{
            Field field = Source.Feature.class.getDeclaredField("minLevel");
            field.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            Source.Feature[] feats = {
            Feature.PRIVATE_SAFE_VARARGS, Feature.SWITCH_EXPRESSION, Feature.SWITCH_RULE, Feature.SWITCH_MULTIPLE_CASE_LABELS,
            Feature.LOCAL_VARIABLE_TYPE_INFERENCE, Feature.VAR_SYNTAX_IMPLICIT_LAMBDAS, Feature.DIAMOND_WITH_ANONYMOUS_CLASS_CREATION,
            Feature.EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES, Feature.TEXT_BLOCKS, Feature.PATTERN_MATCHING_IN_INSTANCEOF,
            Feature.REIFIABLE_TYPES_INSTANCEOF
            };

            for(Source.Feature feat : feats){
                field.set(feat, Source.JDK8);
            }

        }catch(Exception e){
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