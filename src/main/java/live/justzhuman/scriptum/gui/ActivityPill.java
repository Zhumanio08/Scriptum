package live.justzhuman.scriptum.gui;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/** The Xcode-style activity view in the middle of the toolbar: what Scriptum is doing right now. */
final class ActivityPill extends JComponent {

    private final Widgets.Spinner spinner = new Widgets.Spinner(this, 14);
    private final Icon appIcon = Icons.app(16);
    private final Timer clock;
    private Icon icon = appIcon;
    private String text = "Scriptum";
    private String trailing = "";
    private boolean busy;
    private long busySince;
    private Color textColor;
    private float shimmer;

    ActivityPill() {
        setOpaque(false);
        setFont(Theme.ui(Font.PLAIN, 12.5f));
        clock = new Timer(50, e -> {
            shimmer = (shimmer + 0.012f) % 1.4f;
            long ms = System.currentTimeMillis() - busySince;
            trailing = ms < 1000 ? "" : formatSeconds(ms);
            repaint();
        });
    }

    void idle(String message) {
        stopBusy();
        icon = appIcon;
        text = message;
        trailing = "";
        textColor = null;
        repaint();
    }

    void busy(String message) {
        if (!busy) busySince = System.currentTimeMillis();
        busy = true;
        icon = spinner;
        text = message;
        textColor = null;
        spinner.start();
        clock.start();
        repaint();
    }

    void done(Icon statusIcon, String message, String trail, Color color) {
        stopBusy();
        icon = statusIcon;
        text = message;
        trailing = trail == null ? "" : trail;
        textColor = color;
        repaint();
    }

    private void stopBusy() {
        busy = false;
        spinner.stop();
        clock.stop();
    }

    static String formatSeconds(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format("%.2f s", ms / 1000.0);
        return String.format("%d:%02d", ms / 60_000, (ms / 1000) % 60);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(380, 30);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(180, 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Widgets.smooth(g);
        Theme.Palette p = Theme.p();
        int w = getWidth();
        int h = getHeight();
        Shape pill = new RoundRectangle2D.Double(0, 0, w, h, 10, 10);
        g2.setColor(p.pill());
        g2.fill(pill);
        g2.setColor(Theme.isDark() ? new Color(255, 255, 255, 14) : new Color(0, 0, 0, 12));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 10, 10));

        if (busy) {
            // A soft accent shimmer travelling along the bottom edge.
            g2.setClip(pill);
            float cx = (shimmer - 0.2f) * w;
            Color a = Theme.alpha(p.accent(), 0);
            Color b = Theme.alpha(p.accent(), 200);
            g2.setPaint(new GradientPaint(cx - 90, 0, a, cx, 0, b, true));
            g2.fillRect(0, h - 2, w, 2);
            g2.setClip(null);
        }

        FontMetrics fm = g2.getFontMetrics(getFont());
        int x = 10;
        if (icon != null) {
            icon.paintIcon(this, g2, x, (h - icon.getIconHeight()) / 2);
            x += icon.getIconWidth() + 8;
        }
        int trailingW = trailing.isEmpty() ? 0 : fm.stringWidth(trailing) + 12;
        g2.setFont(getFont());
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();
        if (!trailing.isEmpty()) {
            g2.setColor(p.text2());
            g2.drawString(trailing, w - 10 - fm.stringWidth(trailing), baseline);
        }
        g2.setColor(textColor != null ? textColor : p.text());
        g2.drawString(ellipsize(text, fm, w - x - 10 - trailingW), x, baseline);
        g2.dispose();
    }

    static String ellipsize(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > max) end--;
        return s.substring(0, end) + dots;
    }
}
