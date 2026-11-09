package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.Version;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Shown when no document is open: a friendly start screen in the style of Xcode's welcome window. */
final class WelcomePanel extends JPanel {

    WelcomePanel(Runnable newDoc, Runnable open, Runnable tour, String shortcutPrefix) {
        super(new GridBagLayout());
        setOpaque(false);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(Icons.app(112));
        JLabel title = Widgets.label(Version.NAME, Theme.semibold(28f), () -> Theme.p().text());
        JLabel tagline = Widgets.label(Version.TAGLINE + ".", Theme.ui(Font.PLAIN, 14f), () -> Theme.p().text2());
        JLabel version = Widgets.label("Version " + Version.get(), Theme.ui(Font.PLAIN, 11.5f), () -> Theme.p().text3());

        for (JComponent c : new JComponent[]{logo, title, tagline, version}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            if (c instanceof JLabel l) l.setHorizontalAlignment(SwingConstants.CENTER);
        }
        column.add(logo);
        column.add(Box.createVerticalStrut(10));
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        column.add(tagline);
        column.add(Box.createVerticalStrut(2));
        column.add(version);
        column.add(Box.createVerticalStrut(28));

        column.add(new Option("doc-plus", "New Document", "Start writing Markdown with a Java snippet", shortcutPrefix + "N", newDoc));
        column.add(Box.createVerticalStrut(6));
        column.add(new Option("folder", "Open Markdown File…", "Run the Java blocks of any .md file", shortcutPrefix + "O", open));
        column.add(Box.createVerticalStrut(6));
        column.add(new Option("book", "Take the Tour", "An example document that shows every feature", "", tour));
        column.add(Box.createVerticalStrut(22));

        JLabel hint = Widgets.label("Tip: drop a Markdown file anywhere in this window", Theme.ui(Font.PLAIN, 12f), () -> Theme.p().text3());
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(hint);
        add(column);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.p().window());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** A large clickable row: icon tile, title, description and keyboard shortcut. */
    private static final class Option extends JComponent {
        private final Icon icon;
        private final String title;
        private final String subtitle;
        private final String shortcut;
        private boolean hover;

        Option(String iconName, String title, String subtitle, String shortcut, Runnable action) {
            this.icon = Icons.tinted(iconName, 20, () -> Theme.p().accent());
            this.title = title;
            this.subtitle = subtitle;
            this.shortcut = shortcut;
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(420, 58);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Widgets.smooth(g);
            Theme.Palette p = Theme.p();
            int w = getWidth();
            int h = getHeight();
            g2.setColor(hover ? p.hover() : Theme.alpha(p.hover(), p.hover().getAlpha() / 2));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 14, 14));

            int tile = 36;
            g2.setColor(Theme.alpha(p.accent(), Theme.isDark() ? 50 : 30));
            g2.fill(new RoundRectangle2D.Double(12, (h - tile) / 2.0, tile, tile, 10, 10));
            icon.paintIcon(this, g2, 12 + (tile - 20) / 2, (h - 20) / 2);

            int x = 12 + tile + 14;
            Font tf = Theme.semibold(13.5f);
            g2.setFont(tf);
            g2.setColor(p.text());
            g2.drawString(title, x, h / 2 - 3);
            Font sf = Theme.ui(Font.PLAIN, 12f);
            g2.setFont(sf);
            g2.setColor(p.text2());
            g2.drawString(subtitle, x, h / 2 + g2.getFontMetrics().getAscent() + 1);

            if (!shortcut.isEmpty()) {
                g2.setFont(Theme.ui(Font.PLAIN, 12f));
                FontMetrics km = g2.getFontMetrics();
                int kw = km.stringWidth(shortcut) + 14;
                int kx = w - 14 - kw;
                int ky = (h - 22) / 2;
                g2.setColor(p.controlBg());
                g2.fill(new RoundRectangle2D.Double(kx, ky, kw, 22, 8, 8));
                g2.setColor(p.text2());
                g2.drawString(shortcut, kx + 7, ky + (22 - km.getHeight()) / 2 + km.getAscent());
            }
            g2.dispose();
        }
    }
}
