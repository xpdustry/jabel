// Examples made by Claude Opus 4.5

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Examples of Java 9 features desugared by the compiler: <br>
 *
 * <strong>PRIVATE_SAFE_VARARGS</strong>
 * <pre>
 * // Source (Java 9+):
 * @SafeVarargs
 * private void method(List&lt;String&gt;... lists) { }
 *
 * // Decompiled (Java 8): same code, annotation preserved
 * </pre>
 * <p>
 * <strong>DIAMOND_WITH_ANONYMOUS_CLASS_CREATION</strong>
 * <pre>
 * // Source (Java 9+):
 * List&lt;String&gt; list = new ArrayList&lt;&gt;() { };
 *
 * // Decompiled (Java 8):
 * List&lt;String&gt; list = new ArrayList&lt;String&gt;() { };
 * </pre>
 * <p>
 * <strong>EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES</strong>
 * <pre>
 * // Source (Java 9+):
 * Closeable c = ...;
 * try (c) { }
 *
 * // Decompiled (Java 8):
 * Closeable c = ...;
 * try (Closeable c2 = c) { }
 * </pre>
 * <p>
 * <strong>PRIVATE_INTERFACE_METHODS</strong>
 * <pre>
 * // Source (Java 9+):
 * interface I {
 *     private String helper() { return "!"; }
 *     default String greet() { return "Hi" + helper(); }
 * }
 *
 * // Decompiled (Java 8): same code (private methods in interfaces supported in bytecode)
 * </pre>
 */
public class Java9FeaturesExample {

    @SafeVarargs
    private final void safeVarargsMethod(List<String>... lists) {
        for (var list : lists) System.out.println(list);
    }

    List<String> diamondWithAnonymous = new ArrayList<>() {
        @Override
        public boolean add(String s) {
            return super.add(s.toUpperCase());
        }
    };

    void effectivelyFinalTryWithResources() throws Exception {
        Closeable resource = () -> System.out.println("closed");
        try (resource) {
            System.out.println("using resource");
        }
    }

    interface WithPrivateMethods {
        private String helper() { return "!"; }
        default String greet(String name) { return "Hello " + name + helper(); }
    }
}
