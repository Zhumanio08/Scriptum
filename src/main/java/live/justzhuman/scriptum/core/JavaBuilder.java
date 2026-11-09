package live.justzhuman.scriptum.core;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a {@link Program} to disk and compiles it.
 *
 * <p>Uses the in-process compiler ({@code javax.tools}) when available and falls back to an
 * external {@code javac} otherwise, so Scriptum also works from a JRE-only runtime as long as
 * a JDK is installed somewhere.
 */
public final class JavaBuilder {

    /** Result of a build. {@code problems} contains errors and warnings in Markdown coordinates. */
    public record Result(boolean success, List<Problem> problems, long millis) {

        public long errorCount() {
            return problems.stream().filter(Problem::isError).count();
        }

        public long warningCount() {
            return problems.size() - errorCount();
        }
    }

    private static final List<String> OPTIONS = List.of("-encoding", "UTF-8", "-proc:none", "-g", "-Xlint:-options");

    private JavaBuilder() {
    }

    /** Writes every unit of the program below {@code sourceRoot}. Returns the written files. */
    public static List<Path> writeSources(Program program, Path sourceRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        for (SourceUnit u : program.units()) {
            Path file = sourceRoot.resolve(u.relativePath());
            Files.createDirectories(file.getParent());
            Files.writeString(file, u.text(), StandardCharsets.UTF_8);
            files.add(file);
        }
        return files;
    }

    public static Result build(Program program, Workspace ws) throws IOException {
        long start = System.nanoTime();
        List<Path> files = writeSources(program, ws.sources());
        Files.createDirectories(ws.classes());

        Map<Path, SourceUnit> byPath = new HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            byPath.put(files.get(i).toAbsolutePath().normalize(), program.units().get(i));
        }

        List<String> options = new ArrayList<>(OPTIONS);
        options.add("-d");
        options.add(ws.classes().toString());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<Problem> problems;
        boolean ok;
        if (compiler != null) {
            problems = new ArrayList<>();
            ok = compileInProcess(compiler, files, options, byPath, problems);
        } else {
            problems = new ArrayList<>();
            ok = compileExternal(files, options, byPath, problems);
        }
        return new Result(ok, List.copyOf(problems), (System.nanoTime() - start) / 1_000_000);
    }

    private static boolean compileInProcess(JavaCompiler compiler, List<Path> files, List<String> options,
                                            Map<Path, SourceUnit> byPath, List<Problem> problems) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(files);
            StringWriter out = new StringWriter();
            boolean ok = compiler.getTask(out, fm, diagnostics, options, null, units).call();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                Problem.Severity severity = switch (d.getKind()) {
                    case ERROR -> Problem.Severity.ERROR;
                    case WARNING, MANDATORY_WARNING -> Problem.Severity.WARNING;
                    default -> null;
                };
                if (severity == null) continue;
                SourceUnit unit = null;
                if (d.getSource() != null) {
                    unit = byPath.get(Path.of(d.getSource().toUri()).toAbsolutePath().normalize());
                }
                int javaLine = (int) Math.max(0, d.getLineNumber());
                int col = (int) Math.max(0, d.getColumnNumber());
                problems.add(toProblem(severity, d.getMessage(Locale.ENGLISH), unit, javaLine, col));
            }
            if (!ok && problems.stream().noneMatch(Problem::isError)) {
                problems.add(new Problem(Problem.Severity.ERROR, out.toString().trim(), 0, 0, "", ""));
            }
            return ok;
        }
    }

    private static final Pattern JAVAC_LINE = Pattern.compile("^(.+\\.java):(\\d+): (error|warning): (.*)$");

    private static boolean compileExternal(List<Path> files, List<String> options, Map<Path, SourceUnit> byPath,
                                           List<Problem> problems) throws IOException {
        Path javac = Toolchain.javac();
        if (javac == null) {
            throw new IOException("No Java compiler found. Scriptum needs a JDK (17 or newer), not just a JRE. "
                    + "Install one, or point JAVA_HOME to it.");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(javac.toString());
        cmd.add("-J-Duser.language=en");
        cmd.addAll(options);
        for (Path f : files) cmd.add(f.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output;
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            output = buf.toString(StandardCharsets.UTF_8);
        }
        int exit;
        try {
            exit = p.waitFor();
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Compilation interrupted", e);
        }
        String[] lines = output.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = JAVAC_LINE.matcher(lines[i]);
            if (!m.matches()) continue;
            SourceUnit unit = byPath.get(Path.of(m.group(1)).toAbsolutePath().normalize());
            int javaLine = Integer.parseInt(m.group(2));
            int col = 0;
            // javac prints the source line and then a caret line.
            if (i + 2 < lines.length && lines[i + 2].trim().equals("^")) col = lines[i + 2].indexOf('^') + 1;
            Problem.Severity sev = m.group(3).equals("error") ? Problem.Severity.ERROR : Problem.Severity.WARNING;
            problems.add(toProblem(sev, m.group(4), unit, javaLine, col));
        }
        if (exit != 0 && problems.stream().noneMatch(Problem::isError)) {
            problems.add(new Problem(Problem.Severity.ERROR, output.trim(), 0, 0, "", ""));
        }
        return exit == 0;
    }

    private static Problem toProblem(Problem.Severity severity, String message, SourceUnit unit, int javaLine, int col) {
        String msg = message == null ? "" : message.strip();
        // javac sometimes appends extra lines (symbol/location); keep them, but trim each.
        msg = msg.replaceAll("\\s*\\R\\s*", "\n  ");
        if (unit == null) return new Problem(severity, msg, 0, 0, "", "");
        String src = javaLine >= 1 && javaLine <= unit.lines().size() ? unit.lines().get(javaLine - 1) : "";
        int mdLine = unit.markdownLine(javaLine);
        // Columns are only meaningful when the line is copied verbatim from Markdown.
        boolean generated = javaLine < 1 || javaLine > unit.markdownLines().length || unit.markdownLines()[javaLine - 1] == 0;
        return new Problem(severity, msg, mdLine, generated ? 0 : col, unit.fileName() + ".java", src);
    }
}
