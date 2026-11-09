package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.core.Problem;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The bottom panel: program output, compiler problems and an input line for stdin. */
final class ConsolePanel extends JPanel {

    enum Kind { OUT, ERR, SYS, OK, FAIL, INPUT }

    private record Seg(String text, Kind kind) {
    }

    private static final int MAX_CHARS = 400_000;
    private static final Pattern STACK_LINK = Pattern.compile("\\(([A-Za-z0-9_$]+\\.java):(\\d+)\\)");

    private final JTextPane output = new JTextPane() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final DefaultListModel<Problem> problemModel = new DefaultListModel<>();
    private final JList<Problem> problems = new JList<>(problemModel);
    private final Widgets.Segmented tabs = new Widgets.Segmented(0);
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final CardLayout probCards = new CardLayout();
    private final JPanel probCard = new JPanel(probCards);
    private final JTextField input = new JTextField();
    private final JButton eof;
    private final JLabel caretInfo;
    private final JLabel prompt;
    private final JPanel header;
    private final Deque<Seg> segments = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Seg> pending = new ConcurrentLinkedQueue<>();
    private int totalChars;
    private RunSession session;

    ConsolePanel(BiConsumer<Integer, Integer> navigate, BiConsumer<String, Integer> openStackFrame) {
        super(new BorderLayout());
        setOpaque(false);

        // ---------------------------------------------------------------- header
        header = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.p().console());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.p().hairline());
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(new EmptyBorder(7, 12, 6, 8));
        tabs.add("Output", Icons.tinted("terminal", 14, () -> Theme.p().text2()), null);
        tabs.add("Problems", Icons.tinted("list", 14, () -> Theme.p().text2()), null);
        tabs.onChange(i -> cards.show(body, i == 0 ? "out" : "problems"));
        JButton clear = Widgets.toolButton(Icons.tinted("trash", 15, () -> Theme.p().text2()), "Clear output");
        clear.addActionListener(e -> clear());
        header.add(tabs);
        header.add(Box.createHorizontalGlue());
        header.add(clear);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        tabs.setCursor(Cursor.getDefaultCursor());
        clear.setCursor(Cursor.getDefaultCursor());

        // ---------------------------------------------------------------- output
        output.setEditable(false);
        output.setFont(Theme.mono(12.5f));
        output.setBorder(new EmptyBorder(10, 16, 10, 16));
        output.getCaret().setVisible(false);
        MouseAdapter links = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Matcher m = linkAt(e);
                if (m != null) openStackFrame.accept(m.group(1), Integer.parseInt(m.group(2)));
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                output.setCursor(linkAt(e) != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            }
        };
        output.addMouseListener(links);
        output.addMouseMotionListener(links);
        JScrollPane outScroll = new JScrollPane(output);
        outScroll.setBorder(BorderFactory.createEmptyBorder());

        // ---------------------------------------------------------------- problems
        problems.setCellRenderer(new ProblemRenderer());
        problems.setFixedCellHeight(46);
        problems.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Problem p = problems.getSelectedValue();
                if (p != null && e.getClickCount() >= 1) navigate.accept(p.markdownLine(), p.column());
            }
        });
        problems.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Problem p = problems.getSelectedValue();
                if (e.getKeyCode() == KeyEvent.VK_ENTER && p != null) navigate.accept(p.markdownLine(), p.column());
            }
        });
        JScrollPane probScroll = new JScrollPane(problems);
        probScroll.setBorder(BorderFactory.createEmptyBorder());
        JLabel noProblems = Widgets.label("No problems", Theme.ui(Font.PLAIN, 13f), () -> Theme.p().text3());
        noProblems.setHorizontalAlignment(JLabel.CENTER);
        probCard.add(noProblems, "empty");
        probCard.add(probScroll, "list");
        body.add(outScroll, "out");
        body.add(probCard, "problems");

        // ---------------------------------------------------------------- footer (owns the bottom corner)
        JPanel footer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                MacWindow.paintRounded(this, g, Theme.p().console());
                g.setColor(Theme.p().hairline());
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(5, 14, 7, 14));
        footer.setPreferredSize(new Dimension(10, 38));
        prompt = Widgets.label("›", Theme.mono(15f), () -> session != null ? Theme.p().accent() : Theme.p().text3());
        prompt.setBorder(new EmptyBorder(0, 0, 2, 8));
        input.putClientProperty("JTextField.placeholderText", "Program input — available while a program is running");
        input.putClientProperty("JComponent.roundRect", true);
        input.setFont(Theme.mono(12.5f));
        input.setEnabled(false);
        input.addActionListener(e -> {
            if (session == null) return;
            String line = input.getText();
            append(line + "\n", Kind.INPUT);
            session.sendInput(line);
            input.setText("");
        });
        eof = Widgets.toolButton(null, "Close the program's input (end of file)");
        eof.setText("EOF");
        eof.setFont(Theme.ui(Font.PLAIN, 11.5f));
        eof.setEnabled(false);
        eof.addActionListener(e -> {
            if (session != null) {
                session.closeInput();
                append("^D\n", Kind.INPUT);
                setInputEnabled(false);
            }
        });
        caretInfo = Widgets.label("", Theme.ui(Font.PLAIN, 11.5f), () -> Theme.p().text2());
        caretInfo.setBorder(new EmptyBorder(0, 14, 0, 0));
        JPanel inputRow = new JPanel(new BorderLayout());
        inputRow.setOpaque(false);
        inputRow.add(prompt, BorderLayout.WEST);
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(eof, BorderLayout.EAST);
        footer.add(inputRow, BorderLayout.CENTER);
        footer.add(caretInfo, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        new Timer(40, e -> flush()).start();
        applyTheme();
        Theme.onChange(this::applyTheme);
    }

    JComponent header() {
        return header;
    }

    // ---------------------------------------------------------------- output

    /** Thread-safe. Text is batched and painted every 40 ms. */
    void append(String text, Kind kind) {
        if (!text.isEmpty()) pending.add(new Seg(text, kind));
    }

    void clear() {
        pending.clear();
        segments.clear();
        totalChars = 0;
        output.setText("");
    }

    void showOutput() {
        tabs.select(0);
        cards.show(body, "out");
    }

    void showProblems() {
        tabs.select(1);
        cards.show(body, "problems");
    }

    void setProblems(List<Problem> list) {
        problemModel.clear();
        for (Problem p : list) problemModel.addElement(p);
        long errors = list.stream().filter(Problem::isError).count();
        tabs.setLabel(1, list.isEmpty() ? "Problems" : "Problems  " + (errors > 0 ? errors : list.size()));
        probCards.show(probCard, list.isEmpty() ? "empty" : "list");
        body.revalidate();
        body.repaint();
    }

    void setSession(RunSession s) {
        session = s;
        setInputEnabled(s != null);
        prompt.repaint();
    }

    void setInputEnabled(boolean on) {
        input.setEnabled(on);
        eof.setEnabled(on);
        if (on) input.requestFocusInWindow();
    }

    void setCaretInfo(String s) {
        caretInfo.setText(s);
    }

    private void flush() {
        if (pending.isEmpty()) return;
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, output);
        boolean atBottom = true;
        if (sp != null) {
            var bar = sp.getVerticalScrollBar();
            atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 24;
        }
        StyledDocument doc = output.getStyledDocument();
        Seg s;
        try {
            while ((s = pending.poll()) != null) {
                doc.insertString(doc.getLength(), s.text(), attrs(s.kind()));
                Seg last = segments.peekLast();
                if (last != null && last.kind() == s.kind() && last.text().length() < 4096) {
                    segments.pollLast();
                    segments.addLast(new Seg(last.text() + s.text(), s.kind()));
                } else {
                    segments.addLast(s);
                }
                totalChars += s.text().length();
            }
            while (totalChars > MAX_CHARS && !segments.isEmpty()) {
                Seg first = segments.pollFirst();
                doc.remove(0, first.text().length());
                totalChars -= first.text().length();
            }
        } catch (BadLocationException ignored) {
            // cannot happen: we only append at the end
        }
        if (atBottom) output.setCaretPosition(doc.getLength());
    }

    private SimpleAttributeSet attrs(Kind kind) {
        Theme.Palette p = Theme.p();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, output.getFont().getFamily());
        StyleConstants.setFontSize(a, Math.round(output.getFont().getSize2D()));
        switch (kind) {
            case OUT -> StyleConstants.setForeground(a, p.text());
            case ERR -> StyleConstants.setForeground(a, p.red());
            case SYS -> StyleConstants.setForeground(a, p.text2());
            case OK -> StyleConstants.setForeground(a, p.green());
            case FAIL -> {
                StyleConstants.setForeground(a, p.red());
                StyleConstants.setBold(a, true);
            }
            case INPUT -> {
                StyleConstants.setForeground(a, p.accent());
                StyleConstants.setBold(a, true);
            }
        }
        return a;
    }

    private void applyTheme() {
        Theme.Palette p = Theme.p();
        output.setBackground(p.console());
        output.setForeground(p.text());
        output.setSelectionColor(p.selection());
        problems.setBackground(p.console());
        body.setBackground(p.console());
        for (Component c : body.getComponents()) c.setBackground(p.console());
        for (Component c : probCard.getComponents()) c.setBackground(p.console());
        probCard.setBackground(p.console());
        // Re-color the existing text.
        StyledDocument doc = output.getStyledDocument();
        int pos = 0;
        for (Seg s : segments) {
            doc.setCharacterAttributes(pos, s.text().length(), attrs(s.kind()), true);
            pos += s.text().length();
        }
        repaint();
    }

    private Matcher linkAt(MouseEvent e) {
        int pos = output.viewToModel2D(e.getPoint());
        if (pos < 0) return null;
        Element root = output.getDocument().getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(pos));
        try {
            String text = output.getDocument().getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset());
            int col = pos - line.getStartOffset();
            Matcher m = STACK_LINK.matcher(text);
            while (m.find()) {
                if (col >= m.start() && col <= m.end()) return m;
            }
        } catch (BadLocationException ignored) {
            // outside the document
        }
        return null;
    }

    // ---------------------------------------------------------------- problem rows

    private static final class ProblemRenderer extends JPanel implements ListCellRenderer<Problem> {
        private final JLabel icon = new JLabel();
        private final JLabel message = new JLabel();
        private final JLabel where = new JLabel();
        private boolean selected;

        ProblemRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(6, 16, 6, 16));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(message);
            text.add(Box.createVerticalStrut(2));
            text.add(where);
            icon.setVerticalAlignment(JLabel.TOP);
            icon.setBorder(new EmptyBorder(2, 0, 0, 0));
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Problem> list, Problem p, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Theme.Palette pal = Theme.p();
            selected = isSelected;
            icon.setIcon(Icons.colored(p.isError() ? "status-error" : "status-warning", 15));
            String first = p.message().lines().findFirst().orElse("");
            message.setText(first);
            message.setFont(Theme.ui(Font.PLAIN, 12.5f));
            message.setForeground(pal.text());
            StringBuilder w = new StringBuilder();
            if (p.markdownLine() > 0) {
                w.append("Line ").append(p.markdownLine());
                if (p.column() > 0) w.append(", column ").append(p.column());
            }
            if (!p.javaFile().isEmpty()) w.append(w.length() > 0 ? "  ·  " : "").append(p.javaFile());
            if (!p.sourceLine().isBlank()) w.append("  ·  ").append(p.sourceLine().strip());
            where.setText(w.toString());
            where.setFont(Theme.ui(Font.PLAIN, 11.5f));
            where.setForeground(pal.text2());
            setBackground(pal.console());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.p().console());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (selected) {
                var g2 = Widgets.smooth(g);
                g2.setColor(Theme.p().selection());
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(6, 2, getWidth() - 12, getHeight() - 4, 10, 10));
                g2.dispose();
            }
            Color line = Theme.p().hairline();
            g.setColor(Theme.alpha(line, 120));
            g.fillRect(16, getHeight() - 1, getWidth() - 32, 1);
        }
    }

    /** Paints pending output immediately (used for screenshots). */
    void flushNow() {
        flush();
    }
}
