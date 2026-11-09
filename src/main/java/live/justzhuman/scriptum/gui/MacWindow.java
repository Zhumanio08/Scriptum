package live.justzhuman.scriptum.gui;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * A window that looks like a macOS app window on every platform.
 *
 * <ul>
 *   <li>macOS: the native window with a transparent, full-size title bar (real traffic lights);</li>
 *   <li>Windows / Linux: an undecorated window with rounded corners, a soft drop shadow,
 *       hand-drawn traffic lights, dragging, double-click to zoom and edge resizing.</li>
 * </ul>
 * Set {@code -Dscriptum.nativeFrame=true} (or {@code SCRIPTUM_NATIVE_FRAME=1}) to use the
 * system title bar instead, e.g. on tiling window managers.
 */
final class MacWindow extends JFrame {

    static final int RADIUS = 12;
    private static final int SHADOW = 26;      // margin around the body for the shadow
    private static final int SHADOW_DY = 8;    // the shadow falls a bit downwards
    private static final int GRIP = 8;         // resize hit area outside the body
    private static final int FALLBACK_GRIP = 5;

    final boolean customFrame;
    private final boolean translucent;
    private final Surface surface;
    private final JPanel root;
    private boolean zoomed;
    private boolean active = true;

    MacWindow() {
        boolean nativeFrame = Boolean.getBoolean("scriptum.nativeFrame") || System.getenv("SCRIPTUM_NATIVE_FRAME") != null;
        customFrame = !Theme.MAC && !nativeFrame;
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        translucent = customFrame && gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
                && !Boolean.getBoolean("scriptum.noShadow");

        surface = new Surface();
        root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                paintShadow((Graphics2D) g);
            }
        };
        root.setOpaque(false);
        root.add(surface, BorderLayout.CENTER);

        if (customFrame) {
            setUndecorated(true);
            if (translucent) setBackground(new Color(0, 0, 0, 0));
            updateMargins();
            Resizer resizer = new Resizer();
            root.addMouseListener(resizer);
            root.addMouseMotionListener(resizer);
        } else if (Theme.MAC) {
            getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
            getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");
        }
        setContentPane(root);
        getRootPane().setOpaque(!translucent);
        if (translucent) {
            ((JComponent) getLayeredPane()).setOpaque(false);
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                active = true;
                root.repaint();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                active = false;
                root.repaint();
            }
        });
        addWindowStateListener(e -> {
            boolean max = (e.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            if (max != zoomed) {
                zoomed = max;
                updateMargins();
            }
        });
        try {
            setIconImages(Icons.windowIcons());
        } catch (RuntimeException ignored) {
            // icon is cosmetic
        }
    }

    /** The rounded panel that holds the actual UI. */
    JPanel surface() {
        return surface;
    }

    boolean isActiveWindow() {
        return active || !customFrame;
    }

    int cornerRadius() {
        return customFrame && !zoomed ? RADIUS : 0;
    }

    /** Insets between the window bounds and the visible body (the shadow area). */
    Insets margins() {
        if (!translucent || zoomed) return new Insets(0, 0, 0, 0);
        return new Insets(SHADOW - SHADOW_DY / 2, SHADOW, SHADOW + SHADOW_DY / 2, SHADOW);
    }

    /** Sets the size of the visible window body (without the shadow margin). */
    void setBodySize(int w, int h) {
        Insets m = margins();
        setSize(w + m.left + m.right, h + m.top + m.bottom);
    }

    Rectangle bodyBounds() {
        Insets m = margins();
        return new Rectangle(getX() + m.left, getY() + m.top, getWidth() - m.left - m.right, getHeight() - m.top - m.bottom);
    }

    void setBodyBounds(Rectangle r) {
        Insets m = margins();
        setBounds(r.x - m.left, r.y - m.top, r.width + m.left + m.right, r.height + m.top + m.bottom);
    }

    // ------------------------------------------------------------------ window actions

    void minimizeWindow() {
        setExtendedState(getExtendedState() | Frame.ICONIFIED);
    }

    void toggleZoom() {
        if (!customFrame) {
            setExtendedState(getExtendedState() ^ Frame.MAXIMIZED_BOTH);
            return;
        }
        if ((getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            setExtendedState(Frame.NORMAL);
        } else {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            Rectangle screen = gc.getBounds();
            Insets si = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            setMaximizedBounds(new Rectangle(screen.x + si.left, screen.y + si.top,
                    screen.width - si.left - si.right, screen.height - si.top - si.bottom));
            setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    boolean isZoomed() {
        return zoomed;
    }

    /** Lets the user drag the window by this component (and double-click it to zoom). */
    void installDragArea(JComponent c) {
        MouseAdapter drag = new MouseAdapter() {
            private Point offset;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (e.getClickCount() == 2) {
                    toggleZoom();
                    offset = null;
                    return;
                }
                Point p = e.getLocationOnScreen();
                offset = new Point(p.x - getX(), p.y - getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (offset == null || !customFrame) return;
                Point p = e.getLocationOnScreen();
                if (zoomed) {
                    // Un-zoom and keep the cursor at the same relative position.
                    float rel = (float) offset.x / Math.max(1, getWidth());
                    setExtendedState(Frame.NORMAL);
                    offset = new Point(Math.round(getWidth() * rel), offset.y + margins().top);
                }
                setLocation(p.x - offset.x, p.y - offset.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                offset = null;
            }
        };
        c.addMouseListener(drag);
        c.addMouseMotionListener(drag);
    }

    private void updateMargins() {
        Insets m = margins();
        if (customFrame && !translucent && !zoomed) {
            // Without per-pixel translucency: hard rounded shape + a thin band for resizing.
            m = new Insets(FALLBACK_GRIP, FALLBACK_GRIP, FALLBACK_GRIP, FALLBACK_GRIP);
        }
        root.setBorder(new EmptyBorder(m));
        if (customFrame && !translucent) {
            try {
                setShape(zoomed ? null : new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), RADIUS * 2, RADIUS * 2));
            } catch (UnsupportedOperationException ignored) {
                // shaped windows not supported: keep square corners
            }
        }
        root.revalidate();
        root.repaint();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (customFrame && !translucent && !zoomed) {
            try {
                setShape(new RoundRectangle2D.Double(0, 0, width, height, RADIUS * 2, RADIUS * 2));
            } catch (UnsupportedOperationException ignored) {
                // see updateMargins
            }
        }
    }

    // ------------------------------------------------------------------ painting

    private static BufferedImage shadowCache;
    private static boolean shadowCacheDark;

    private void paintShadow(Graphics2D g) {
        if (!translucent || zoomed) return;
        Rectangle body = surface.getBounds();
        Graphics2D g2 = (Graphics2D) g.create();
        // Nearly invisible fill so the resize band receives mouse events everywhere.
        g2.setComposite(AlphaComposite.Src);
        g2.setColor(new Color(0, 0, 0, 1));
        Area grip = new Area(new Rectangle(body.x - GRIP, body.y - GRIP, body.width + GRIP * 2, body.height + GRIP * 2));
        grip.subtract(new Area(body));
        g2.fill(grip);
        g2.setComposite(AlphaComposite.SrcOver);

        BufferedImage img = shadowImage();
        int blur = SHADOW;
        int c = blur + RADIUS;
        int x = body.x;
        int y = body.y + SHADOW_DY;
        int w = body.width;
        int h = body.height;
        int s = img.getWidth();
        int mid = c;
        // 9-slice: corners, then stretched edges.
        draw(g2, img, x - blur, y - blur, c, c, 0, 0, c, c);
        draw(g2, img, x + w - RADIUS, y - blur, c, c, s - c, 0, s, c);
        draw(g2, img, x - blur, y + h - RADIUS, c, c, 0, s - c, c, s);
        draw(g2, img, x + w - RADIUS, y + h - RADIUS, c, c, s - c, s - c, s, s);
        draw(g2, img, x + RADIUS, y - blur, w - 2 * RADIUS, c, mid, 0, mid + 1, c);
        draw(g2, img, x + RADIUS, y + h - RADIUS, w - 2 * RADIUS, c, mid, s - c, mid + 1, s);
        draw(g2, img, x - blur, y + RADIUS, c, h - 2 * RADIUS, 0, mid, c, mid + 1);
        draw(g2, img, x + w - RADIUS, y + RADIUS, c, h - 2 * RADIUS, s - c, mid, s, mid + 1);
        g2.dispose();
    }

    private static void draw(Graphics2D g, BufferedImage img, int dx, int dy, int dw, int dh, int sx1, int sy1, int sx2, int sy2) {
        if (dw <= 0 || dh <= 0) return;
        g.drawImage(img, dx, dy, dx + dw, dy + dh, sx1, sy1, sx2, sy2, null);
    }

    private static BufferedImage shadowImage() {
        boolean dark = Theme.isDark();
        if (shadowCache != null && shadowCacheDark == dark) return shadowCache;
        int blur = SHADOW;
        int size = 2 * (blur + RADIUS) + 1;
        int pad = blur;
        BufferedImage src = new BufferedImage(size + 2 * pad, size + 2 * pad, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.p().shadow());
        g.fill(new RoundRectangle2D.Double(pad + blur, pad + blur, size - 2 * blur, size - 2 * blur, RADIUS * 2, RADIUS * 2));
        g.dispose();
        BufferedImage blurred = gaussian(src, blur / 2);
        shadowCache = blurred.getSubimage(pad, pad, size, size);
        shadowCacheDark = dark;
        return shadowCache;
    }

    private static BufferedImage gaussian(BufferedImage src, int radius) {
        int n = radius * 2 + 1;
        float[] k = new float[n];
        float sigma = radius / 2f;
        float sum = 0;
        for (int i = 0; i < n; i++) {
            float x = i - radius;
            k[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < n; i++) k[i] /= sum;
        ConvolveOp h = new ConvolveOp(new Kernel(n, 1, k), ConvolveOp.EDGE_NO_OP, null);
        ConvolveOp v = new ConvolveOp(new Kernel(1, n, k), ConvolveOp.EDGE_NO_OP, null);
        return v.filter(h.filter(src, null), null);
    }

    /**
     * Fills {@code c}'s background with {@code color}, clipped to the window's rounded body,
     * so panels that touch a window corner keep the corner round.
     */
    static void paintRounded(JComponent c, Graphics g, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        Surface s = (Surface) SwingUtilities.getAncestorOfClass(Surface.class, c);
        Shape shape = new Rectangle(0, 0, c.getWidth(), c.getHeight());
        if (s != null && s.radius() > 0) {
            Point p = SwingUtilities.convertPoint(c, 0, 0, s);
            Shape body = s.bodyShape();
            Area a = new Area(body);
            a.transform(java.awt.geom.AffineTransform.getTranslateInstance(-p.x, -p.y));
            a.intersect(new Area(shape));
            shape = a;
        }
        g2.fill(shape);
        g2.dispose();
    }

    /** The rounded window body. Children that touch the edges must be non-opaque. */
    final class Surface extends JPanel {

        Surface() {
            super(new BorderLayout());
            setOpaque(false);
        }

        int radius() {
            return cornerRadius();
        }

        Shape bodyShape() {
            int r = radius();
            if (r == 0) return new Rectangle(0, 0, getWidth(), getHeight());
            return new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), r * 2, r * 2);
        }

        @Override
        public Insets getInsets() {
            // Keep children off the 1px outline drawn by paint().
            return customFrame && !zoomed ? new Insets(1, 1, 1, 1) : new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.p().window());
            g2.fill(bodyShape());
            g2.dispose();
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (!customFrame || zoomed) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int r = radius();
            g2.setColor(Theme.p().border());
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, r * 2, r * 2));
            if (Theme.isDark()) {
                // macOS dark windows have a faint light inner edge.
                g2.setColor(new Color(255, 255, 255, 18));
                g2.draw(new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 3, r * 2 - 2, r * 2 - 2));
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ resizing

    private final class Resizer extends MouseAdapter {
        private int edge;           // bitmask of N=1, S=2, W=4, E=8
        private Point start;
        private Rectangle startBounds;

        private int edgeAt(Point p) {
            if (zoomed) return 0;
            Rectangle b = surface.getBounds();
            int band = translucent ? GRIP : FALLBACK_GRIP;
            Rectangle outer = new Rectangle(b.x - band, b.y - band, b.width + 2 * band, b.height + 2 * band);
            if (!outer.contains(p) || b.contains(p)) return 0;
            int corner = RADIUS + 4;
            int e = 0;
            if (p.y < b.y + corner && p.y >= outer.y && (p.y < b.y || p.x < b.x + corner || p.x > b.x + b.width - corner)) e |= 1;
            if (p.y > b.y + b.height - corner && (p.y >= b.y + b.height || p.x < b.x + corner || p.x > b.x + b.width - corner)) e |= 2;
            if (p.x < b.x + corner && (p.x < b.x || p.y < b.y + corner || p.y > b.y + b.height - corner)) e |= 4;
            if (p.x > b.x + b.width - corner && (p.x >= b.x + b.width || p.y < b.y + corner || p.y > b.y + b.height - corner)) e |= 8;
            return e;
        }

        private Cursor cursorFor(int e) {
            return switch (e) {
                case 1 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case 2 -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case 4 -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case 8 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case 5 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case 9 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case 6 -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case 10 -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default -> Cursor.getDefaultCursor();
            };
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            root.setCursor(cursorFor(edgeAt(e.getPoint())));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (start == null) root.setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mousePressed(MouseEvent e) {
            edge = edgeAt(e.getPoint());
            if (edge == 0) return;
            start = e.getLocationOnScreen();
            startBounds = getBounds();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (start == null || edge == 0) return;
            Point p = e.getLocationOnScreen();
            int dx = p.x - start.x;
            int dy = p.y - start.y;
            Rectangle r = new Rectangle(startBounds);
            Dimension min = getMinimumSize();
            if ((edge & 8) != 0) r.width = Math.max(min.width, startBounds.width + dx);
            if ((edge & 2) != 0) r.height = Math.max(min.height, startBounds.height + dy);
            if ((edge & 4) != 0) {
                r.width = Math.max(min.width, startBounds.width - dx);
                r.x = startBounds.x + startBounds.width - r.width;
            }
            if ((edge & 1) != 0) {
                r.height = Math.max(min.height, startBounds.height - dy);
                r.y = startBounds.y + startBounds.height - r.height;
            }
            setBounds(r);
            validate();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            start = null;
            edge = 0;
        }
    }

    /** Hand-drawn traffic lights for non-macOS platforms. */
    static Component trafficLights(MacWindow w) {
        return new TrafficLights(w);
    }
}
