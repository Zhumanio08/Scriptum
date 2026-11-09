package live.justzhuman.scriptum.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownParserTest {

    @Test
    void extractsJavaBlocksOnly() {
        String md = """
                # Title
                ```python
                print("no")
                ```
                ```java
                class A {}
                ```
                ```Java
                class B {}
                ```
                """;
        List<CodeBlock> blocks = MarkdownParser.parse(md);
        assertEquals(2, blocks.size());
        assertEquals(List.of("class A {}"), blocks.get(0).lines());
        assertEquals(5, blocks.get(0).fenceLine());
        assertEquals(6, blocks.get(0).firstLine());
        assertEquals(List.of("class B {}"), blocks.get(1).lines());
    }

    @Test
    void longerFenceProtectsNestedExamples() {
        String md = """
                ````markdown
                ```java
                class ShouldNotBeExtracted {}
                ```
                ````
                ```java
                class Real {}
                ```
                """;
        List<CodeBlock> blocks = MarkdownParser.parse(md);
        assertEquals(1, blocks.size());
        assertEquals("class Real {}", blocks.get(0).lines().get(0));
    }

    @Test
    void tildeFencesAndIndentation() {
        String md = """
                - item

                  ~~~java
                  class Indented {
                      int x;
                  }
                  ~~~
                """;
        CodeBlock b = MarkdownParser.parse(md).get(0);
        assertEquals(List.of("class Indented {", "    int x;", "}"), b.lines());
    }

    @Test
    void parsesAttributes() {
        String md = """
                ```java class="Main" norun
                ```
                ```java class='Other'
                ```
                ```java name=Third skip
                ```
                ```{.java class="Pandoc"}
                ```
                """;
        List<CodeBlock> blocks = MarkdownParser.parse(md);
        assertEquals(4, blocks.size());
        assertEquals("Main", blocks.get(0).explicitName());
        assertTrue(blocks.get(0).isNoRun());
        assertEquals("Other", blocks.get(1).explicitName());
        assertEquals("Third", blocks.get(2).explicitName());
        assertTrue(blocks.get(2).isSkipped());
        assertEquals("Pandoc", blocks.get(3).explicitName());
    }

    @Test
    void plainBlockHasNoName() {
        CodeBlock b = MarkdownParser.parse("```java\nint x;\n```\n").get(0);
        assertNull(b.explicitName());
        assertFalse(b.isSkipped());
        assertTrue(b.closed());
    }

    @Test
    void unclosedBlockRunsToEnd() {
        List<CodeBlock> blocks = MarkdownParser.parse("```java\nclass A {}\n");
        assertEquals(1, blocks.size());
        assertFalse(blocks.get(0).closed());
        assertEquals(List.of("class A {}"), blocks.get(0).lines());
    }

    @Test
    void handlesWindowsLineEndings() {
        List<CodeBlock> blocks = MarkdownParser.parse("```java\r\nclass A {}\r\n```\r\n");
        assertEquals(List.of("class A {}"), blocks.get(0).lines());
    }
}
