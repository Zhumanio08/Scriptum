package live.justzhuman.scriptum.gui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** The close / minimize / zoom buttons, drawn like on macOS (glyphs appear on hover). */
final class TrafficLights extends JComponent {

    private static final int D = 12;      // button diameter
    private static final int GAP = 8;     // gap between buttons
    private static final Color[] FILL = {new Color(0xFF5F57), new Color(0xFEBC2E), new Color(0x28C840)};
    private static final Color[] RIM = {new Color(0xE2463F), new Color(0xE1A116), new Color(0x1AAB29)};
    private static final Color[] GLYPH = {new Color(0x8C1A10), new Color(0x985712), new Color(0x0B650D)};

    private final MacWindow window;
    private boolean hover;
    private int pressed = -1;

    TrafficLights(MacWindow window) {
        this.window = window;
        setOpaque(false);
        setToolTipText(null);
        MouseAdapter m = new MouseAdapter() {
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
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressed = buttonAt(e.getX(), e.getY());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int b = buttonAt(e.getX(), e.getY());
                int p = pressed;
                pressed = -1;
                repaint();
                if (b != p || b < 0) return;
                switch (b) {
                    case 0 -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    case 1 -> window.minimizeWindow();
                    case 2 -> window.toggleZoom();
                    default -> { }
                }
            }
        };
        addMouseListener(m);
    }

    private int buttonAt(int x, int y) {
        int top = (getHeight() - D) / 2;
        if (y < top - 4 || y > top + D + 4) return -1;
        for (int i = 0; i < 3; i++) {
            int left = i * (D + GAP);
            if (x >= left - GAP / 2 && x <= left + D + GAP / 2) return i;
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(3 * D + 2 * GAP, D + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int top = (getHeight() - D) / 2;
        boolean active = window.isActiveWindow() || hover;
        for (int i = 0; i < 3; i++) {
            double x = i * (D + GAP);
            Ellipse2D circle = new Ellipse2D.Double(x, top, D, D);
            Color fill = active ? FILL[i] : Theme.p().trafficInactive();
            if (pressed == i) fill = fill.darker();
            g2.setColor(fill);
            g2.fill(circle);
            g2.setColor(active ? RIM[i] : Theme.mix(Theme.p().trafficInactive(), Color.BLACK, 0.08f));
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new Ellipse2D.Double(x + 0.4, top + 0.4, D - 0.8, D - 0.8));
            if (hover) paintGlyph(g2, i, x + D / 2.0, top + D / 2.0);
        }
        g2.dispose();
    }

    private static void paintGlyph(Graphics2D g, int i, double cx, double cy) {
        g.setColor(GLYPH[i]);
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double r = 2.9;
        switch (i) {
            case 0 -> {
                g.draw(new Line2D.Double(cx - r + 0.4, cy - r + 0.4, cx + r - 0.4, cy + r - 0.4));
                g.draw(new Line2D.Double(cx - r + 0.4, cy + r - 0.4, cx + r - 0.4, cy - r + 0.4));
            }
            case 1 -> g.draw(new Line2D.Double(cx - r - 0.2, cy, cx + r + 0.2, cy));
            case 2 -> {
                // Two small triangles pointing outwards (macOS full-screen glyph).
                Path2D a = new Path2D.Double();
                a.moveTo(cx - 3.2, cy + 0.6);
                a.lineTo(cx - 3.2, cy - 3.2);
                a.lineTo(cx + 0.6, cy - 3.2);
                a.closePath();
                Path2D b = new Path2D.Double();
                b.moveTo(cx + 3.2, cy - 0.6);
                b.lineTo(cx + 3.2, cy + 3.2);
                b.lineTo(cx - 0.6, cy + 3.2);
                b.closePath();
                g.fill(a);
                g.fill(b);
            }
            default -> { }
        }
    }
}
