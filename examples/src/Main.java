// Examples made by Claude Opus 4.5

/** Main class to demonstrate all Java 9-25 features desugared by Jabel. */
class Main {
    public static void main(String[] args) {
        System.out.println("=== Jabel Feature Examples ===");

        System.out.println("\n--- Java 9 Features ---");
        Java9FeaturesExample java9 = new Java9FeaturesExample();
        java9.diamondWithAnonymous.add("test");
        System.out.println("Diamond with anonymous: " + java9.diamondWithAnonymous);
        Java9FeaturesExample.WithPrivateMethods impl = new Java9FeaturesExample.WithPrivateMethods() {};
        System.out.println("Private interface method: " + impl.greet("World"));

        System.out.println("\n--- Java 10 Features ---");
        Java10FeaturesExample java10 = new Java10FeaturesExample();
        java10.varExamples();
        System.out.println("var keyword works!");

        System.out.println("\n--- Java 11 Features ---");
        Java11FeaturesExample java11 = new Java11FeaturesExample();
        System.out.println("var in lambda: " + java11.concat.apply("hello", "world"));

        System.out.println("\n--- Java 14 Features ---");
        var java14 = new Java14FeaturesExample();
        System.out.println("Switch expression: " + java14.switchMultipleLabels(Java14FeaturesExample.Day.SAT));
        System.out.println("Switch with yield: " + java14.switchExpressionWithYield(Java14FeaturesExample.Day.WED));

        System.out.println("\n--- Java 15 Features ---");
        var java15 = new Java15FeaturesExample();
        System.out.println("Text block: " + java15.basic.replace("\n", "\\n"));

        System.out.println("\n--- Java 16 Features ---");
        var java16 = new Java16FeaturesExample();
        java16.patternMatchingInstanceof("Hello");
        var point = new Java16FeaturesExample.Point(3, 4);
        System.out.println("Record: " + point);

        System.out.println("\n--- Java 17 Features ---");
        var java17 = new Java17FeaturesExample();
        System.out.println("sealed classes works!");
        System.out.println("strictfp method: " + java17.strictMethod(3.14, 2.71));

        System.out.println("\n--- Java 21 Features ---");
        var java21 = new Java21FeaturesExample();
        System.out.println("case null: " + java21.caseNull(null));
        System.out.println("pattern switch: " + java21.patternSwitch("test"));
        System.out.println("record pattern: " + java21.recordPatternSwitchStatement(new Java21FeaturesExample.Point(5, 7)));
        System.out.println("switch yield: " + java21.switchYield("hello"));

        System.out.println("\n--- Java 22 Features ---");
        var java22 = new Java22FeaturesExample();
        java22.unnamedInForEach();
        System.out.println("Unnamed variables work!");

        System.out.println("\n--- Java 25 Features ---");
        // Inline: complex prologue
        var dp = new Java25FeaturesExample.ComplexPrologue(10);
        assert dp.value == 20 : "ComplexPrologue"; // 0+2+4+6+8 = 20
        System.out.println("ComplexPrologue: value=" + dp.value);
        // General: multiple shared vars in epilogue
        var ms = new Java25FeaturesExample.ManyLocals(10, 4);
        assert ms.value == 14 && "10/4".equals(ms.label) && ms.ratio == 2.5 : "ManyLocals";
        System.out.println("ManyLocals: value=" + ms.value + " label=" + ms.label + " ratio=" + ms.ratio);
        // Edge: tests constructor collisions
        var sp = new Java25FeaturesExample.SpoofChild(99);
        assert sp.value == 99 && !sp.spoofed : "SpoofChild";
        System.out.println("SpoofChild: value=" + sp.value + " spoofed=" + sp.spoofed);
        //Implicit classes
        //Java25FeaturesExample2.main(); // in theory we cannot reference the class

        System.out.println("\n=== All features work! ===");
    }
}
