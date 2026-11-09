package live.justzhuman.scriptum.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny, forgiving structural scanner for Java source.
 *
 * <p>It is not a parser: it masks comments and literals, tracks brace depth and looks at
 * tokens at the top level. That is enough to learn which types a block declares, which of
 * them have a {@code main} method, the package, and whether the block is a plain snippet of
 * statements. Broken code never throws — the compiler reports real errors later.
 */
public final class JavaSourceScanner {

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> NOT_METHOD_NAMES = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "return", "new", "throw",
            "else", "do", "case", "assert", "yield", "super", "this");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private JavaSourceScanner() {
    }

    /** A top-level type declaration. {@code line} is a 0-based index into the scanned lines. */
    public record TypeDecl(String name, String kind, boolean isPublic, boolean hasMain, int line) {
    }

    /**
     * @param types              top-level type declarations, in source order
     * @param packageName        declared package or {@code ""}
     * @param topLevelMethods    the code declares methods outside any type (a Java 25 compact source file)
     * @param importLines        0-based indexes of lines that hold {@code import} statements
     */
    public record Analysis(List<TypeDecl> types, String packageName, boolean topLevelMethods,
                           List<Integer> importLines) {

        public boolean isSnippet() {
            return types.isEmpty();
        }

        public TypeDecl primaryType() {
            for (TypeDecl t : types) if (t.isPublic()) return t;
            return types.isEmpty() ? null : types.get(0);
        }
    }

    public static Analysis analyze(List<String> lines) {
        String source = String.join("\n", lines);
        String masked = mask(source);
        int[] lineStarts = lineStarts(masked);
        List<Token> tokens = tokenize(masked);

        List<TypeDecl> types = new ArrayList<>();
        boolean topLevelMethods = false;

        int depth = 0;
        String pendingName = null;
        String pendingKind = null;
        boolean pendingPublic = false;
        int pendingLine = 0;
        boolean pendingMain = false;
        boolean insideType = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String prev = i > 0 ? tokens.get(i - 1).text : "";
            String next = i + 1 < tokens.size() ? tokens.get(i + 1).text : "";

            if (t.text.equals("{")) {
                if (depth == 0 && pendingName != null) insideType = true;
                depth++;
                continue;
            }
            if (t.text.equals("}")) {
                depth = Math.max(0, depth - 1);
                if (depth == 0 && insideType) {
                    types.add(new TypeDecl(pendingName, pendingKind, pendingPublic, pendingMain, pendingLine));
                    pendingName = null;
                    insideType = false;
                }
                continue;
            }

            if (depth == 0 && pendingName == null && TYPE_KEYWORDS.contains(t.text)
                    && isIdentifier(next) && !prev.equals(".")) {
                pendingName = next;
                pendingKind = prev.equals("@") ? "annotation" : t.text;
                pendingPublic = hasPublicModifier(tokens, i);
                pendingLine = lineOf(lineStarts, t.offset);
                pendingMain = false;
                i++;
                continue;
            }

            if (depth == 1 && insideType && t.text.equals("main") && prev.equals("void") && next.equals("(")) {
                pendingMain = true;
                continue;
            }

            if (depth == 0 && pendingName == null && !topLevelMethods && isIdentifier(t.text) && next.equals("(")
                    && !NOT_METHOD_NAMES.contains(t.text) && looksLikeReturnType(prev)) {
                int close = matchingParen(tokens, i + 1);
                if (close > 0 && close + 1 < tokens.size()) {
                    String after = tokens.get(close + 1).text;
                    if (after.equals("{") || after.equals("throws")) topLevelMethods = true;
                }
            }
        }
        // A type left open at the end of the block (e.g. a class split across blocks).
        if (pendingName != null) {
            types.add(new TypeDecl(pendingName, pendingKind, pendingPublic, pendingMain, pendingLine));
        }

        String pkg = "";
        Matcher m = PACKAGE.matcher(masked);
        if (m.find()) pkg = m.group(1);

        List<Integer> imports = new ArrayList<>();
        String[] maskedLines = masked.split("\n", -1);
        for (int k = 0; k < maskedLines.length; k++) {
            String s = maskedLines[k].trim();
            if (s.startsWith("import ") && s.endsWith(";")) imports.add(k);
        }
        return new Analysis(List.copyOf(types), pkg, topLevelMethods, List.copyOf(imports));
    }

    public static boolean isIdentifier(String s) {
        if (s == null || s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    /** Turns any string into a usable Java class name: {@code "my-demo"} → {@code "my_demo"}. */
    public static String toIdentifier(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(Character.isJavaIdentifierPart(c) ? c : '_');
        if (b.length() == 0 || !Character.isJavaIdentifierStart(b.charAt(0))) b.insert(0, '_');
        return b.toString();
    }

    private static boolean looksLikeReturnType(String prev) {
        return (isIdentifier(prev) && !NOT_METHOD_NAMES.contains(prev)) || prev.equals(">") || prev.equals("]");
    }

    private static boolean hasPublicModifier(List<Token> tokens, int keywordIndex) {
        for (int j = keywordIndex - 1; j >= 0; j--) {
            String s = tokens.get(j).text;
            if (s.equals(";") || s.equals("}") || s.equals("{")) return false;
            if (s.equals("public")) return true;
        }
        return false;
    }

    private static int matchingParen(List<Token> tokens, int open) {
        int d = 0;
        for (int j = open; j < tokens.size(); j++) {
            String s = tokens.get(j).text;
            if (s.equals("(")) d++;
            else if (s.equals(")") && --d == 0) return j;
            else if (s.equals("{") || s.equals(";")) return -1;
        }
        return -1;
    }

    // ------------------------------------------------------------------ lexing

    private record Token(String text, int offset) {
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                out.add(new Token(s.substring(start, i), start));
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == '_')) i++;
                out.add(new Token("0", i));
            } else {
                out.add(new Token(String.valueOf(c), i));
                i++;
            }
        }
        return out;
    }

    /** Replaces comments and the contents of string/char literals with spaces, keeping line breaks. */
    static String mask(String s) {
        char[] out = s.toCharArray();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') out[i++] = ' ';
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                out[i++] = ' ';
                out[i++] = ' ';
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) != '\n') out[i] = ' ';
                    i++;
                }
                if (i < n) out[i++] = ' ';
                if (i < n) out[i++] = ' ';
            } else if (s.startsWith("\"\"\"", i)) {
                i += 3;
                while (i < n && !s.startsWith("\"\"\"", i)) {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        out[i++] = ' ';
                    }
                    if (s.charAt(i) != '\n') out[i] = ' ';
                    i++;
                }
                i = Math.min(n, i + 3);
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n && s.charAt(i + 1) != '\n') {
                        out[i++] = ' ';
                    }
                    out[i++] = ' ';
                }
                i++;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    private static int[] lineStarts(String s) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') starts.add(i + 1);
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] starts, int offset) {
        int lo = 0;
        int hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }
}
