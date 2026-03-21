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

    sealed interface Geometry permits Point, Triangle, Shape{}
    record Point(int x, int y) implements Geometry {}
    record Triangle(Point a, Point b, Point c) implements Geometry {}
    record Shape(String name, Triangle triangle) implements Geometry {}

    class Builder{
        public int n;
        public Builder build(Object o){
            n++;
            return this;
        }
    }

    String caseNull(String input) {
        return switch (input.toString()) {
            case "hello" -> "hello";
            case null -> "null";
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
            default -> throw new IllegalArgumentException(obj.toString());
        };
    }

    String patternSwitchVariable(Object obj) {
        String var = "ignored part:" + switch (obj) {
            case String s -> var = "String: " + s;
            case null -> var = "null";
            case Integer i -> var = "Integer: " + i;
            default -> var = "Other";
        };
        var.toString(); // fake a use to avoid optimizations
        return var;
    }

    String patternSwitchWithGuard(Object obj) {
        return (switch (obj) {
            case String s when s.isEmpty() -> "empty";
            case String s when s.length() < 5 -> "short";
            case Integer i -> "int";
            case String s when s.length() > 3 && s.startsWith("a") -> "long-a";
            case String s -> "long";
            default -> "not a string";
        }).trim();
    }

    String patternSwitchExpressionWithGuard(Object obj) {
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

    String patternSwitchStatement(Object obj) {
        String result;
        switch (obj) {
            case String s:
                result = "String: " + s;
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

    int switchYield(Object obj) {
        return switch (obj) {
            case String s -> {
                int len = s.length();
                yield len * 2;
            }
            case Integer i -> {
                yield i + 1;
            }
            default -> 0;
        };
    }

    String nestedSwitch(Object outer, Object inner) {
        return switch (outer) {
            case String s -> switch (inner) {
                case Integer i -> "String+Int: " + s + i;
                default -> switch (inner) {
                    case Float f -> "String+Float: " + s + f;
                    default -> "String+Other";
                };
            };
            case Integer i -> "int";
            default -> "Other";
        };
    }

    int inMethod(Object obj) {
        return new Builder().build(1).build(switch (obj) {
            case null -> -1;
            case String s when s.length() > 5 -> s.length()-5;
            case String s -> s.length();
            case Integer i -> i;
            default -> obj.hashCode();
        }).build(4).n;
    }

    void switchInIf(Object obj) {
        if (switch (obj) {
                case null -> -1;
                case String s when s.length() > 5 -> s.length()-5;
                case String s -> s.length();
                case Integer i -> i;
                default -> obj.hashCode();
        } > 0) {
             System.out.println("Working");
        }
    }

    // Record patterns in instanceof
    void recordPatternInstanceof(Object obj) {
        if (obj instanceof Point(int x, int y)) {
            System.out.println(x + y);
        }
    }

    void nestedRecordPattern(Object obj) {
        if (obj instanceof Shape(var name, Triangle(var p, Point(int bx, int by), Point(int cx, int cy)))) {
            System.out.println("Shape " + name + " has triangle with vertices: " +
                "(" + p.x() + "," + p.y() + "), (" + bx + "," + by + "), (" + cx + "," + cy + ")");
        }
    }

    int recordPatternSwitchStatement(Geometry obj) {
        return switch (obj) {
            case Point(int x, int y): yield x + y;
            case Triangle(Point p, Point(int bx, int by), Point(int cx, int cy)): {
                int sum = p.x() * p.y();
                sum += bx * by;
                sum += cx * cy;
                yield sum;
            }
            case Shape s: yield 0;
            // No default case since class is sealed.
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

