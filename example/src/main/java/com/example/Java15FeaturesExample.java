// Examples made by Claude Opus 4.5

package com.example;

/**
 * Examples of Java 15 features desugared by the compiler: <br>
 *
 * <strong>TEXT_BLOCKS</strong>
 * <pre>
 * // Source (Java 15+):
 * String s = """
 *     Hello
 *     World
 *     """;
 * String cont = """
 *     Single \
 *     line""";
 *
 * // Decompiled (Java 8):
 * String s = "Hello\nWorld\n";
 * String cont = "Single line";
 * </pre>
 */
public class Java15FeaturesExample {

    String basic = """
        Hello
        World
        """;

    String json = """
        {"name": "test", "value": 42}
        """;

    String withTrailingSpace = """
        Line with space \s
        Next line
        """;

    String lineContinuation = """
        Single \
        line""";

    String formatted = String.format("""
        Name: %s
        Age: %d
        """, "Alice", 25);
}
