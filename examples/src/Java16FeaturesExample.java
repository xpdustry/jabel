// Examples made by Claude Opus 4.5

import java.util.List;
import java.util.Objects;

/**
 * Examples of Java 16 features desugared by the compiler: <br>
 *
 * <strong>PATTERN_MATCHING_IN_INSTANCEOF</strong>
 * <pre>
 * // Source (Java 16+):
 * if (obj instanceof String s) {
 *     System.out.println(s.length());
 * }
 *
 * // Decompiled (Java 8):
 * if (obj instanceof String) {
 *     String s = (String) obj;
 *     System.out.println(s.length());
 * }
 * </pre>
 * <p>
 * <strong>RECORDS</strong>
 * <pre>
 * // Source (Java 16+):
 * record Point(int x, int y) { }
 *
 * // Decompiled (Java 8):
 * class Point {
 *     private final int x;
 *     private final int y;
 *     Point(int x, int y) { this.x = x; this.y = y; }
 *     int x() { return x; }
 *     int y() { return y; }
 *     public boolean equals(Object o) { ... }
 *     public int hashCode() { ... }
 *     public String toString() { ... }
 * }
 * </pre>
 */
public class Java16FeaturesExample {
    record Pair<A, B>(A first, B second) {}

    record Point(int x, int y) {
        public Point(int x, int y) {
            if (x > Short.MAX_VALUE) throw new IllegalArgumentException();
            this.x = x;
            if (y > Short.MAX_VALUE) throw new IllegalArgumentException();
            this.y = y;
        }
    }

    record Person(String name, int age) {
        Person {
            Objects.requireNonNull(name);
            if (age < 0) throw new IllegalArgumentException();
        }
    }

    record MultipleTypes(boolean bool, byte b, short s, int i, long l,
                         float f, double d, String str, Point p) {
      @Override
      public boolean equals(Object o) { return this == o; }
    }

    void patternMatchingInstanceof(Object obj) {
        if (obj instanceof String s) {
            System.out.println(s.length());
        }

        if (obj instanceof String s && s.length() > 3) {
            System.out.println(s.toUpperCase());
        }

        int len = obj instanceof String s ? s.length() : -1;
        Integer.valueOf(len); // use variable to get expected byte code
    }

    int inlinePatternMatching(Object obj) {
        return obj instanceof String s ? s.length() : obj instanceof Integer i ? i : -1;
    }

    void reifiableTypesInstanceof(Object obj) {
        if (!(obj instanceof List<?> list)) {
            System.out.println("Not a list");
            return;
        }
        System.out.println(list.size());
    }

    void recordsExample() {
        var p1 = new Point(3, 4);
        var p2 = new Point(3, 4);

        int x = p1.x();
        int y = p1.y();
        boolean equal = p1.equals(p2);
        int hash = p1.hashCode();
        String str = p1.toString();
    }
}
