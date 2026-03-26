// Examples made by Claude Opus 4.5

package com.example;

/**
 * Examples of Java 14 features desugared by the compiler: <br>
 *
 * <strong>SWITCH_MULTIPLE_CASE_LABELS</strong>
 * <pre>
 * // Source (Java 14+):
 * case SAT, SUN -&gt; "weekend";
 *
 * // Decompiled (Java 8):
 * case SAT:
 * case SUN:
 *     return "weekend";
 * </pre>
 * <p>
 * <strong>SWITCH_RULE (arrow syntax)</strong>
 * <pre>
 * // Source (Java 14+):
 * case MON -&gt; "monday";
 *
 * // Decompiled (Java 8):
 * case MON:
 *     return "monday";
 * </pre>
 * <p>
 * <strong>SWITCH_EXPRESSION</strong>
 * <pre>
 * // Source (Java 14+):
 * String result = switch (day) {
 *     case MON -&gt; "monday";
 *     default -&gt; { yield "other"; }
 * };
 *
 * // Decompiled (Java 8):
 * String result;
 * switch (day) {
 *     case MON:
 *         result = "monday";
 *         break;
 *     default:
 *         result = "other";
 * }
 * </pre>
 */
public class Java14FeaturesExample {

    enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

    class Builder{
        public int n;
        public Builder build(Object o){
            n++;
            return this;
        }
    }

    String switchMultipleLabels(Day day) {
        return switch (day) {
            case SAT, SUN -> "weekend";
            case MON, TUE, WED, THU, FRI -> "weekday";
        };
    }

    String switchRule(Day day) {
        return (switch (day) {
            case MON -> "monday";
            case TUE -> "tuesday";
            default -> "other";
        }).toLowerCase();
    }

    int switchExpressionWithYield(Day day) {
        return switch (day) {
            case MON -> 1;
            case TUE -> 2;
            default -> {
                int result = day.ordinal();
                yield result;
            }
        };
    }

    void statementSwitchWithArrow(Day day) {
        switch (day) {
            case SAT -> System.out.println("Saturday");
            case SUN -> System.out.println("Sunday");
            default -> System.out.println("Weekday");
        }
    }

    int[] asArrayElements(int a, int b) {
        return new int[]{
            switch (a) { case 1 -> 10; default -> 0; },
            switch (b) { case 2 -> 20; default -> 0; }
        };
    }

    String chained(String str) {
        return switch (
            switch (str) {
                case "test": {
                    str = "pok";
                    yield "A";
                }
                default: yield "B";
            }
        ) {
            case "A" -> str.isEmpty() ? "got-A" : "err";
            default  -> "got-B";
        };
    }

    int twiceOf(int n) {
        return new Builder().build(1).build(switch (n) {
            case 1  -> 10;
            case 2  -> 20;
            default -> n * n;
        }).build(4).n;
    }
}
