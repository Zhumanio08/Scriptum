package live.justzhuman.scriptum.cli;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Minimal styled output for the terminal: ANSI colors and Unicode symbols with ASCII fallbacks. */
final class Terminal {

    private final PrintStream out;
    private final boolean color;
    private final boolean unicode;

    Terminal(PrintStream out, boolean color) {
        this.out = out;
        this.color = color;
        this.unicode = supportsUnicode();
    }

    static boolean colorSupported() {
        if (System.getenv("NO_COLOR") != null) return false;
        if (System.getenv("FORCE_COLOR") != null) return true;
        String term = System.getenv("TERM");
        if ("dumb".equals(term)) return false;
        return isInteractive();
    }

    /** True if stdout is attached to a terminal (works on Java 17 and 22+). */
    static boolean isInteractive() {
        java.io.Console c = System.console();
        if (c == null) return false;
        try {
            // Java 22+: System.console() is non-null even when redirected; ask explicitly.
            return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(c);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private static boolean supportsUnicode() {
        String enc = System.getProperty("stderr.encoding", System.getProperty("stdout.encoding"));
        Charset cs;
        try {
            cs = enc != null ? Charset.forName(enc) : Charset.defaultCharset();
        } catch (RuntimeException e) {
            cs = Charset.defaultCharset();
        }
        return cs.equals(StandardCharsets.UTF_8);
    }

    String bold(String s) {
        return style("1", s);
    }

    String dim(String s) {
        return style("2", s);
    }

    String red(String s) {
        return style("31", s);
    }

    String green(String s) {
        return style("32", s);
    }

    String yellow(String s) {
        return style("33", s);
    }

    String accent(String s) {
        return style("38;5;105", s);
    }

    String badge(String s) {
        return color ? "\u001b[1;97;48;5;62m " + s + " \u001b[0m" : "[" + s + "]";
    }

    String check() {
        return green(unicode ? "✓" : "+");
    }

    String cross() {
        return red(unicode ? "✗" : "x");
    }

    String warn() {
        return yellow(unicode ? "⚠" : "!");
    }

    String play() {
        return accent(unicode ? "▶" : ">");
    }

    String bullet() {
        return dim(unicode ? "•" : "*");
    }

    String arrow() {
        return unicode ? "→" : "->";
    }

    String bar() {
        return dim(unicode ? "│" : "|");
    }

    void println(String s) {
        out.println(s);
        out.flush();
    }

    void println() {
        out.println();
        out.flush();
    }

    private String style(String code, String s) {
        return color ? "\u001b[" + code + "m" + s + "\u001b[0m" : s;
    }
}
