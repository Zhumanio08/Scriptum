package live.justzhuman.scriptum.core;

import java.util.List;
import java.util.Map;

/**
 * One fenced {@code ```java} block found in a Markdown document.
 *
 * @param index      1-based position among the Java blocks of the document
 * @param fenceLine  1-based line number of the opening fence in the Markdown file
 * @param lines      the code lines (without the fences)
 * @param attributes attributes from the fence info string, e.g. {@code class="Main"} or {@code skip}
 * @param closed     {@code false} if the document ended before the closing fence
 */
public record CodeBlock(int index, int fenceLine, List<String> lines,
                        Map<String, String> attributes, boolean closed) {

    public CodeBlock {
        lines = List.copyOf(lines);
        attributes = Map.copyOf(attributes);
    }

    /** 1-based Markdown line of the first code line. */
    public int firstLine() {
        return fenceLine + 1;
    }

    /** 1-based Markdown line of the last code line (or of the fence for an empty block). */
    public int lastLine() {
        return fenceLine + Math.max(lines.size(), 1);
    }

    /** Explicit name from {@code class="..."} or {@code name="..."}, or {@code null}. */
    public String explicitName() {
        String name = attributes.get("class");
        if (name == null || name.isBlank()) name = attributes.get("name");
        return name == null || name.isBlank() ? null : name.trim();
    }

    /** {@code skip}: the block is documentation only and is ignored completely. */
    public boolean isSkipped() {
        return attributes.containsKey("skip") || attributes.containsKey("ignore");
    }

    /** {@code norun}: the block is compiled, but its {@code main} is not launched. */
    public boolean isNoRun() {
        return attributes.containsKey("norun") || attributes.containsKey("no-run");
    }

    public String code() {
        return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n");
    }
}
