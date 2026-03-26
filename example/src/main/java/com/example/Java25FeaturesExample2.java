/**
 * Examples of Java 25 features with manual desugaring: <br>

 * <strong>IMPLICIT_CLASSES</strong>
 * <pre>
 * // Source (Java 25+):
 * // File: Main.java (no class declaration)
 * void main() {
 *     System.out.println("Hello");
 * }
 *
 * // Decompiled (Java 8):
 * public class Main {
 *     public static void main(String[] args) {
 *         System.out.println("Hello");
 *     }
 * }
 * </pre>
 */

int variable = 1;

static void main() {
    System.out/*IO*/.println("Implicit classes work!");
}

void main(String[] args) {
    System.out/*IO*/.println("instance main with args.");
    new Test();
}

private void privateMethod() {
    main();
}


interface TestInterface {

}

class Test implements TestInterface {
    public Test() {
        Integer.toString(variable);
    }
}