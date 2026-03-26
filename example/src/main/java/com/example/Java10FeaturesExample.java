// Examples made by Claude Opus 4.5

package com.example;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Examples of Java 10 features desugared by the compiler: <br>
 *
 * <strong>LOCAL_VARIABLE_TYPE_INFERENCE (var)</strong>
 * <pre>
 * // Source (Java 10+):
 * var str = "hello";
 * var list = new ArrayList&lt;String&gt;();
 * for (var i = 0; i &lt; 10; i++) { }
 * for (var item : list) { }
 *
 * // Decompiled (Java 8):
 * String str = "hello";
 * ArrayList&lt;String&gt; list = new ArrayList&lt;String&gt;();
 * for (int i = 0; i &lt; 10; i++) { }
 * for (String item : list) { }
 * </pre>
 */
public class Java10FeaturesExample {

    void varExamples() {
        var str = "hello";
        var num = 42;
        var list = new ArrayList<String>();
        var map = new HashMap<String, Integer>();

        list.add("item");
        map.put("key", 1);

        for (var i = 0; i < 3; i++) {
            list.add("item" + i);
        }
        for (var item : list) {
            System.out.println(item);
        }
    }
}
