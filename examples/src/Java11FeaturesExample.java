// Examples made by Claude Opus 4.5

import java.util.function.BiFunction;

/**
 * Examples of Java 11 features desugared by the compiler: <br>
 *
 * <strong>VAR_SYNTAX_IMPLICIT_LAMBDAS</strong>
 * <pre>
 * // Source (Java 11+):
 * BiFunction&lt;String, String, String&gt; f = (var a, var b) -&gt; a + b;
 * // Allows annotations:
 * Consumer&lt;String&gt; c = (@NonNull var s) -&gt; System.out.println(s);
 *
 * // Decompiled (Java 8):
 * BiFunction&lt;String, String, String&gt; f = (a, b) -&gt; a + b;
 * // or with explicit types:
 * BiFunction&lt;String, String, String&gt; f = (String a, String b) -&gt; a + b;
 * </pre>
 */
public class Java11FeaturesExample {

    BiFunction<String, String, String> concat = (var a, var b) -> a + b;

    BiFunction<Integer, Integer, Integer> add =
        (var x, var y) -> x + y;
}
