// Examples made by Claude Opus 4.5

/**
 * Examples of Java 25 features with manual desugaring: <br>
 *
 * <strong>FLEXIBLE_CONSTRUCTORS</strong>
 * <pre>
 * // Source (Java 25+):
 * class Child extends Parent {
 *     Child(int value) {
 *         if (value &lt; 0) throw new IllegalArgumentException();
 *         int processed = value * 2;
 *         super(processed);
 *     }
 * }
 *
 * // Decompiled (Java 8):
 * class Child extends Parent {
 *     Child(int value) {
 *         super(validateAndProcess(value));
 *     }
 *     private static int validateAndProcess(int value) {
 *         if (value &lt; 0) throw new IllegalArgumentException();
 *         return value * 2;
 *     }
 * }
 * </pre>
 */
public class Java25FeaturesExample {

    static class Parent {
        final int value;
        Parent(int value) { this.value = value; }
    }

    static class ValidatedChild extends Parent {
        ValidatedChild(int value) {
            if (value < 0) throw new IllegalArgumentException("negative");
            super(value);
        }
    }

    static class ProcessedChild extends Parent {
        ProcessedChild(int value) {
            int processed = value * 2;
            super(processed);
        }
    }

    static class ComplexChild extends Parent {
        final String description;

        ComplexChild(int value) {
            String desc;
            if (value > 0) {
                desc = "positive";
            } else {
                desc = "non-positive";
            }
            super(Math.abs(value));
            this.description = desc;
        }
    }
}
