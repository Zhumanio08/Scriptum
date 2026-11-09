package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.Version;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the desktop app. */
public final class ScriptumApp {

    private ScriptumApp() {
    }

    public static void launch(String file) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", Version.NAME);
        System.setProperty("apple.awt.application.appearance", "system");

        SwingUtilities.invokeLater(() -> {
            Theme.installFonts();
            Theme.apply(Prefs.theme(), false);
            ToolTipManager.sharedInstance().setInitialDelay(600);

            MainWindow window = new MainWindow();
            window.show();
            if (file != null) {
                Path p = Path.of(file).toAbsolutePath();
                if (Files.isRegularFile(p)) window.open(p);
            }
            followSystemAppearance();
        });
    }

    /** In "Match System" mode, re-check the OS appearance every few seconds. */
    private static void followSystemAppearance() {
        Timer t = new Timer(4000, e -> {
            if (Theme.mode() != Theme.Mode.SYSTEM) return;
            Thread probe = new Thread(() -> {
                boolean dark = Theme.systemPrefersDark();
                if (dark != Theme.isDark()) SwingUtilities.invokeLater(() -> Theme.apply(Theme.Mode.SYSTEM, true));
            }, "scriptum-appearance");
            probe.setDaemon(true);
            probe.start();
        });
        t.start();
    }
}
