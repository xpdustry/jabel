// Examples made by Claude Opus 4.6

package com.example;

/**
 * Examples of Java 25 flexible constructors with manual desugaring: <br>
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
 *         super($prologue$0(value));
 *     }
 *     private static int $prologue$0(int value) {
 *         if (value &lt; 0) throw new IllegalArgumentException();
 *         return value * 2;
 *     }
 * }
 * </pre>
 */
public class Java25FeaturesExample {
    String str;

    // A warning will appear for signature duplication
    void main(String[] args) {
        str = "test";
    }

    void main() {
        System.out.println("Bridged entry point");
    }

    static class Parent {
        final int value;
        Parent(int value) { this.value = value; }
    }

    // Complex loop in prologue
    static class ComplexPrologue extends Parent {
        ComplexPrologue(int n) {
            if (n < 0) throw new IllegalArgumentException();
            int sum = 0;
            for (int i = 0; i < n; i++) {
                if (i % 2 == 0) {
                    sum += i;
                }
            }
            super(sum > 100 ? 100 : sum);
        }
    }

    // Epilogue reuses multiple prologue variables
    static class ManyLocals extends Parent {
        final String label;
        final double ratio;
        ManyLocals(int a, int b) {
            String lbl = a + "/" + b;
            double r = b != 0 ? (double) a / b : 0.0;
            int sum = a + b;
            super(sum);
            this.label = lbl;
            this.ratio = r;
        }
    }

    // Existing Object[] / (Object[], Void) constructors must not clash
    static class SpoofChild extends Parent {
        boolean spoofed;

        SpoofChild(int value) {
            super(value);
        }

        // Spoof: (Object[])
        SpoofChild(Object[] args) {
            boolean s = false;
            if (args.length > 0) s = true;
            this(0);
            spoofed = s;
        }

        // Spoof: (Object[], Void)
        SpoofChild(Object[] args, Void v) {
            this((int)(Integer) args[0]);
            this.spoofed = true;
        }

        // Another flex in the same spoofed class
        SpoofChild(String s) {
            int parsed = Integer.parseInt(s);
            super(parsed);
        }
    }
}