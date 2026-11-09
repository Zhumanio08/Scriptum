package live.justzhuman.scriptum.gui;

import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerBase;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;
import org.fife.ui.rsyntaxtextarea.modes.MarkdownTokenMaker;

import javax.swing.text.Segment;

/**
 * Highlights Markdown and, inside {@code ```java} fences, real Java syntax.
 *
 * <p>RSyntaxTextArea's own Markdown mode paints fenced code in a single color. This maker
 * delegates prose lines to the Markdown lexer and fenced Java lines to the Java lexer,
 * carrying both lexers' multi-line state in the "last token type" of each line.
 * Markdown token types are remapped to types the Java lexer never uses, so headings and
 * Java keywords can have different colors.
 */
public final class MarkdownJavaTokenMaker extends TokenMakerBase {

    public static final String SYNTAX_STYLE = "text/x-scriptum-markdown";

    /** Remapped Markdown token types. */
    static final int MD_HEADING = TokenTypes.MARKUP_PROCESSING_INSTRUCTION;
    static final int MD_BOLD = TokenTypes.MARKUP_CDATA;
    static final int MD_ITALIC = TokenTypes.MARKUP_COMMENT;
    static final int MD_LIST = TokenTypes.MARKUP_DTD;
    static final int MD_LINK_URL = TokenTypes.MARKUP_CDATA_DELIMITER;
    static final int FENCE = TokenTypes.MARKUP_TAG_DELIMITER;
    static final int FENCE_LANG = TokenTypes.VARIABLE;
    static final int FENCE_ATTR = TokenTypes.MARKUP_TAG_ATTRIBUTE;

    // Line-end states. Java's own state is stored as an offset from JAVA_BASE.
    private static final int JAVA_BASE = -10_000;
    private static final int OTHER_FENCE = -20_000;

    private final JavaTokenMaker javaLexer = new JavaTokenMaker();
    private final MarkdownTokenMaker markdown = new MarkdownTokenMaker();

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        boolean inJava = initialTokenType <= JAVA_BASE + 5_000 && initialTokenType > JAVA_BASE - 5_000;
        boolean inOther = initialTokenType == OTHER_FENCE;
        String line = new String(text.array, text.offset, text.count);
        String trimmed = line.strip();

        if (inJava || inOther) {
            if (isFenceOnly(trimmed)) {
                addToken(text, text.offset, text.offset + text.count - 1, FENCE, startOffset);
                addNullToken();
                return firstToken;
            }
            if (inOther) {
                if (text.count > 0) addToken(text, text.offset, text.offset + text.count - 1, TokenTypes.LITERAL_BACKQUOTE, startOffset);
                addEndMarker(text, startOffset, OTHER_FENCE);
                return firstToken;
            }
            int javaState = initialTokenType - JAVA_BASE;
            int end = copy(javaLexer.getTokenList(text, javaState, startOffset), false);
            addEndMarker(text, startOffset, JAVA_BASE + end);
            return firstToken;
        }

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            char ch = trimmed.charAt(0);
            int lead = line.indexOf(ch);
            int j = lead;
            while (j < line.length() && line.charAt(j) == ch) j++;
            String info = line.substring(j).strip();
            String lang = info.split("[\\s{]", 2)[0].toLowerCase(java.util.Locale.ROOT);
            int s = text.offset;
            if (lead > 0) addToken(text, s, s + lead - 1, TokenTypes.WHITESPACE, startOffset);
            addToken(text, s + lead, s + j - 1, FENCE, startOffset + lead);
            if (j < line.length()) {
                int langStart = line.indexOf(lang.isEmpty() ? " " : lang.substring(0, 1), j);
                if (!lang.isEmpty() && langStart >= 0 && line.regionMatches(true, langStart, lang, 0, lang.length())) {
                    if (langStart > j) addToken(text, s + j, s + langStart - 1, TokenTypes.WHITESPACE, startOffset + j);
                    int langEnd = langStart + lang.length();
                    addToken(text, s + langStart, s + langEnd - 1, FENCE_LANG, startOffset + langStart);
                    if (langEnd < line.length()) {
                        addToken(text, s + langEnd, s + line.length() - 1, FENCE_ATTR, startOffset + langEnd);
                    }
                } else {
                    addToken(text, s + j, s + line.length() - 1, FENCE_ATTR, startOffset + j);
                }
            }
            addEndMarker(text, startOffset, lang.equals("java") ? JAVA_BASE : OTHER_FENCE);
            return firstToken;
        }

        // Inline Markdown spans (code, bold, ...) never continue onto the next line here:
        // a stray backtick must not paint the rest of the document.
        copy(markdown.getTokenList(text, TokenTypes.NULL, startOffset), true);
        addNullToken();
        return firstToken;
    }

    /** Copies the paintable tokens of another lexer and returns its line-end state. */
    private int copy(Token t, boolean remapMarkdown) {
        int last = TokenTypes.NULL;
        while (t != null) {
            last = t.getType();
            if (t.isPaintable()) {
                int type = remapMarkdown ? remap(t.getType()) : t.getType();
                int start = t.getTextOffset();
                addToken(t.getTextArray(), start, start + t.length() - 1, type, t.getOffset());
            }
            t = t.getNextToken();
        }
        return last;
    }

    private static int remap(int type) {
        return switch (type) {
            case TokenTypes.RESERVED_WORD -> MD_HEADING;
            case TokenTypes.RESERVED_WORD_2 -> MD_BOLD;
            case TokenTypes.DATA_TYPE -> MD_ITALIC;
            case TokenTypes.LITERAL_NUMBER_DECIMAL_INT -> MD_LIST;
            case TokenTypes.ANNOTATION -> MD_LINK_URL;
            default -> type;
        };
    }

    private void addEndMarker(Segment text, int startOffset, int state) {
        if (state == TokenTypes.NULL) {
            addNullToken();
            return;
        }
        int end = text.offset + text.count;
        addToken(text.array, end, end, state, startOffset + text.count);
    }

    private static boolean isFenceOnly(String s) {
        if (s.length() < 3) return false;
        char c = s.charAt(0);
        if (c != '`' && c != '~') return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) != c) return false;
        return true;
    }

    @Override
    public String[] getLineCommentStartAndEnd(int languageIndex) {
        return new String[]{"<!-- ", " -->"};
    }

    @Override
    public boolean getCurlyBracesDenoteCodeBlocks(int languageIndex) {
        return true;
    }

    @Override
    public boolean getShouldIndentNextLineAfter(Token t) {
        return javaLexer.getShouldIndentNextLineAfter(t);
    }
}
