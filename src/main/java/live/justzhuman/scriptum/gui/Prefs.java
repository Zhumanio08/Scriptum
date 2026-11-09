package live.justzhuman.scriptum.gui;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Small persistent settings: theme, window bounds, layout and recent files. */
final class Prefs {

    private static final Preferences P = Preferences.userRoot().node("live/justzhuman/scriptum");
    private static final int MAX_RECENT = 8;

    private Prefs() {
    }

    static Theme.Mode theme() {
        try {
            return Theme.Mode.valueOf(P.get("theme", Theme.Mode.SYSTEM.name()));
        } catch (IllegalArgumentException e) {
            return Theme.Mode.SYSTEM;
        }
    }

    static void theme(Theme.Mode m) {
        P.put("theme", m.name());
    }

    static Rectangle bounds() {
        int w = P.getInt("w", 0);
        int h = P.getInt("h", 0);
        if (w < 200 || h < 200) return null;
        return new Rectangle(P.getInt("x", 0), P.getInt("y", 0), w, h);
    }

    static void bounds(Rectangle r) {
        P.putInt("x", r.x);
        P.putInt("y", r.y);
        P.putInt("w", r.width);
        P.putInt("h", r.height);
    }

    static boolean sidebar() {
        return P.getBoolean("sidebar", true);
    }

    static void sidebar(boolean visible) {
        P.putBoolean("sidebar", visible);
    }

    static double consoleRatio() {
        return P.getDouble("console", 0.68);
    }

    static void consoleRatio(double r) {
        P.putDouble("console", r);
    }

    static List<Path> recent() {
        List<Path> out = new ArrayList<>();
        for (String s : P.get("recent", "").split("\n")) {
            if (s.isBlank()) continue;
            try {
                Path p = Path.of(s);
                if (Files.isRegularFile(p)) out.add(p);
            } catch (RuntimeException ignored) {
                // invalid path on this OS
            }
        }
        return out;
    }

    static void addRecent(Path file) {
        List<Path> list = recent();
        Path abs = file.toAbsolutePath().normalize();
        list.remove(abs);
        list.add(0, abs);
        saveRecent(list);
    }

    static void removeRecent(Path file) {
        List<Path> list = recent();
        list.remove(file);
        saveRecent(list);
    }

    private static void saveRecent(List<Path> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(MAX_RECENT, list.size()); i++) b.append(list.get(i)).append('\n');
        P.put("recent", b.toString());
    }
}
