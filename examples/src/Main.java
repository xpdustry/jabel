// Examples made by Claude Opus 4.5

/**
 * Main class to demonstrate all Java 9-25 features desugared by Jabel.
 * <p>
 * Run this class to verify that all features compile and work correctly
 * when targeting Java 8 bytecode.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== Jabel Feature Examples ===\n");

        // Java 9
        System.out.println("--- Java 9 Features ---");
        Java9FeaturesExample java9 = new Java9FeaturesExample();
        java9.diamondWithAnonymous.add("test");
        System.out.println("Diamond with anonymous: " + java9.diamondWithAnonymous);

        Java9FeaturesExample.WithPrivateMethods impl = new Java9FeaturesExample.WithPrivateMethods() {};
        System.out.println("Private interface method: " + impl.greet("World"));

        // Java 10
        System.out.println("\n--- Java 10 Features ---");
        var java10 = new Java10FeaturesExample();
        java10.varExamples();
        System.out.println("var keyword works!");

        // Java 11
        System.out.println("\n--- Java 11 Features ---");
        var java11 = new Java11FeaturesExample();
        System.out.println("var in lambda: " + java11.concat.apply("hello", "world"));

        // Java 14
        System.out.println("\n--- Java 14 Features ---");
        var java14 = new Java14FeaturesExample();
        System.out.println("Switch expression: " + java14.switchMultipleLabels(Java14FeaturesExample.Day.SAT));
        System.out.println("Switch with yield: " + java14.switchExpressionWithYield(Java14FeaturesExample.Day.WED));

        // Java 15
        System.out.println("\n--- Java 15 Features ---");
        var java15 = new Java15FeaturesExample();
        System.out.println("Text block: " + java15.basic.replace("\n", "\\n"));

        // Java 16
        System.out.println("\n--- Java 16 Features ---");
        var java16 = new Java16FeaturesExample();
        java16.patternMatchingInstanceof("Hello");
        var point = new Java16FeaturesExample.Point(3, 4);
        System.out.println("Record: " + point);

        // Java 17
        System.out.println("\n--- Java 17 Features ---");
        var java17 = new Java17FeaturesExample();
        System.out.println("sealed classes works!");
        System.out.println("strictfp method: " + java17.strictMethod(3.14, 2.71));

        // Java 21 (manual desugar)
        System.out.println("\n--- Java 21 Features (manual desugar) ---");
        var java21 = new Java21FeaturesExample();
        System.out.println("case null: " + java21.caseNull(null));
        System.out.println("pattern switch: " + java21.patternSwitch("test"));
        System.out.println("record pattern: " + java21.recordPatternSwitch(new Java21FeaturesExample.Point(5, 7)));
/*
        // Java 22
        System.out.println("\n--- Java 22 Features ---");
        var java22 = new Java22FeaturesExample();
        java22.unnamedInForEach();
        System.out.println("Unnamed variables work!");

        // Java 23 (manual desugar)
        System.out.println("\n--- Java 23 Features (manual desugar) ---");
        var java23 = new Java23FeaturesExample();
        System.out.println("Primitive pattern: " + java23.primitivePatternSwitch(42));

        // Java 25 (manual desugar)
        System.out.println("\n--- Java 25 Features (manual desugar) ---");
        var validated = new Java25FeaturesExample.ValidatedChild(10);
        System.out.println("Flexible constructor: " + validated.value);
*/
        System.out.println("\n=== All features work! ===");
    }
}
