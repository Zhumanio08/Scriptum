package live.justzhuman.scriptum.cli;

import live.justzhuman.scriptum.Version;
import live.justzhuman.scriptum.core.JavaBuilder;
import live.justzhuman.scriptum.core.Problem;
import live.justzhuman.scriptum.core.Program;
import live.justzhuman.scriptum.core.ProgramRunner;
import live.justzhuman.scriptum.core.SourceUnit;
import live.justzhuman.scriptum.core.Workspace;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** The {@code scriptum} command-line interface. */
public final class Cli {

    /** Exit codes, documented in the README. */
    public static final int OK = 0;
    public static final int PROGRAM_FAILED = 1;
    public static final int USAGE = 2;
    public static final int BUILD_FAILED = 3;
    public static final int TIMEOUT = 124;

    private final Options opt;
    private final Terminal term;

    private Cli(Options opt, PrintStream status) {
        this.opt = opt;
        this.term = new Terminal(status, opt.color);
    }

    public static int run(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("scriptum: " + e.getMessage());
            System.err.println("Try 'scriptum --help' for more information.");
            return USAGE;
        }
        if (opt.help) {
            System.out.println(usage());
            return OK;
        }
        if (opt.version) {
            System.out.println(Version.NAME + " " + Version.get() + " (Java " + Runtime.version() + ")");
            return OK;
        }
        return new Cli(opt, System.err).execute();
    }

    public static String usage() {
        return """
                Scriptum %s — %s.

                Usage:
                  scriptum [options] <file.md> [-- program arguments...]
                  scriptum gui [file.md]          open the desktop app

                Extracts the ```java blocks of a Markdown file, compiles them together
                and runs every class that has a main method.

                Options:
                  -l, --list             show the blocks and entry points, don't run anything
                  -r, --run <Class>      run only this class (repeatable)
                  -o, --out <dir>        also export the generated .java files to <dir>
                  -k, --keep             keep the temporary build directory and print its path
                  -t, --timeout <sec>    stop a program that runs longer than <sec> seconds
                  -q, --quiet            print only the programs' own output
                      --no-color         disable colors (NO_COLOR is honored too)
                  -h, --help             show this help
                  -V, --version          show the version

                Use '-' as the file name to read Markdown from standard input.

                Block attributes (on the opening fence):
                  ```java class="Main"    file / class name; blocks with the same name are merged
                  ```java norun           compile, but don't launch its main
                  ```java skip            ignore the block entirely

                Exit codes: 0 ok, 1 a program failed, 2 usage error,
                            3 compilation failed, 124 timeout.
                """.formatted(Version.get(), Version.TAGLINE);
    }

    private int execute() {
        String markdown;
        String displayName;
        try {
            if (opt.file.equals("-")) {
                markdown = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                displayName = "<stdin>";
            } else {
                Path path = Path.of(opt.file);
                if (Files.isDirectory(path)) {
                    term.println(term.cross() + " " + opt.file + " is a directory, expected a Markdown file");
                    return USAGE;
                }
                markdown = Files.readString(path, StandardCharsets.UTF_8);
                displayName = path.getFileName().toString();
            }
        } catch (NoSuchFileException e) {
            term.println(term.cross() + " File not found: " + opt.file);
            return USAGE;
        } catch (IOException e) {
            term.println(term.cross() + " Cannot read " + opt.file + ": " + e.getMessage());
            return USAGE;
        }

        Program program = Program.fromMarkdown(markdown);
        if (!opt.quiet) {
            for (String w : program.warnings()) term.println(term.warn() + " " + term.yellow(w));
        }
        if (opt.list) {
            printList(program, displayName);
            return OK;
        }
        if (program.isEmpty()) {
            if (!opt.quiet) term.println(term.dim("No ```java blocks found in " + displayName + "."));
            return OK;
        }

        List<Program.EntryPoint> entries = selectEntries(program);
        if (entries == null) return USAGE;

        if (!opt.quiet) printHeader(program, displayName);

        Workspace ws;
        try {
            ws = Workspace.create();
        } catch (IOException e) {
            term.println(term.cross() + " Cannot create a temporary directory: " + e.getMessage());
            return PROGRAM_FAILED;
        }
        ws.keep(opt.keep);
        AtomicReference<Process> running = new AtomicReference<>();
        Thread cleanup = new Thread(() -> {
            ProgramRunner.kill(running.get());
            ws.close();
        }, "scriptum-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);

        try {
            if (opt.out != null) {
                JavaBuilder.writeSources(program, Path.of(opt.out));
                if (!opt.quiet) term.println(term.check() + " Exported sources to " + term.bold(Path.of(opt.out).toAbsolutePath().toString()));
            }
            JavaBuilder.Result build = JavaBuilder.build(program, ws);
            printProblems(build, displayName);
            if (!build.success()) {
                term.println(term.cross() + " " + term.red(term.bold("Build failed")) + term.dim(" — " + plural(build.errorCount(), "error")));
                return BUILD_FAILED;
            }
            if (!opt.quiet) {
                term.println(term.check() + " Compiled " + plural(program.units().size(), "file") + term.dim(" in " + build.millis() + " ms"));
            }
            if (opt.keep && !opt.quiet) term.println(term.bullet() + " Build directory: " + ws.root());

            if (entries.isEmpty()) {
                if (!opt.quiet) term.println(term.dim("Nothing to run: no class has a main method."));
                return OK;
            }
            return runAll(entries, ws, running);
        } catch (IOException e) {
            term.println(term.cross() + " " + e.getMessage());
            return PROGRAM_FAILED;
        } finally {
            ProgramRunner.kill(running.get());
            ws.close();
            try {
                Runtime.getRuntime().removeShutdownHook(cleanup);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
        }
    }

    private int runAll(List<Program.EntryPoint> entries, Workspace ws, AtomicReference<Process> running) throws IOException {
        int result = OK;
        for (Program.EntryPoint e : entries) {
            if (!opt.quiet) {
                term.println();
                term.println(term.play() + " " + term.bold(e.className()) + term.dim("  line " + e.markdownLine()));
            }
            long start = System.nanoTime();
            Process p = ProgramRunner.processFor(ws, e.className(), opt.programArgs, null).inheritIO().start();
            running.set(p);
            int exit;
            boolean timedOut = false;
            try {
                if (opt.timeoutSeconds > 0) {
                    if (!p.waitFor(opt.timeoutSeconds, TimeUnit.SECONDS)) {
                        ProgramRunner.kill(p);
                        p.waitFor();
                        timedOut = true;
                    }
                }
                exit = p.waitFor();
            } catch (InterruptedException ie) {
                ProgramRunner.kill(p);
                Thread.currentThread().interrupt();
                return PROGRAM_FAILED;
            } finally {
                running.set(null);
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (timedOut) {
                term.println(term.cross() + " " + e.simpleName() + term.red(" timed out after " + opt.timeoutSeconds + " s"));
                if (result == OK) result = TIMEOUT;
            } else if (exit != 0) {
                term.println(term.cross() + " " + e.simpleName() + term.red(" exited with code " + exit) + term.dim(" · " + ms + " ms"));
                if (result == OK) result = PROGRAM_FAILED;
            } else if (!opt.quiet) {
                term.println(term.check() + " " + e.simpleName() + term.dim(" finished in " + ms + " ms"));
            }
        }
        return result;
    }

    private List<Program.EntryPoint> selectEntries(Program program) {
        List<Program.EntryPoint> all = program.entryPoints();
        if (opt.only.isEmpty()) return all;
        List<Program.EntryPoint> out = new ArrayList<>();
        for (String want : opt.only) {
            Program.EntryPoint match = all.stream()
                    .filter(e -> e.simpleName().equals(want) || e.className().equals(want))
                    .findFirst().orElse(null);
            if (match == null) {
                term.println(term.cross() + " No runnable class named '" + want + "'.");
                term.println(term.dim("  Available: " + (all.isEmpty() ? "none" : String.join(", ",
                        all.stream().map(Program.EntryPoint::simpleName).toList()))));
                return null;
            }
            out.add(match);
        }
        return out;
    }

    private void printHeader(Program program, String displayName) {
        List<String> files = program.units().stream().map(u -> u.fileName() + ".java").toList();
        term.println(term.badge("Scriptum") + " " + term.bold(displayName) + term.dim("  "
                + plural(program.blocks().size(), "block") + " " + term.arrow() + " " + String.join(", ", files)));
    }

    private void printList(Program program, String displayName) {
        term.println(term.badge("Scriptum") + " " + term.bold(displayName));
        if (program.isEmpty()) {
            term.println(term.dim("No ```java blocks found."));
            return;
        }
        for (SourceUnit u : program.units()) {
            String lines = u.blocks().stream()
                    .map(b -> String.valueOf(b.fenceLine()))
                    .reduce((a, b) -> a + ", " + b).orElse("");
            term.println("  " + term.bold(u.relativePath().toString().replace('\\', '/'))
                    + term.dim("  " + u.kind().label().toLowerCase() + " · line " + lines));
            for (String entry : u.entryPoints()) {
                term.println("    " + term.play() + " " + u.qualify(entry));
            }
        }
        long skipped = program.blocks().stream().filter(b -> b.isSkipped()).count();
        if (skipped > 0) term.println(term.dim("  (" + plural(skipped, "block") + " skipped)"));
    }

    private void printProblems(JavaBuilder.Result build, String displayName) {
        for (Problem p : build.problems()) {
            if (!p.isError() && opt.quiet) continue;
            String label = p.isError() ? term.red(term.bold("error")) : term.yellow(term.bold("warning"));
            String where = p.markdownLine() > 0
                    ? displayName + ":" + p.markdownLine() + (p.column() > 0 ? ":" + p.column() : "")
                    : (p.javaFile().isEmpty() ? displayName : p.javaFile());
            term.println();
            term.println(label + " " + term.bold(where) + term.dim(p.javaFile().isEmpty() ? "" : "  (" + p.javaFile() + ")"));
            term.println("  " + p.message());
            if (!p.sourceLine().isEmpty() && p.markdownLine() > 0) {
                String num = String.valueOf(p.markdownLine());
                term.println(" " + term.dim(num) + " " + term.bar() + " " + p.sourceLine());
                if (p.column() > 0) {
                    String caret = " ".repeat(Math.min(p.column() - 1, p.sourceLine().length()))
                            + (p.isError() ? term.red("^") : term.yellow("^"));
                    term.println(" " + " ".repeat(num.length()) + " " + term.bar() + " " + caret);
                }
            }
        }
        if (!build.problems().isEmpty()) term.println();
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    // ------------------------------------------------------------------ options

    static final class Options {
        String file;
        boolean list;
        boolean keep;
        boolean quiet;
        boolean help;
        boolean version;
        boolean color = Terminal.colorSupported();
        String out;
        long timeoutSeconds;
        final List<String> only = new ArrayList<>();
        final List<String> programArgs = new ArrayList<>();

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.equals("--")) {
                    for (int j = i + 1; j < args.length; j++) o.programArgs.add(args[j]);
                    break;
                }
                switch (a) {
                    case "-h", "--help" -> o.help = true;
                    case "-V", "--version" -> o.version = true;
                    case "-l", "--list" -> o.list = true;
                    case "-k", "--keep" -> o.keep = true;
                    case "-q", "--quiet" -> o.quiet = true;
                    case "--no-color" -> o.color = false;
                    case "--color" -> o.color = true;
                    case "-r", "--run" -> o.only.add(value(args, ++i, a));
                    case "-o", "--out" -> o.out = value(args, ++i, a);
                    case "-t", "--timeout" -> {
                        String v = value(args, ++i, a);
                        try {
                            o.timeoutSeconds = Long.parseLong(v);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("invalid timeout '" + v + "' (expected seconds)");
                        }
                        if (o.timeoutSeconds <= 0) throw new IllegalArgumentException("timeout must be positive");
                    }
                    default -> {
                        if (a.startsWith("--run=")) o.only.add(a.substring(6));
                        else if (a.startsWith("--out=")) o.out = a.substring(6);
                        else if (a.startsWith("-") && !a.equals("-")) throw new IllegalArgumentException("unknown option '" + a + "'");
                        else if (o.file == null) o.file = a;
                        else o.programArgs.add(a);
                    }
                }
            }
            if (!o.help && !o.version && o.file == null) throw new IllegalArgumentException("missing Markdown file");
            return o;
        }

        private static String value(String[] args, int i, String option) {
            if (i >= args.length) throw new IllegalArgumentException("option '" + option + "' needs a value");
            return args[i];
        }
    }
}
