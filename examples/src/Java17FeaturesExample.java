// Examples made by Claude Opus 4.5


/**
 * Examples of Java 17 features desugared by the compiler: <br>
 *
 * <strong>SEALED_CLASSES</strong>
 * <pre>
 * // Source (Java 17+):
 * public sealed class Shape permits Circle, Square { }
 * public final class Circle extends Shape { }
 * public non-sealed class Square extends Shape { }
 *
 * // Decompiled (Java 8):
 * public abstract class Shape { }  // sealed, permits removed
 * public final class Circle extends Shape { }
 * public class Square extends Shape { }  // non-sealed removed
 * </pre>
 * <p>
 * <strong>REDUNDANT_STRICTFP</strong>
 * <pre>
 * // Source (Java 17+):
 * public strictfp class Math { }
 *
 * // Decompiled (Java 8): same code (strictfp kept but redundant since Java 17)
 * </pre>
 * <p>
 * <strong>PATTERN_SWITCH (preview since JDK 17)</strong>
 * <pre>
 * // Source (Java 17+ preview):
 * switch (obj) {
 *     case String s -> s.length();
 *     case Integer i -> i;
 *     default -> 0;
 * }
 * </pre>
 */
public class Java17FeaturesExample {

    sealed class Shape permits Circle, Square, Rectangle {
        private final String name;
        Shape(String name) { this.name = name; }
        String getName() { return name; }
    }

    final class Circle extends Shape {
        Circle() { super("Circle"); }
        void test(){
            Square.test();
        }
    }

    final class Square extends Shape {
        Square() { super("Square"); }
        static void test() {}
    }

    non-sealed class Rectangle extends Shape {
        Rectangle() { super("Rectangle"); }
    }

    class SpecialRectangle extends Rectangle {}

    strictfp double strictMethod(double a, double b) {
        return a * b + a / b;
    }

    //// Preview features testing

    String patternSwitch(Object obj) {
        return switch (obj) {
            case String s -> "String: " + s;
            case null -> "null";
            case Integer i -> "Integer: " + i;
            default -> "other";
        };
    }

    String patternSwitchStatement(Object obj) {
        String result;
        switch (obj) {
            case String s:
                result = "String: " + s;
                break;
            case null:
                result = "null";
                break;
            case Integer i:
                result = "Int: " + i;
                break;
            default:
                result = "Other";
                break;
        }
        return result;
    }

    enum Day {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    String enumWithNullAndPattern(Day day) {
        return switch (day) {
            case SAT, SUN -> "weekend";
            case null -> "<unknown>";
            case MON, TUE, WED, THU, FRI -> "weekday";
            case Day d -> "not possible: " + d;
        };
    }

    String classicWithNull(Integer day) {
        return switch (day) {
            case 6, 7 -> "weekend";
            case null -> "<unknown>";
            case 1, 2, 3, 4, 5 -> "weekday";
            default -> "<not a day>";
        };
    }
}
