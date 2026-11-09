package live.justzhuman.scriptum.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds the command line that launches a compiled entry point in its own JVM. */
public final class ProgramRunner {

    private ProgramRunner() {
    }

    public static ProcessBuilder processFor(Workspace ws, String mainClass, List<String> args, Path workingDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Toolchain.java().toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(ws.classes().toString());
        cmd.add(mainClass);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDir != null) pb.directory(workingDir.toFile());
        return pb;
    }

    /** Kills a process and everything it started. */
    public static void kill(Process p) {
        if (p == null) return;
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    /**
     * Splits a command line into arguments the way a shell would (spaces separate,
     * single/double quotes group, backslash escapes).
     */
    public static List<String> splitArgs(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inArg = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else if (c == '\\' && quote == '"' && i + 1 < line.length()) cur.append(line.charAt(++i));
                else cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                inArg = true;
            } else if (c == '\\' && i + 1 < line.length()) {
                cur.append(line.charAt(++i));
                inArg = true;
            } else if (Character.isWhitespace(c)) {
                if (inArg) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    inArg = false;
                }
            } else {
                cur.append(c);
                inArg = true;
            }
        }
        if (inArg) out.add(cur.toString());
        return out;
    }
}
