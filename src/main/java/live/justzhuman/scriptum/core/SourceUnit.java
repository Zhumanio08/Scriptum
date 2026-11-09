package live.justzhuman.scriptum.core;

import java.nio.file.Path;
import java.util.List;

/**
 * One generated {@code .java} file, assembled from one or more Markdown blocks.
 *
 * @param fileName     simple file name without {@code .java}
 * @param packageName  declared package, or {@code ""}
 * @param kind         how the file was produced
 * @param lines        generated source lines
 * @param markdownLines for every generated line, the 1-based Markdown line it came from
 *                     ({@code 0} for lines Scriptum generated itself)
 * @param blocks       the Markdown blocks merged into this file
 * @param entryPoints  simple names of the types to launch
 */
public record SourceUnit(String fileName, String packageName, Kind kind, List<String> lines,
                         int[] markdownLines, List<CodeBlock> blocks, List<String> entryPoints,
                         List<JavaSourceScanner.TypeDecl> types) {

    public enum Kind {
        /** Ordinary Java source with class/record/enum/interface declarations. */
        CLASS("Class"),
        /** Bare statements that Scriptum wrapped into a {@code main} method. */
        SNIPPET("Snippet"),
        /** A Java 25 compact source file: top-level methods such as {@code void main()}. */
        COMPACT("Compact source");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public SourceUnit {
        lines = List.copyOf(lines);
        markdownLines = markdownLines.clone();
        blocks = List.copyOf(blocks);
        entryPoints = List.copyOf(entryPoints);
        types = List.copyOf(types);
    }

    public String text() {
        return String.join("\n", lines) + "\n";
    }

    /** Path of this file relative to a source root, e.g. {@code com/acme/App.java}. */
    public Path relativePath() {
        Path p = Path.of(fileName + ".java");
        if (packageName.isEmpty()) return p;
        return Path.of(packageName.replace('.', '/')).resolve(p);
    }

    public String qualify(String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    /** Maps a 1-based line of the generated file back to the Markdown file (0 if unknown). */
    public int markdownLine(int javaLine) {
        if (javaLine < 1 || markdownLines.length == 0) return blocks.isEmpty() ? 0 : blocks.get(0).fenceLine();
        int idx = Math.min(javaLine, markdownLines.length) - 1;
        if (markdownLines[idx] > 0) return markdownLines[idx];
        // Generated line: point at the nearest real line above, or the first fence.
        for (int k = idx; k >= 0; k--) if (markdownLines[k] > 0) return markdownLines[k];
        return blocks.isEmpty() ? 0 : blocks.get(0).fenceLine();
    }

    /** Markdown line where the given type is declared (or the first fence). */
    public int markdownLineOf(String typeName) {
        for (JavaSourceScanner.TypeDecl t : types) {
            if (t.name().equals(typeName)) return markdownLine(t.line() + 1);
        }
        return blocks.isEmpty() ? 0 : blocks.get(0).fenceLine();
    }
}
