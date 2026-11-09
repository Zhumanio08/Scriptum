package live.justzhuman.scriptum.gui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Small macOS-flavoured controls. */
final class Widgets {

    private Widgets() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    /** A borderless icon button that shows a rounded highlight on hover, like a macOS toolbar item. */
    static JButton toolButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.putClientProperty("JComponent.minimumHeight", 28);
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(5, 6, 5, 6));
        b.setCursor(Cursor.getDefaultCursor());
        return b;
    }

    static JLabel label(String text, Font font, Supplier<Color> color) {
        JLabel l = new JLabel(text) {
            @Override
            public Color getForeground() {
                return color == null ? super.getForeground() : color.get();
            }
        };
        l.setFont(font);
        return l;
    }

    /** Small uppercase sidebar section title. */
    static JLabel section(String text) {
        JLabel l = label(text, Theme.semibold(11f), () -> Theme.p().text3());
        l.setBorder(new EmptyBorder(14, 18, 6, 12));
        return l;
    }

    // ------------------------------------------------------------------ accent button

    /** A filled, rounded push button (the prominent "Run" button). */
    static final class PillButton extends JButton {
        private boolean hover;
        private boolean down;
        private final Supplier<Color> fill;

        PillButton(String text, Icon icon, Supplier<Color> fill) {
            super(text, icon);
            this.fill = fill;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setIconTextGap(6);
            setFont(Theme.semibold(12.5f));
            setBorder(new EmptyBorder(0, 12, 0, 14));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    down = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    down = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    down = false;
                    repaint();
                }
            });
        }

        @Override
        public Color getForeground() {
            return fill == null ? super.getForeground() : Theme.p().onAccent();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, 28);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Color c = fill.get();
            if (!isEnabled()) c = Theme.alpha(c, 110);
            else if (down) c = Theme.mix(c, Color.BLACK, 0.15f);
            else if (hover) c = Theme.mix(c, Color.WHITE, 0.10f);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
            // Subtle top highlight for depth.
            g2.setColor(new Color(255, 255, 255, isEnabled() ? 40 : 15));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------ segmented control

    /** macOS segmented control: a pill-shaped track with a raised selected segment. */
    static final class Segmented extends JComponent {
        private final List<String> labels = new ArrayList<>();
        private final List<Icon> icons = new ArrayList<>();
        private final List<String> tips = new ArrayList<>();
        private int selected;
        private int hover = -1;
        private IntConsumer onChange = i -> { };
        private final int segmentWidth;

        Segmented(int segmentWidth) {
            this.segmentWidth = segmentWidth;
            setFont(Theme.ui(Font.PLAIN, 12f));
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i >= 0 && i != selected) {
                        selected = i;
                        repaint();
                        onChange.accept(i);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hover) {
                        hover = i;
                        setToolTipText(i >= 0 && tips.get(i) != null ? tips.get(i) : null);
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        Segmented add(String label, Icon icon, String tip) {
            labels.add(label);
            icons.add(icon);
            tips.add(tip);
            return this;
        }

        void setLabel(int i, String label) {
            labels.set(i, label);
            revalidate();
            repaint();
        }

        void onChange(IntConsumer c) {
            onChange = c;
        }

        int selected() {
            return selected;
        }

        void select(int i) {
            selected = i;
            repaint();
        }

        private int widthOf(int i) {
            if (segmentWidth > 0) return segmentWidth;
            FontMetrics fm = getFontMetrics(getFont());
            int w = 20;
            if (labels.get(i) != null) w += fm.stringWidth(labels.get(i));
            if (icons.get(i) != null) w += icons.get(i).getIconWidth() + (labels.get(i) != null ? 5 : 0);
            return Math.max(w, 30);
        }

        private int indexAt(int x) {
            int pos = 2;
            for (int i = 0; i < labels.size(); i++) {
                int w = widthOf(i);
                if (x >= pos && x < pos + w) return i;
                pos += w;
            }
            return -1;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 4;
            for (int i = 0; i < labels.size(); i++) w += widthOf(i);
            return new Dimension(w, 24);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Theme.Palette p = Theme.p();
            int h = getHeight();
            g2.setColor(p.controlBg());
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), h, 9, 9));
            int pos = 2;
            FontMetrics fm = g2.getFontMetrics(getFont());
            for (int i = 0; i < labels.size(); i++) {
                int w = widthOf(i);
                if (i == selected) {
                    g2.setColor(new Color(0, 0, 0, Theme.isDark() ? 60 : 22));
                    g2.fill(new RoundRectangle2D.Double(pos, 2.5, w, h - 4, 7, 7));
                    g2.setColor(Theme.isDark() ? new Color(0x636366) : Color.WHITE);
                    g2.fill(new RoundRectangle2D.Double(pos, 2, w, h - 4, 7, 7));
                } else if (i == hover) {
                    g2.setColor(p.hover());
                    g2.fill(new RoundRectangle2D.Double(pos, 2, w, h - 4, 7, 7));
                }
                Icon icon = icons.get(i);
                String label = labels.get(i);
                int contentW = (icon != null ? icon.getIconWidth() : 0) + (label != null ? fm.stringWidth(label) : 0)
                        + (icon != null && label != null ? 5 : 0);
                int x = pos + (w - contentW) / 2;
                if (icon != null) {
                    icon.paintIcon(this, g2, x, (h - icon.getIconHeight()) / 2);
                    x += icon.getIconWidth() + 5;
                }
                if (label != null) {
                    g2.setFont(i == selected ? Theme.semibold(getFont().getSize2D()) : getFont());
                    g2.setColor(i == selected ? p.text() : p.text2());
                    g2.drawString(label, x, (h - fm.getHeight()) / 2 + fm.getAscent());
                }
                pos += w;
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ spinner

    /** An indeterminate activity indicator (rotating arc). */
    static final class Spinner implements Icon {
        private final int size;
        private final JComponent owner;
        private final Timer timer;
        private int angle;

        Spinner(JComponent owner, int size) {
            this.owner = owner;
            this.size = size;
            this.timer = new Timer(16, e -> {
                angle = (angle + 8) % 360;
                owner.repaint();
            });
        }

        void start() {
            if (!timer.isRunning()) timer.start();
        }

        void stop() {
            timer.stop();
        }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = smooth(g);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.alpha(Theme.p().text2(), 50));
            g2.draw(new Arc2D.Double(x + 1.5, y + 1.5, size - 3, size - 3, 0, 360, Arc2D.OPEN));
            g2.setColor(Theme.p().accent());
            g2.draw(new Arc2D.Double(x + 1.5, y + 1.5, size - 3, size - 3, -angle, 100, Arc2D.OPEN));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static void hoverHand(AbstractButton b) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
