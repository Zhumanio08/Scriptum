package live.justzhuman.scriptum.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Scriptum extracted from a Markdown document: the generated source files and
 * the classes it will launch.
 */
public final class Program {

    /** A class with a {@code main} method that will be launched. */
    public record EntryPoint(String className, String simpleName, SourceUnit unit, int markdownLine) {
    }

    private final List<CodeBlock> blocks;
    private final List<SourceUnit> units;
    private final List<String> warnings;

    private Program(List<CodeBlock> blocks, List<SourceUnit> units, List<String> warnings) {
        this.blocks = List.copyOf(blocks);
        this.units = List.copyOf(units);
        this.warnings = List.copyOf(warnings);
    }

    public static Program fromMarkdown(String markdown) {
        return assemble(MarkdownParser.parse(markdown));
    }

    public List<CodeBlock> blocks() {
        return blocks;
    }

    public List<SourceUnit> units() {
        return units;
    }

    public List<String> warnings() {
        return warnings;
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    public List<EntryPoint> entryPoints() {
        List<EntryPoint> out = new ArrayList<>();
        for (SourceUnit u : units) {
            for (String name : u.entryPoints()) {
                // Snippets have no class declaration in the Markdown: point at their opening fence.
                int line = u.kind() == SourceUnit.Kind.CLASS ? u.markdownLineOf(name) : u.blocks().get(0).fenceLine();
                out.add(new EntryPoint(u.qualify(name), name, u, line));
            }
        }
        return out;
    }

    /** Finds the unit whose generated file has this name ({@code Hello.java} or {@code Hello}). */
    public SourceUnit unitByFile(String fileName) {
        String base = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        for (SourceUnit u : units) if (u.fileName().equals(base)) return u;
        return null;
    }

    // ------------------------------------------------------------------ assembly

    static Program assemble(List<CodeBlock> blocks) {
        List<String> warnings = new ArrayList<>();
        Map<String, List<CodeBlock>> groups = new LinkedHashMap<>();
        int auto = 0;

        for (CodeBlock b : blocks) {
            if (!b.closed()) {
                warnings.add("Block at line " + b.fenceLine() + " is never closed; it runs to the end of the file.");
            }
            if (b.isSkipped()) continue;
            if (b.lines().stream().allMatch(String::isBlank)) {
                warnings.add("Block at line " + b.fenceLine() + " is empty and was ignored.");
                continue;
            }
            String key = b.explicitName();
            if (key == null) {
                JavaSourceScanner.TypeDecl primary = JavaSourceScanner.analyze(b.lines()).primaryType();
                if (primary != null) key = primary.name();
            }
            if (key == null) key = "Block" + (++auto);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }

        List<SourceUnit> units = new ArrayList<>();
        for (Map.Entry<String, List<CodeBlock>> e : groups.entrySet()) {
            units.add(buildUnit(e.getKey(), e.getValue(), warnings));
        }
        return new Program(blocks, units, warnings);
    }

    private static SourceUnit buildUnit(String key, List<CodeBlock> group, List<String> warnings) {
        List<String> lines = new ArrayList<>();
        List<Integer> md = new ArrayList<>();
        for (CodeBlock b : group) {
            for (int i = 0; i < b.lines().size(); i++) {
                lines.add(b.lines().get(i));
                md.add(b.firstLine() + i);
            }
        }
        boolean noRun = group.stream().anyMatch(CodeBlock::isNoRun);
        JavaSourceScanner.Analysis a = JavaSourceScanner.analyze(lines);
        String name = JavaSourceScanner.toIdentifier(key);

        if (!a.isSnippet()) {
            JavaSourceScanner.TypeDecl pub = a.types().stream().filter(JavaSourceScanner.TypeDecl::isPublic).findFirst().orElse(null);
            if (pub != null && !pub.name().equals(name)) {
                if (!key.equals(pub.name())) {
                    warnings.add("Block named \"" + key + "\" declares public " + pub.kind() + " " + pub.name()
                            + "; the file is saved as " + pub.name() + ".java.");
                }
                name = pub.name();
            }
            List<String> entries = new ArrayList<>();
            if (!noRun) {
                for (JavaSourceScanner.TypeDecl t : a.types()) if (t.hasMain()) entries.add(t.name());
            }
            return new SourceUnit(name, a.packageName(), SourceUnit.Kind.CLASS, lines, toArray(md), group,
                    entries, a.types());
        }

        if (a.topLevelMethods()) {
            // Java 25+ compact source file: javac turns it into an implicit class named after the file.
            List<String> entries = noRun ? List.of() : List.of(name);
            return new SourceUnit(name, "", SourceUnit.Kind.COMPACT, lines, toArray(md), group, entries,
                    List.of(new JavaSourceScanner.TypeDecl(name, "class", false, true, 0)));
        }

        // Plain statements: wrap them into a class with a main method.
        List<String> out = new ArrayList<>();
        List<Integer> outMd = new ArrayList<>();
        for (int idx : a.importLines()) {
            out.add(lines.get(idx));
            outMd.add(md.get(idx));
        }
        int headerLine = out.size();
        out.add("public class " + name + " {");
        outMd.add(0);
        out.add("    public static void main(String[] args) throws Exception {");
        outMd.add(0);
        for (int i = 0; i < lines.size(); i++) {
            if (a.importLines().contains(i)) continue;
            out.add(lines.get(i));
            outMd.add(md.get(i));
        }
        out.add("    }");
        outMd.add(0);
        out.add("}");
        outMd.add(0);
        // Generated header lines point at the first code line of the block.
        for (int i = headerLine; i < headerLine + 2; i++) outMd.set(i, group.get(0).firstLine());
        List<String> entries = noRun ? List.of() : List.of(name);
        return new SourceUnit(name, "", SourceUnit.Kind.SNIPPET, out, toArray(outMd), group, entries,
                List.of(new JavaSourceScanner.TypeDecl(name, "class", true, true, headerLine)));
    }

    private static int[] toArray(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
