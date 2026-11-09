package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.core.JavaBuilder;
import live.justzhuman.scriptum.core.Program;
import live.justzhuman.scriptum.core.ProgramRunner;
import live.justzhuman.scriptum.core.Workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Compiles a program and runs its entry points one after another on a background thread.
 * All callbacks are invoked on that background thread; the UI must hop to the EDT itself.
 */
final class RunSession {

    interface Listener {
        void compiling(int files);

        void compiled(JavaBuilder.Result result);

        void started(Program.EntryPoint entry);

        void output(String text, boolean stderr);

        void exited(Program.EntryPoint entry, int code, long millis, boolean stopped);

        /** Always called last. */
        void finished(Outcome outcome);
    }

    enum Kind { SUCCESS, BUILD_FAILED, PROGRAM_FAILED, STOPPED, NOTHING_TO_RUN, ERROR }

    record Outcome(Kind kind, String detail, long millis, int programs) {
    }

    private final Program program;
    private final List<Program.EntryPoint> entries;
    private final List<String> args;
    private final Path workingDir;
    private final Listener listener;

    private volatile boolean stopped;
    private volatile Process process;
    private volatile OutputStream stdin;

    RunSession(Program program, List<Program.EntryPoint> entries, List<String> args, Path workingDir, Listener listener) {
        this.program = program;
        this.entries = entries;
        this.args = args;
        this.workingDir = workingDir;
        this.listener = listener;
    }

    void start() {
        Thread t = new Thread(this::runAll, "scriptum-run");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        stopped = true;
        ProgramRunner.kill(process);
    }

    boolean acceptsInput() {
        return stdin != null;
    }

    void sendInput(String line) {
        OutputStream out = stdin;
        if (out == null) return;
        try {
            out.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // the program has closed its input
        }
    }

    void closeInput() {
        OutputStream out = stdin;
        stdin = null;
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private void runAll() {
        long start = System.nanoTime();
        Outcome outcome;
        try (Workspace ws = Workspace.create()) {
            listener.compiling(program.units().size());
            JavaBuilder.Result build = JavaBuilder.build(program, ws);
            listener.compiled(build);
            if (!build.success()) {
                outcome = new Outcome(Kind.BUILD_FAILED, build.errorCount() + "", millis(start), 0);
            } else if (stopped) {
                outcome = new Outcome(Kind.STOPPED, "", millis(start), 0);
            } else if (entries.isEmpty()) {
                outcome = new Outcome(Kind.NOTHING_TO_RUN, "", millis(start), 0);
            } else {
                outcome = runEntries(ws, start);
            }
        } catch (IOException | RuntimeException e) {
            outcome = new Outcome(Kind.ERROR, e.getMessage() == null ? e.toString() : e.getMessage(), millis(start), 0);
        }
        listener.finished(outcome);
    }

    private Outcome runEntries(Workspace ws, long sessionStart) throws IOException {
        int ran = 0;
        for (Program.EntryPoint e : entries) {
            if (stopped) return new Outcome(Kind.STOPPED, e.simpleName(), millis(sessionStart), ran);
            long t0 = System.nanoTime();
            Process p = ProgramRunner.processFor(ws, e.className(), args, workingDir).start();
            process = p;
            stdin = p.getOutputStream();
            listener.started(e);
            Thread out = pump(p.getInputStream(), false);
            Thread err = pump(p.getErrorStream(), true);
            int code;
            try {
                code = p.waitFor();
                out.join();
                err.join();
            } catch (InterruptedException ie) {
                ProgramRunner.kill(p);
                Thread.currentThread().interrupt();
                code = -1;
            }
            stdin = null;
            process = null;
            ran++;
            long ms = (System.nanoTime() - t0) / 1_000_000;
            listener.exited(e, code, ms, stopped);
            if (stopped) return new Outcome(Kind.STOPPED, e.simpleName(), millis(sessionStart), ran);
            if (code != 0) return new Outcome(Kind.PROGRAM_FAILED, e.simpleName() + ":" + code, millis(sessionStart), ran);
        }
        return new Outcome(Kind.SUCCESS, "", millis(sessionStart), ran);
    }

    private Thread pump(InputStream in, boolean stderr) {
        Thread t = new Thread(() -> {
            char[] buf = new char[8192];
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                int n;
                while ((n = r.read(buf)) > 0) listener.output(new String(buf, 0, n), stderr);
            } catch (IOException ignored) {
                // stream closed when the process was killed
            }
        }, stderr ? "scriptum-stderr" : "scriptum-stdout");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static long millis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
