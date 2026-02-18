// Examples made by Claude Opus 4.5

/**
 * Examples of Java 23 features with manual desugaring: <br>
 *
 * <strong>PRIMITIVE_PATTERNS</strong>
 * <pre>
 * // Source (Java 23+):
 * switch (obj) {
 *     case int i -&gt; "int: " + i;
 *     case long l -&gt; "long: " + l;
 *     default -&gt; "other";
 * }
 *
 * // Decompiled (Java 8):
 * if (obj instanceof Integer) {
 *     int i = ((Integer) obj).intValue();
 *     return "int: " + i;
 * } else if (obj instanceof Long) {
 *     long l = ((Long) obj).longValue();
 *     return "long: " + l;
 * } else {
 *     return "other";
 * }
 * </pre>
 */
public class Java23FeaturesExample {

    String primitivePatternSwitch(Object obj) {
        return switch (obj) {
            case int i -> "int: " + i;
            case long l -> "long: " + l;
            case double d -> "double: " + d;
            default -> "other";
        };
    }

    String primitivePatternWithGuard(Object obj) {
        return switch (obj) {
            case int i when i < 0 -> "negative";
            case int i when i == 0 -> "zero";
            case int i -> "positive";
            default -> "not an int";
        };
    }
}
