package live.justzhuman.scriptum.core;

/**
 * A compiler error or warning, already translated to a position in the Markdown file.
 *
 * @param markdownLine 1-based line in the Markdown file, or 0 if unknown
 * @param column       1-based column in that line, or 0 if unknown
 * @param javaFile     generated file the compiler complained about (e.g. {@code Hello.java}), may be empty
 * @param sourceLine   the offending line of code, may be empty
 */
public record Problem(Severity severity, String message, int markdownLine, int column,
                      String javaFile, String sourceLine) {

    public enum Severity { ERROR, WARNING }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
