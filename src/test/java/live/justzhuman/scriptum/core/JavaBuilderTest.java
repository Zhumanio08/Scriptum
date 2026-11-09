package live.justzhuman.scriptum.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: Markdown → compile → run in a separate JVM. */
class JavaBuilderTest {

    private static String run(Workspace ws, String mainClass) throws Exception {
        Process p = ProgramRunner.processFor(ws, mainClass, List.of("x"), null).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        assertEquals(0, p.waitFor(), out.toString(StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void compilesAndRunsMultipleClasses() throws Exception {
        Program program = Program.fromMarkdown("""
                Helper first:
                ```java
                class Helper { static String greet(String n) { return "Hello, " + n + "!"; } }
                ```
                Then the app:
                ```java
                public class App {
                    public static void main(String[] args) {
                        System.out.println(Helper.greet("Scriptum") + " " + args.length);
                    }
                }
                ```
                """);
        try (Workspace ws = Workspace.create()) {
            JavaBuilder.Result r = JavaBuilder.build(program, ws);
            assertTrue(r.success(), r.problems().toString());
            assertEquals("Hello, Scriptum! 1", run(ws, "App"));
        }
    }

    @Test
    void snippetRuns() throws Exception {
        Program program = Program.fromMarkdown("""
                ```java
                import java.util.stream.IntStream;
                System.out.println(IntStream.rangeClosed(1, 4).sum());
                ```
                """);
        try (Workspace ws = Workspace.create()) {
            assertTrue(JavaBuilder.build(program, ws).success());
            assertEquals("10", run(ws, "Block1"));
        }
    }

    @Test
    void errorsPointAtMarkdownLines() throws IOException {
        Program program = Program.fromMarkdown("""
                # Broken

                Some text.

                ```java
                class Broken {
                    public static void main(String[] args) {
                        int x = "not a number";
                    }
                }
                ```
                """);
        try (Workspace ws = Workspace.create()) {
            JavaBuilder.Result r = JavaBuilder.build(program, ws);
            assertFalse(r.success());
            Problem p = r.problems().stream().filter(Problem::isError).findFirst().orElseThrow();
            assertEquals(8, p.markdownLine());
            assertTrue(p.column() > 0);
            assertEquals("Broken.java", p.javaFile());
            assertTrue(p.message().contains("incompatible types"), p.message());
        }
    }

    @Test
    void errorInsideSnippetMapsBack() throws IOException {
        Program program = Program.fromMarkdown("""
                ```java
                System.out.println("ok");
                undefinedCall();
                ```
                """);
        try (Workspace ws = Workspace.create()) {
            JavaBuilder.Result r = JavaBuilder.build(program, ws);
            assertFalse(r.success());
            assertEquals(3, r.problems().get(0).markdownLine());
        }
    }
}
