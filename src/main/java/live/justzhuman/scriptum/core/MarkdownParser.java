package live.justzhuman.scriptum.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts fenced Java code blocks from Markdown.
 *
 * <p>Follows the CommonMark rules that matter in practice:
 * <ul>
 *   <li>fences are {@code ```} or {@code ~~~} (three or more), indented by any amount;</li>
 *   <li>a block is closed only by the same fence character repeated at least as many times,
 *       so a {@code ````markdown} block may safely contain {@code ```java} examples;</li>
 *   <li>the indentation of the opening fence is removed from the code lines;</li>
 *   <li>an unclosed block runs to the end of the document.</li>
 * </ul>
 *
 * <p>The info string may carry attributes: {@code ```java class="Main" norun}.
 */
public final class MarkdownParser {

    private MarkdownParser() {
    }

    public static List<CodeBlock> parse(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<CodeBlock> blocks = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            Fence open = Fence.opening(lines[i]);
            if (open == null) {
                i++;
                continue;
            }
            int fenceLine = i + 1;
            List<String> body = new ArrayList<>();
            boolean closed = false;
            i++;
            while (i < lines.length) {
                if (open.isClosedBy(lines[i])) {
                    closed = true;
                    i++;
                    break;
                }
                body.add(stripIndent(lines[i], open.indent));
                i++;
            }
            // A trailing empty line produced by a final newline is not part of an unclosed block.
            if (!closed && !body.isEmpty() && body.get(body.size() - 1).isEmpty()) {
                body.remove(body.size() - 1);
            }
            if (open.isJava()) {
                blocks.add(new CodeBlock(blocks.size() + 1, fenceLine, body, open.attributes, closed));
            }
        }
        return blocks;
    }

    private static String stripIndent(String line, int indent) {
        int n = 0;
        while (n < indent && n < line.length() && line.charAt(n) == ' ') n++;
        return line.substring(n);
    }

    /** Parses {@code key="v" key='v' key=v flag} pairs from the rest of an info string. */
    static Map<String, String> parseAttributes(String s) {
        Map<String, String> attrs = new LinkedHashMap<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',' || s.charAt(i) == '{' || s.charAt(i) == '}')) i++;
            int keyStart = i;
            while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '=' && s.charAt(i) != '}') i++;
            if (i == keyStart) {
                i++;
                continue;
            }
            String key = s.substring(keyStart, i).toLowerCase(Locale.ROOT);
            if (key.startsWith(".")) key = key.substring(1);
            String value = "";
            if (i < n && s.charAt(i) == '=') {
                i++;
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i++);
                    int end = s.indexOf(q, i);
                    if (end < 0) end = n;
                    value = s.substring(i, end);
                    i = Math.min(n, end + 1);
                } else {
                    int start = i;
                    while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '}') i++;
                    value = s.substring(start, i);
                }
            }
            attrs.put(key, value.trim());
        }
        return attrs;
    }

    private static final class Fence {
        final char ch;
        final int length;
        final int indent;
        final String language;
        final Map<String, String> attributes;

        private Fence(char ch, int length, int indent, String language, Map<String, String> attributes) {
            this.ch = ch;
            this.length = length;
            this.indent = indent;
            this.language = language;
            this.attributes = attributes;
        }

        static Fence opening(String line) {
            int indent = 0;
            while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) indent++;
            if (indent >= line.length()) return null;
            char c = line.charAt(indent);
            if (c != '`' && c != '~') return null;
            int j = indent;
            while (j < line.length() && line.charAt(j) == c) j++;
            int len = j - indent;
            if (len < 3) return null;
            String info = line.substring(j).trim();
            if (c == '`' && info.indexOf('`') >= 0) return null; // inline code, not a fence

            String lang;
            String rest;
            if (info.startsWith("{")) {
                // Pandoc style: ```{.java class="Main"}
                lang = "";
                rest = info;
                for (String token : info.replace("{", " ").replace("}", " ").trim().split("\\s+")) {
                    if (token.startsWith(".")) {
                        lang = token.substring(1);
                        break;
                    }
                }
            } else {
                int sp = 0;
                while (sp < info.length() && !Character.isWhitespace(info.charAt(sp)) && info.charAt(sp) != '{') sp++;
                lang = info.substring(0, sp);
                rest = info.substring(sp);
            }
            Map<String, String> attrs = parseAttributes(rest);
            attrs.remove(lang.toLowerCase(Locale.ROOT));
            return new Fence(c, len, indent, lang.toLowerCase(Locale.ROOT), attrs);
        }

        boolean isJava() {
            return language.equals("java");
        }

        boolean isClosedBy(String line) {
            String t = line.trim();
            if (t.length() < length) return false;
            for (int k = 0; k < t.length(); k++) {
                if (t.charAt(k) != ch) return false;
            }
            return true;
        }
    }
}
