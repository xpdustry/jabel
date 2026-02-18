// Examples made by Claude Opus 4.5

import java.util.List;

/**
 * Examples of Java 22 features desugared by the compiler: <br>
 *
 * <strong>UNNAMED_VARIABLES</strong>
 * <pre>
 * // Source (Java 22+):
 * for (var _ : list) { count++; }
 * try { } catch (Exception _) { }
 * list.forEach(_ -&gt; count++);
 *
 * // Decompiled (Java 8):
 * for (Object $unused : list) { count++; }
 * try { } catch (Exception $unused) { }
 * list.forEach($unused -&gt; count++);
 * </pre>
 */
public class Java22FeaturesExample {

    void unnamedInForEach() {
        var list = List.of("a", "b", "c");
        int count = 0;

        for (var _ : list) {
            count++;
        }
    }

    void unnamedInCatch() {
        try {
            throw new RuntimeException();
        } catch (RuntimeException _) {
            System.out.println("caught");
        }
    }

    void unnamedInLambda() {
        var list = List.of("a", "b", "c");
        var count = new int[]{0};
        list.forEach(_ -> count[0]++);
    }
}
