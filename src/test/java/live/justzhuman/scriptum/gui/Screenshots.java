package live.justzhuman.scriptum.gui;

import javax.imageio.ImageIO;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders the desktop app off-screen into PNG files for the README.
 * Not a unit test. Run after {@code mvn package}:
 * <pre>
 * java -cp target/scriptum.jar:target/test-classes live.justzhuman.scriptum.gui.Screenshots docs/images
 * </pre>
 */
public final class Screenshots {

    private static final double SCALE = 2.0;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/images");
        Files.createDirectories(out);
        String tour = Files.readString(Path.of("examples/tour.md"), StandardCharsets.UTF_8);
        String broken = Files.readString(Path.of("examples/broken.md"), StandardCharsets.UTF_8);
        SwingUtilities.invokeAndWait(Theme::installFonts);

        shot(out.resolve("app-light.png"), Theme.Mode.LIGHT, tour, "tour.md", true, false);
        shot(out.resolve("app-dark.png"), Theme.Mode.DARK, tour, "tour.md", true, false);
        shot(out.resolve("problems-light.png"), Theme.Mode.LIGHT, broken, "broken.md", true, true);
        shot(out.resolve("problems-dark.png"), Theme.Mode.DARK, broken, "broken.md", true, true);
        shot(out.resolve("welcome-light.png"), Theme.Mode.LIGHT, null, null, false, false);
        shot(out.resolve("welcome-dark.png"), Theme.Mode.DARK, null, null, false, false);
        System.exit(0);
    }

    private static void shot(Path file, Theme.Mode mode, String md, String name, boolean run, boolean problems) throws Exception {
        MainWindow[] holder = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            Theme.apply(mode, false);
            javax.swing.SwingUtilities.updateComponentTreeUI(new javax.swing.JPanel());
            MainWindow w = new MainWindow();
            holder[0] = w;
            MacWindow f = w.frame();
            Rectangle b = f.bodyBounds();
            f.setBodyBounds(new Rectangle(b.x, b.y, 1240, 760));
            f.addNotify();
            f.validate();
            if (md != null) w.loadForScreenshot(md, name);
            else w.showWelcomeForScreenshot();
            f.validate();
        });
        MainWindow w = holder[0];
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            var split = (javax.swing.JSplitPane) SwingUtilities.getAncestorOfClass(javax.swing.JSplitPane.class, w.console());
            if (split != null) split.setDividerLocation(problems ? 0.62 : 0.6);
            w.frame().validate();
            if (run) w.runForScreenshot();
        });
        if (run) {
            long until = System.currentTimeMillis() + 20_000;
            boolean[] busy = {true};
            while (busy[0] && System.currentTimeMillis() < until) {
                Thread.sleep(200);
                SwingUtilities.invokeAndWait(() -> busy[0] = w.running());
            }
            Thread.sleep(300);
        }
        SwingUtilities.invokeAndWait(() -> {
            w.console().flushNow();
            if (!problems) w.editor().area.setCaretPosition(0);
            w.editor().area.getCaret().setVisible(false);
            w.frame().validate();
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            MacWindow f = w.frame();
            BufferedImage img = new BufferedImage((int) (f.getWidth() * SCALE), (int) (f.getHeight() * SCALE), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(SCALE, SCALE);
            RepaintManager.currentManager(f.getRootPane()).setDoubleBufferingEnabled(false);
            f.getRootPane().paint(g);
            RepaintManager.currentManager(f.getRootPane()).setDoubleBufferingEnabled(true);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(file.toString()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            f.dispose();
        });
        System.out.println("wrote " + file);
    }
}
