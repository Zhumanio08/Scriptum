package live.justzhuman.scriptum.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramTest {

    @Test
    void namesFileAfterClassAndFindsMain() {
        Program p = Program.fromMarkdown("""
                ```java
                class Hello {
                    public static void main(String[] args) {}
                }
                ```
                """);
        assertEquals(1, p.units().size());
        SourceUnit u = p.units().get(0);
        assertEquals("Hello", u.fileName());
        assertEquals(SourceUnit.Kind.CLASS, u.kind());
        assertEquals(List.of("Hello"), u.entryPoints());
        assertEquals(2, p.entryPoints().get(0).markdownLine());
    }

    @Test
    void entryPointIsTheClassWithMainNotTheFirstClass() {
        Program p = Program.fromMarkdown("""
                ```java
                class Helper { static String hi() { return "hi"; } }
                class App {
                    public static void main(String[] args) { System.out.println(Helper.hi()); }
                }
                ```
                """);
        assertEquals(List.of("App"), p.units().get(0).entryPoints());
    }

    @Test
    void publicClassDecidesTheFileName() {
        Program p = Program.fromMarkdown("""
                ```java class="whatever"
                public class Real { }
                ```
                """);
        assertEquals("Real", p.units().get(0).fileName());
        assertFalse(p.warnings().isEmpty());
    }

    @Test
    void ignoresMainInCommentsAndStrings() {
        Program p = Program.fromMarkdown("""
                ```java
                class Quiet {
                    // void main(String[] a) {}
                    String s = "void main(";
                    /* class Fake { void main() {} } */
                }
                ```
                """);
        SourceUnit u = p.units().get(0);
        assertEquals("Quiet", u.fileName());
        assertTrue(u.entryPoints().isEmpty());
        assertEquals(1, u.types().size());
    }

    @Test
    void blocksWithSameNameAreMerged() {
        Program p = Program.fromMarkdown("""
                ```java class="Story"
                class Story {
                    public static void main(String[] args) {
                ```
                Some prose in between.
                ```java class="Story"
                        System.out.println("end");
                    }
                }
                ```
                """);
        assertEquals(1, p.units().size());
        SourceUnit u = p.units().get(0);
        assertEquals(2, u.blocks().size());
        assertEquals(List.of("Story"), u.entryPoints());
        assertEquals(7, u.markdownLine(3)); // 3rd generated line (println) comes from markdown line 7
    }

    @Test
    void statementsAreWrappedIntoMain() {
        Program p = Program.fromMarkdown("""
                ```java
                import java.util.List;
                var xs = List.of(1, 2, 3);
                System.out.println(xs);
                ```
                """);
        SourceUnit u = p.units().get(0);
        assertEquals(SourceUnit.Kind.SNIPPET, u.kind());
        assertEquals("Block1", u.fileName());
        assertEquals("import java.util.List;", u.lines().get(0));
        assertTrue(u.text().contains("public static void main(String[] args)"));
        assertEquals(List.of("Block1"), u.entryPoints());
        // The println line maps back to markdown line 4.
        int javaLine = u.lines().indexOf("System.out.println(xs);") + 1;
        assertEquals(4, u.markdownLine(javaLine));
    }

    @Test
    void topLevelMethodsMakeACompactSourceFile() {
        Program p = Program.fromMarkdown("""
                ```java
                void main() {
                    IO.println(greet("Ada"));
                }
                String greet(String n) { return "Hi " + n; }
                ```
                """);
        assertEquals(SourceUnit.Kind.COMPACT, p.units().get(0).kind());
    }

    @Test
    void skipAndNorunAttributes() {
        Program p = Program.fromMarkdown("""
                ```java skip
                this is not java
                ```
                ```java norun
                class Demo { public static void main(String[] a) {} }
                ```
                """);
        assertEquals(1, p.units().size());
        assertTrue(p.entryPoints().isEmpty());
    }

    @Test
    void packageGoesIntoAFolder() {
        Program p = Program.fromMarkdown("""
                ```java
                package com.acme;
                public class App { public static void main(String[] a) {} }
                ```
                """);
        SourceUnit u = p.units().get(0);
        assertEquals("com/acme/App.java", u.relativePath().toString().replace('\\', '/'));
        assertEquals("com.acme.App", p.entryPoints().get(0).className());
    }

    @Test
    void recordsEnumsAndGenericsAreRecognized() {
        Program p = Program.fromMarkdown("""
                ```java
                record Point(int x, int y) {}
                enum Color { RED, GREEN }
                interface Shape<T extends Comparable<T>> { double area(); }
                final class Main { static <T> void show(T t) {} public static void main(String... a) {} }
                ```
                """);
        SourceUnit u = p.units().get(0);
        assertEquals(List.of("Point", "Color", "Shape", "Main"), u.types().stream().map(JavaSourceScanner.TypeDecl::name).toList());
        assertEquals(List.of("Main"), u.entryPoints());
    }

    @Test
    void argumentSplitting() {
        assertEquals(List.of("a", "b c", "d\"e", "f g"), ProgramRunner.splitArgs("a \"b c\" d\\\"e 'f g'"));
        assertEquals(List.of(), ProgramRunner.splitArgs("   "));
    }
}
