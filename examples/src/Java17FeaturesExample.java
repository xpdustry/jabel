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
 * // Note: PermittedSubclasses bytecode attribute must be removed
 * </pre>
 * <p>
 * <strong>REDUNDANT_STRICTFP</strong>
 * <pre>
 * // Source (Java 17+):
 * public strictfp class Math { }
 *
 * // Decompiled (Java 8): same code (strictfp kept but redundant since Java 17)
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
    }

    final class Square extends Shape {
        Square() { super("Square"); }
    }

    non-sealed class Rectangle extends Shape {
        Rectangle() { super("Rectangle"); }
    }

    class SpecialRectangle extends Rectangle {}

    strictfp double strictMethod(double a, double b) {
        return a * b + a / b;
    }
}
