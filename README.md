# Jabel - Use modern Java 9-25 syntax when targeting Java 8

> Because life is too short to wait for your users to upgrade their Java!


## Motivation
While Java is evolving and introduces new language features, the majority of OSS libraries
are still using Java 8 as their target because it still dominates.

But, since most of features after Java 8 did not require a change in the bytecode,
`javac` could emit Java 8 bytecode even when compiling Java 12 sources.


## How Jabel works
Although Jabel is a javac compiler plugin, it does not run any processing,
but instruments the java compiler classes and makes it treat some new Java 9+ languages features
as they were supported in Java 8.

The result is a valid Java 8 bytecode for your switch expressions, `var` declarations,
and other features unavailable in Java 8.


## Why it works
The JVM has evolved a lot for the past years. However, most language features
that were added are simply a syntatic sugar. <br>
They do not require new bytecode, hence can be compiled to the Java 8.

But, since the Java language was always bound to the JVM development, new language features
require the same target as the JVM because they get released altogether.  

As was previously described, Jabel makes the compiler think that certain features were developed
for Java 8, and removes the checks that otherwise will report them as invalid for the target.

It is important to understand that it will use the same desugaring code as for Java 9+ but won't change
the result's classfile version, because the compilation phase will be done with Java 8 target.


## How to use
### Gradle 6 or older
Use the following snippet to add Jabel to your Gradle build:
```gradle
repositories {
    maven { url 'https://maven.xpdustry.com/mindustry' }
}

dependencies {
    annotationProcessor 'com.xpdustry:jabel:1.1.0'
}

// Add more tasks if needed, such as compileTestJava
compileJava {
    sourceCompatibility = 14 // for the IDE support

    options.compilerArgs = [
        "--release", "8",
    ]

    doFirst {
        // Can be omitted on Java 14 and higher
        options.compilerArgs << '-Xplugin:jabel'
        
        // Needed to get access to internal compiler classes
        options.fork = true
        ["api", "code", "comp", "tree", "util"].each { 
            options.forkOptions.jvmArgs << "--add-opens=jdk.compiler/com.sun.tools.javac." + it + "=ALL-UNNAMED" 
        }
    }
}
```

Compile your project and verify that the result is still a valid Java 8 bytecode (52.0):
```shell script
$ ./gradlew --no-daemon clean :example:run

> Task :examples:run
=== Jabel Feature Examples ===

--- Java 9 Features ---
Diamond with anonymous: [TEST]
Private interface method: Hello World!

--- Java 10 Features ---
item
item0
item1
item2
var keyword works!

--- Java 11 Features ---
var in lambda: helloworld

--- Java 14 Features ---
Switch expression: weekend
Switch with yield: 2

--- Java 15 Features ---
Text block: Hello\nWorld\n

--- Java 16 Features ---
5
HELLO
Record: Point[x=3,y=4]

--- Java 17 Features ---
sealed classes works!
strictfp method: 9.668071586715866

--- Java 21 Features ---
case null: null
pattern switch: String: test
record pattern: 12
switch yield: 10

--- Java 22 Features ---
Unnamed variables work!

--- Java 25 Features ---
ComplexPrologue: value=20
ManyLocals: value=14 label=10/4 ratio=2.5
SpoofChild: value=99 spoofed=false

=== All features work! ===

BUILD SUCCESSFUL in 32s
7 actionable tasks: 7 executed
```

### Gradle 7 and newer
Gradle 7 supports toolchains and makes it extremely easy to configure everything:
```gradle
repositories {
    maven { url 'https://maven.xpdustry.com/mindustry' }
}

dependencies {
    annotationProcessor 'com.xpdustry:jabel:1.1.0'
}

compileJava {
    sourceCompatibility = 16 // for the IDE support
    options.release = 8

    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(16)
    }
    
    // Can be omitted on Java 14 and higher
    options.compilerArgs += "-Xplugin:jabel"
    
    // Needed to get access to internal compiler classes
    options.fork = true
    ["api", "code", "comp", "tree", "util"].each { 
        options.forkOptions.jvmArgs += "--add-opens=jdk.compiler/com.sun.tools.javac." + it + "=ALL-UNNAMED" 
    }
}
```

You can also force your tests to run with Java 8:
```gradle
compileTestJava {
    sourceCompatibility = targetCompatibility = 8
}

test {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
```
