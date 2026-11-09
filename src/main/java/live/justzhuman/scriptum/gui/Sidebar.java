package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.Version;
import live.justzhuman.scriptum.core.Program;
import live.justzhuman.scriptum.core.SourceUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.List;

/** Source-list sidebar: the code blocks of the document, recent files and the theme switch. */
final class Sidebar extends JPanel {

    static final int WIDTH = 248;

    interface Actions {
        void goToLine(int markdownLine);

        void runUnit(SourceUnit unit);

        void openRecent(Path file);

        void forgetRecent(Path file);

        void setTheme(Theme.Mode mode);
    }

    final JPanel headerSlot = new JPanel(new BorderLayout());
    private final DefaultListModel<SourceUnit> unitModel = new DefaultListModel<>();
    private final JList<SourceUnit> units = new JList<>(unitModel);
    private final DefaultListModel<Path> recentModel = new DefaultListModel<>();
    private final JList<Path> recent = new JList<>(recentModel);
    private final JLabel blocksTitle = Widgets.section("CODE BLOCKS");
    private final JLabel recentTitle = Widgets.section("RECENT");
    private final JLabel empty;
    private final Widgets.Segmented theme = new Widgets.Segmented(34);
    private int hoverUnit = -1;
    private int hoverRecent = -1;

    Sidebar(Actions actions) {
        super(new BorderLayout());
        setOpaque(false);

        headerSlot.setOpaque(false);
        headerSlot.setBorder(new EmptyBorder(0, 18, 0, 10));
        headerSlot.setPreferredSize(new Dimension(WIDTH, 52));

        // ---------------------------------------------------------------- code blocks
        units.setOpaque(false);
        units.setCellRenderer(new UnitRenderer());
        units.setFixedCellHeight(48);
        units.setFixedCellWidth(WIDTH - 2);
        units.setFocusable(false);
        MouseAdapter unitMouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(units, e);
                if (i != hoverUnit) {
                    hoverUnit = i;
                    units.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverUnit = -1;
                units.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = rowAt(units, e);
                if (i < 0) return;
                SourceUnit u = unitModel.get(i);
                boolean onPlay = !u.entryPoints().isEmpty() && e.getX() > units.getWidth() - 40;
                if (onPlay || e.getClickCount() == 2) actions.runUnit(u);
                else actions.goToLine(u.blocks().get(0).fenceLine());
            }
        };
        units.addMouseListener(unitMouse);
        units.addMouseMotionListener(unitMouse);

        empty = Widgets.label("<html><div style='text-align:left'>No <b>```java</b> blocks yet.<br>"
                        + "Add a fenced block to the document<br>and it shows up here.</div></html>",
                Theme.ui(Font.PLAIN, 12f), () -> Theme.p().text3());
        empty.setBorder(new EmptyBorder(4, 18, 8, 12));

        // ---------------------------------------------------------------- recent files
        recent.setOpaque(false);
        recent.setCellRenderer(new RecentRenderer());
        recent.setFixedCellHeight(40);
        recent.setFixedCellWidth(WIDTH - 2);
        recent.setFocusable(false);
        MouseAdapter recentMouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(recent, e);
                if (i != hoverRecent) {
                    hoverRecent = i;
                    recent.setToolTipText(i >= 0 ? recentModel.get(i).toString() : null);
                    recent.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRecent = -1;
                recent.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int i = rowAt(recent, e);
                if (i < 0) return;
                Path p = recentModel.get(i);
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem forget = new JMenuItem("Remove from Recent");
                    forget.addActionListener(a -> actions.forgetRecent(p));
                    menu.add(forget);
                    menu.show(recent, e.getX(), e.getY());
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    actions.openRecent(p);
                }
            }
        };
        recent.addMouseListener(recentMouse);
        recent.addMouseMotionListener(recentMouse);

        JPanel content = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(WIDTH - 2, d.height);
            }
        };
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{blocksTitle, units, empty, recentTitle, recent}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(c);
        }
        content.add(Box.createVerticalGlue());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ---------------------------------------------------------------- footer
        theme.add(null, Icons.tinted("sun", 14, () -> Theme.p().text()), "Light")
                .add(null, Icons.tinted("auto", 14, () -> Theme.p().text()), "Match system")
                .add(null, Icons.tinted("moon", 14, () -> Theme.p().text()), "Dark");
        theme.onChange(i -> actions.setTheme(Theme.Mode.values()[new int[]{1, 0, 2}[i]]));
        JLabel version = Widgets.label("v" + Version.get(), Theme.ui(Font.PLAIN, 11f), () -> Theme.p().text3());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 16, 12, 16));
        JPanel themeWrap = new JPanel(new BorderLayout());
        themeWrap.setOpaque(false);
        themeWrap.add(theme, BorderLayout.WEST);
        footer.add(themeWrap, BorderLayout.WEST);
        footer.add(version, BorderLayout.EAST);

        add(headerSlot, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(WIDTH, 100));
        setProgram(null);
    }

    void setThemeMode(Theme.Mode mode) {
        theme.select(switch (mode) {
            case LIGHT -> 0;
            case SYSTEM -> 1;
            case DARK -> 2;
        });
    }

    void setProgram(Program program) {
        unitModel.clear();
        if (program != null) for (SourceUnit u : program.units()) unitModel.addElement(u);
        boolean none = unitModel.isEmpty();
        units.setVisible(!none);
        empty.setVisible(program != null && none);
        blocksTitle.setVisible(program != null);
        blocksTitle.setText(none ? "CODE BLOCKS" : "CODE BLOCKS  ·  " + unitModel.size());
        revalidate();
        repaint();
    }

    void setRecent(List<Path> files) {
        recentModel.clear();
        for (Path p : files) recentModel.addElement(p);
        recentTitle.setVisible(!files.isEmpty());
        recent.setVisible(!files.isEmpty());
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        MacWindow.paintRounded(this, g, Theme.p().sidebar());
        g.setColor(Theme.p().hairline());
        g.fillRect(getWidth() - 1, 0, 1, getHeight());
    }

    private static int rowAt(JList<?> list, MouseEvent e) {
        int i = list.locationToIndex(e.getPoint());
        if (i < 0) return -1;
        Rectangle r = list.getCellBounds(i, i);
        return r != null && r.contains(e.getPoint()) ? i : -1;
    }

    static String shortPath(Path dir) {
        if (dir == null) return "";
        String home = System.getProperty("user.home");
        String s = dir.toString();
        if (home != null && s.startsWith(home)) s = "~" + s.substring(home.length());
        return s.replace('\\', '/');
    }

    private static void paintRow(Graphics2D g2, int w, int h, boolean hover) {
        if (!hover) return;
        g2.setColor(Theme.p().hover());
        g2.fill(new RoundRectangle2D.Double(10, 2, w - 20, h - 4, 10, 10));
    }

    // ---------------------------------------------------------------- renderers

    private final class UnitRenderer extends JComponent implements ListCellRenderer<SourceUnit> {
        private SourceUnit unit;
        private int index;

        @Override
        public Component getListCellRendererComponent(JList<? extends SourceUnit> list, SourceUnit value, int idx,
                                                      boolean selected, boolean focus) {
            unit = value;
            index = idx;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Widgets.smooth(g);
            Theme.Palette p = Theme.p();
            int w = getWidth();
            int h = getHeight();
            boolean hover = index == hoverUnit;
            paintRow(g2, w, h, hover);

            Color kindColor = switch (unit.kind()) {
                case CLASS -> p.accent();
                case SNIPPET -> p.green();
                case COMPACT -> p.orange();
            };
            String iconName = switch (unit.kind()) {
                case CLASS -> "braces";
                case SNIPPET -> "bolt";
                case COMPACT -> "function";
            };
            int tile = 28;
            int tx = 20;
            int ty = (h - tile) / 2;
            g2.setColor(Theme.alpha(kindColor, Theme.isDark() ? 60 : 36));
            g2.fill(new RoundRectangle2D.Double(tx, ty, tile, tile, 9, 9));
            Icon icon = Icons.tinted(iconName, 15, () -> kindColor);
            icon.paintIcon(this, g2, tx + (tile - 15) / 2, ty + (tile - 15) / 2);

            boolean runnable = !unit.entryPoints().isEmpty();
            int textX = tx + tile + 10;
            int textW = w - textX - (runnable ? 44 : 16);
            Font titleFont = Theme.semibold(12.5f);
            FontMetrics fm = g2.getFontMetrics(titleFont);
            g2.setFont(titleFont);
            g2.setColor(p.text());
            g2.drawString(ActivityPill.ellipsize(unit.fileName() + ".java", fm, textW), textX, h / 2 - 2);

            Font subFont = Theme.ui(Font.PLAIN, 11f);
            FontMetrics sfm = g2.getFontMetrics(subFont);
            g2.setFont(subFont);
            g2.setColor(p.text2());
            String sub = "Line " + unit.blocks().get(0).fenceLine();
            if (unit.blocks().size() > 1) sub += " · " + unit.blocks().size() + " blocks";
            sub += " · " + (runnable ? (unit.kind() == SourceUnit.Kind.CLASS ? "main" : unit.kind().label().toLowerCase())
                    : (unit.blocks().stream().anyMatch(b -> b.isNoRun()) ? "norun" : "library"));
            g2.drawString(ActivityPill.ellipsize(sub, sfm, textW), textX, h / 2 + sfm.getAscent());

            if (runnable) {
                int bx = w - 40;
                int by = (h - 24) / 2;
                if (hover) {
                    g2.setColor(Theme.alpha(p.green(), Theme.isDark() ? 60 : 40));
                    g2.fill(new RoundRectangle2D.Double(bx, by, 24, 24, 24, 24));
                }
                Icon play = Icons.tinted("play", 12, () -> hover ? p.green() : p.text3());
                play.paintIcon(this, g2, bx + 6, by + 6);
            }
            g2.dispose();
        }
    }

    private final class RecentRenderer extends JComponent implements ListCellRenderer<Path> {
        private Path path;
        private int index;

        @Override
        public Component getListCellRendererComponent(JList<? extends Path> list, Path value, int idx,
                                                      boolean selected, boolean focus) {
            path = value;
            index = idx;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Widgets.smooth(g);
            Theme.Palette p = Theme.p();
            int w = getWidth();
            int h = getHeight();
            paintRow(g2, w, h, index == hoverRecent);
            Icon icon = Icons.tinted("doc", 16, () -> p.text2());
            icon.paintIcon(this, g2, 24, (h - 16) / 2);
            int textX = 50;
            int textW = w - textX - 16;
            Font f = Theme.ui(Font.PLAIN, 12.5f);
            FontMetrics fm = g2.getFontMetrics(f);
            g2.setFont(f);
            g2.setColor(p.text());
            g2.drawString(ActivityPill.ellipsize(path.getFileName().toString(), fm, textW), textX, h / 2 - 1);
            Font sf = Theme.ui(Font.PLAIN, 10.5f);
            FontMetrics sfm = g2.getFontMetrics(sf);
            g2.setFont(sf);
            g2.setColor(p.text3());
            g2.drawString(ActivityPill.ellipsize(shortPath(path.getParent()), sfm, textW), textX, h / 2 + sfm.getAscent());
            g2.dispose();
        }
    }

    /** Lets the window move the traffic lights / sidebar toggle here. */
    void setHeader(JComponent c) {
        headerSlot.removeAll();
        if (c != null) headerSlot.add(c, BorderLayout.CENTER);
        headerSlot.revalidate();
        headerSlot.repaint();
    }
}
