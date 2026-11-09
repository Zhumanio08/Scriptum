package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.core.CodeBlock;
import live.justzhuman.scriptum.core.Problem;
import live.justzhuman.scriptum.core.Program;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The Markdown editor: syntax highlighting, code-block backgrounds, error markers and run buttons in the gutter. */
final class Editor {

    static {
        AbstractTokenMakerFactory f = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        f.putMapping(MarkdownJavaTokenMaker.SYNTAX_STYLE, MarkdownJavaTokenMaker.class.getName());
    }

    final RSyntaxTextArea area = new RSyntaxTextArea(20, 80);
    final RTextScrollPane scroll;
    private final Map<Integer, Program.EntryPoint> runLines = new HashMap<>();
    private Program program;
    private List<Problem> problems = List.of();

    Editor(Consumer<Program.EntryPoint> runEntry) {
        area.setSyntaxEditingStyle(MarkdownJavaTokenMaker.SYNTAX_STYLE);
        area.setCodeFoldingEnabled(false);
        area.setTabSize(4);
        area.setTabsEmulated(true);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setAntiAliasingEnabled(true);
        area.setFractionalFontMetricsEnabled(true);
        area.setHighlightCurrentLine(true);
        area.setFadeCurrentLineHighlight(false);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setPaintMatchedBracketPair(true);
        area.setMarkOccurrences(false);
        area.setAutoIndentEnabled(true);
        area.setCloseCurlyBraces(true);
        area.setMargin(new Insets(0, 10, 24, 22));
        area.setBorder(new EmptyBorder(0, 0, 0, 0));

        scroll = new RTextScrollPane(area, true);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        scroll.setViewportBorder(BorderFactory.createEmptyBorder());
        Gutter gutter = scroll.getGutter();
        gutter.setIconRowHeaderEnabled(true);
        gutter.setIconRowHeaderInheritsGutterBackground(true);

        gutter.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(gutter, e.getPoint(), area);
                int offset = area.viewToModel2D(new Point(0, p.y));
                try {
                    int line = area.getLineOfOffset(offset) + 1;
                    Program.EntryPoint ep = runLines.get(line);
                    if (ep != null) runEntry.accept(ep);
                } catch (BadLocationException ignored) {
                    // click below the last line
                }
            }
        });
        applyTheme();
        Theme.onChange(this::applyTheme);
    }

    String text() {
        return area.getText();
    }

    void setText(String s) {
        area.setText(s);
        area.setCaretPosition(0);
        area.discardAllEdits();
    }

    /** Moves the caret to a 1-based Markdown line/column and centers it. */
    void goTo(int line, int column) {
        if (line < 1) return;
        Element root = area.getDocument().getDefaultRootElement();
        int idx = Math.min(line - 1, root.getElementCount() - 1);
        Element el = root.getElement(idx);
        int pos = el.getStartOffset() + Math.max(0, Math.min(column - 1, el.getEndOffset() - el.getStartOffset() - 1));
        area.setCaretPosition(pos);
        try {
            Rectangle r = area.modelToView2D(pos).getBounds();
            Rectangle visible = area.getVisibleRect();
            r.y = Math.max(0, r.y - visible.height / 3);
            r.height = visible.height;
            area.scrollRectToVisible(r);
        } catch (BadLocationException | NullPointerException ignored) {
            // not laid out yet
        }
        area.requestFocusInWindow();
    }

    void setProgram(Program p) {
        this.program = p;
        refreshMarkers();
    }

    void setProblems(List<Problem> list) {
        this.problems = list;
        refreshMarkers();
    }

    /** Re-draws the code-block backgrounds, problem lines and gutter icons. */
    void refreshMarkers() {
        area.removeAllLineHighlights();
        Gutter gutter = scroll.getGutter();
        gutter.removeAllTrackingIcons();
        runLines.clear();
        int lines = area.getLineCount();
        Theme.Palette pal = Theme.p();
        java.util.Set<Integer> problemLines = new java.util.HashSet<>();
        for (Problem p : problems) problemLines.add(p.markdownLine() - 1);
        try {
            if (program != null) {
                for (CodeBlock b : program.blocks()) {
                    int from = b.fenceLine() - 1;
                    int to = Math.min(lines - 1, b.fenceLine() + b.lines().size() + (b.closed() ? 0 : -1));
                    Color bg = b.isSkipped() ? Theme.alpha(pal.codeBlock(), pal.codeBlock().getAlpha() / 2) : pal.codeBlock();
                    for (int l = from; l <= to; l++) if (!problemLines.contains(l)) area.addLineHighlight(l, bg);
                }
                for (Program.EntryPoint ep : program.entryPoints()) {
                    int l = ep.markdownLine();
                    if (l >= 1 && l <= lines) {
                        runLines.put(l, ep);
                        gutter.addLineTrackingIcon(l - 1, Icons.colored("gutter-run", 14), "Run " + ep.simpleName());
                    }
                }
            }
            for (Problem p : problems) {
                int l = p.markdownLine();
                if (l < 1 || l > lines) continue;
                area.addLineHighlight(l - 1, p.isError() ? pal.errorLine() : pal.warningLine());
                gutter.addLineTrackingIcon(l - 1, Icons.colored(p.isError() ? "gutter-error" : "gutter-warning", 14),
                        p.message().lines().findFirst().orElse(""));
            }
        } catch (BadLocationException ignored) {
            // document changed under us; the next refresh fixes it
        }
    }

    private void applyTheme() {
        Theme.Palette p = Theme.p();
        boolean dark = Theme.isDark();
        Font font = Theme.mono(13.5f);
        area.setFont(font);
        area.setBackground(p.window());
        area.setForeground(p.text());
        area.setCaretColor(p.accent());
        area.setSelectionColor(dark ? new Color(0x3F3D7A) : new Color(0xD9D8FB));
        area.setSelectedTextColor(null);
        area.setUseSelectedTextColor(false);
        area.setCurrentLineHighlightColor(dark ? new Color(255, 255, 255, 10) : new Color(0, 0, 0, 8));
        area.setMatchedBracketBGColor(Theme.alpha(p.accent(), 40));
        area.setMatchedBracketBorderColor(Theme.alpha(p.accent(), 140));

        Gutter g = scroll.getGutter();
        g.setBackground(p.window());
        g.setLineNumberColor(p.text3());
        g.setCurrentLineNumberColor(p.text2());
        g.setLineNumberFont(Theme.mono(11.5f));
        g.setBorderColor(p.window());
        g.setActiveLineRangeColor(p.accent());
        scroll.getViewport().setBackground(p.window());
        scroll.setBackground(p.window());

        SyntaxScheme s = new SyntaxScheme(font);
        Color text = p.text();
        for (int i = 0; i < TokenTypes.DEFAULT_NUM_TOKEN_TYPES; i++) s.setStyle(i, new Style(text));

        // Java (palette inspired by Xcode's default themes)
        Color keyword = dark ? new Color(0xFF7AB2) : new Color(0xAD3DA4);
        Color string = dark ? new Color(0xFF8170) : new Color(0xC41A16);
        Color number = dark ? new Color(0xD9C97C) : new Color(0x1C00CF);
        Color comment = dark ? new Color(0x7F8C98) : new Color(0x707F8C);
        Color type = dark ? new Color(0xDABAFF) : new Color(0x703DAA);
        Color func = dark ? new Color(0x67B7A4) : new Color(0x326D74);
        Color annotation = dark ? new Color(0xFD8F3F) : new Color(0x643820);
        Font bold = font.deriveFont(Font.BOLD);
        Font italic = font.deriveFont(Font.ITALIC);
        s.setStyle(TokenTypes.RESERVED_WORD, new Style(keyword, null, bold));
        s.setStyle(TokenTypes.RESERVED_WORD_2, new Style(keyword, null, bold));
        s.setStyle(TokenTypes.LITERAL_BOOLEAN, new Style(keyword, null, bold));
        s.setStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, new Style(string));
        s.setStyle(TokenTypes.LITERAL_CHAR, new Style(string));
        s.setStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT, new Style(number));
        s.setStyle(TokenTypes.LITERAL_NUMBER_FLOAT, new Style(number));
        s.setStyle(TokenTypes.LITERAL_NUMBER_HEXADECIMAL, new Style(number));
        s.setStyle(TokenTypes.COMMENT_EOL, new Style(comment, null, italic));
        s.setStyle(TokenTypes.COMMENT_MULTILINE, new Style(comment, null, italic));
        s.setStyle(TokenTypes.COMMENT_DOCUMENTATION, new Style(comment, null, italic));
        s.setStyle(TokenTypes.COMMENT_KEYWORD, new Style(comment, null, bold));
        s.setStyle(TokenTypes.COMMENT_MARKUP, new Style(comment, null, italic));
        s.setStyle(TokenTypes.DATA_TYPE, new Style(type, null, bold));
        s.setStyle(TokenTypes.FUNCTION, new Style(func));
        s.setStyle(TokenTypes.ANNOTATION, new Style(annotation));
        s.setStyle(TokenTypes.OPERATOR, new Style(text));
        s.setStyle(TokenTypes.SEPARATOR, new Style(p.text2()));
        s.setStyle(TokenTypes.ERROR_STRING_DOUBLE, new Style(p.red()));
        s.setStyle(TokenTypes.ERROR_IDENTIFIER, new Style(p.red()));
        s.setStyle(TokenTypes.ERROR_NUMBER_FORMAT, new Style(p.red()));
        s.setStyle(TokenTypes.ERROR_CHAR, new Style(p.red()));

        // Markdown
        Font semibold = Theme.mono(13.5f).deriveFont(Font.BOLD);
        s.setStyle(MarkdownJavaTokenMaker.MD_HEADING, new Style(p.accent(), null, semibold));
        s.setStyle(MarkdownJavaTokenMaker.MD_BOLD, new Style(text, null, bold));
        s.setStyle(MarkdownJavaTokenMaker.MD_ITALIC, new Style(text, null, italic));
        s.setStyle(MarkdownJavaTokenMaker.MD_LIST, new Style(p.accent(), null, bold));
        s.setStyle(MarkdownJavaTokenMaker.MD_LINK_URL, new Style(p.text3()));
        s.setStyle(TokenTypes.REGEX, new Style(p.accent()));
        s.setStyle(TokenTypes.PREPROCESSOR, new Style(dark ? new Color(0xFFB86C) : new Color(0xB4430C),
                dark ? new Color(255, 184, 108, 22) : new Color(180, 67, 12, 14)));
        s.setStyle(MarkdownJavaTokenMaker.FENCE, new Style(p.text3()));
        s.setStyle(MarkdownJavaTokenMaker.FENCE_LANG, new Style(p.accent(), null, bold));
        s.setStyle(MarkdownJavaTokenMaker.FENCE_ATTR, new Style(p.text2(), null, italic));
        s.setStyle(TokenTypes.LITERAL_BACKQUOTE, new Style(p.text2()));
        s.setStyle(TokenTypes.MARKUP_TAG_NAME, new Style(keyword));
        s.setStyle(TokenTypes.MARKUP_TAG_ATTRIBUTE, new Style(p.text2(), null, italic));
        s.setStyle(TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE, new Style(string));
        s.setStyle(TokenTypes.MARKUP_ENTITY_REFERENCE, new Style(number));
        area.setSyntaxScheme(s);
        refreshMarkers();
        area.repaint();
    }
}
