// Examples made by Claude Opus 4.5

/**
 * Examples of Java 21 features with manual desugaring: <br>
 *
 * <strong>CASE_NULL</strong>
 * <pre>
 * // Source (Java 21+):
 * switch (str) {
 *     case null -&gt; "null";
 *     case "a" -&gt; "A";
 *     default -&gt; "other";
 * }
 *
 * // Decompiled (Java 8):
 * if (str == null) {
 *     return "null";
 * }
 * switch (str) {
 *     case "a": return "A";
 *     default: return "other";
 * }
 * </pre>
 * <p>
 * <strong>PATTERN_SWITCH</strong>
 * <pre>
 * // Source (Java 21+):
 * switch (obj) {
 *     case String s -&gt; s.length();
 *     case Integer i -&gt; i;
 *     default -&gt; 0;
 * }
 *
 * // Decompiled (Java 8):
 * if (obj instanceof String) {
 *     String s = (String) obj;
 *     return s.length();
 * } else if (obj instanceof Integer) {
 *     Integer i = (Integer) obj;
 *     return i;
 * } else {
 *     return 0;
 * }
 * </pre>
 * <p>
 * <strong>RECORD_PATTERNS</strong>
 * <pre>
 * // Source (Java 21+):
 * if (obj instanceof Point(int x, int y)) {
 *     return x + y;
 * }
 *
 * // Decompiled (Java 8):
 * if (obj instanceof Point) {
 *     Point p = (Point) obj;
 *     int x = p.x();
 *     int y = p.y();
 *     return x + y;
 * }
 * </pre>
 * <p>
 * <strong>UNCONDITIONAL_PATTERN_IN_INSTANCEOF</strong>
 * <pre>
 * // Source (Java 21+):
 * if (str instanceof CharSequence cs) { }  // always true for non-null
 *
 * // Decompiled (Java 8):
 * if (str != null) {
 *     CharSequence cs = str;
 * }
 * </pre>
 */
public class Java21FeaturesExample {

    record Point(int x, int y) {}
    record Line(Point start, Point end) {}

    String caseNull(String input) {
        return switch (input) {
            case null -> "null";
            case "hello" -> "hello";
            default -> "other";
        };
    }

    String caseNullWithDefault(String input) {
        return switch (input) {
            case "specific" -> "specific";
            case null, default -> "null or default";
        };
    }

    String patternSwitch(Object obj) {
        return switch (obj) {
            case String s -> "String: " + s;
            case Integer i -> "Integer: " + i;
            default -> "Other";
        };
    }

    String patternSwitchStatement(Object obj) {
        String var;
        switch (obj) {
            case null -> var = "null";
            case String s -> var = "String: " + s;
            case Integer i -> var = "Integer: " + i;
            default -> var = "Other";
        }
        var.toString(); // fake a use to avoid optimizations
        return var;
    }

    String patternSwitchWithGuard(Object obj) {
        return switch (obj) {
            case String s when s.isEmpty() -> "empty";
            case String s when s.length() < 5 -> "short";
            case Integer i -> "int";
            case String s -> "long";
            default -> "not a string";
        };
    }

    String patternSwitchWithGuardStatement(Object obj) {
        String var;
        switch (obj) {
            case String s when s.isEmpty() -> var = "empty";
            case String s when s.length() < 5 -> var = "short";
            case Integer i -> var = "int";
            case String s -> var = "long";
            default -> var = "not a string";
        }
        var.toString(); // fake a use to avoid optimizations
        return var;
    }

    // Record patterns in instanceof
    void recordPatternInstanceof(Object obj) {
        if (obj instanceof Point(int x, int y)) {
            System.out.println(x + y);
        }
    }

    void nestedRecordPattern(Object obj) {
        if (obj instanceof Line(Point(int x1, int y1), Point(int x2, int y2))) {
            System.out.println("Line from " + x1 + "," + y1 + " to " + x2 + "," + y2);
        }
    }

    // Deeply nested record pattern (3 levels)
    record Triangle(Point a, Point b, Point c) {}
    record Shape(String name, Triangle triangle) {}

    void deeplyNestedRecordPattern(Object obj) {
        if (obj instanceof Shape(String name, Triangle(Point(int ax, int ay), Point(int bx, int by), Point(int cx, int cy)))) {
            System.out.println("Shape " + name + " has triangle with vertices: " +
                "(" + ax + "," + ay + "), (" + bx + "," + by + "), (" + cx + "," + cy + ")");
        }
    }

    int recordPatternSwitch(Object obj) {
        return switch (obj) {
            case Point(int x, int y) -> x + y;
            default -> 0;
        };
    }

    void unconditionalPattern(String str) {
        // Unconditional pattern - String is always a CharSequence (for non-null)
        if (str instanceof CharSequence cs) {
            System.out.println(cs.length());
        }
    }

    // Unconditional with inferred type
    <T> void unconditionalPatternObject(T str) {
        if (str instanceof Shape o) {
            System.out.println(o.hashCode());
        }
    }
}

