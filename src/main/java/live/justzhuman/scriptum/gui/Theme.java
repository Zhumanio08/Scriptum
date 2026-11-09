package live.justzhuman.scriptum.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Colors, fonts and light/dark switching. All custom painting reads colors from {@link #p()}. */
final class Theme {

    enum Mode { SYSTEM, LIGHT, DARK }

    static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Semantic colors of the app, in the spirit of macOS system colors. */
    record Palette(
            Color window, Color sidebar, Color console, Color codeBlock, Color pill,
            Color hairline, Color border, Color text, Color text2, Color text3,
            Color accent, Color onAccent, Color green, Color red, Color orange,
            Color hover, Color selection, Color controlBg, Color trafficInactive,
            Color errorLine, Color warningLine, Color shadow) {
    }

    private static final Palette LIGHT = new Palette(
            rgb(0xFFFFFF), rgb(0xF2F2F6), rgb(0xFAFAFC), rgb(0xF5F5FA), rgb(0xF1F1F5),
            rgb(0xE3E3E8), argb(0x24000000), rgb(0x1D1D1F), rgb(0x6E6E73), rgb(0xA1A1A6),
            rgb(0x5E5CE6), rgb(0xFFFFFF), rgb(0x1E9E46), rgb(0xE5383B), rgb(0xD97706),
            argb(0x0F000000), argb(0x245E5CE6), argb(0x0D000000), rgb(0xD5D5D8),
            argb(0x30FF3B30), argb(0x30FF9F0A), argb(0x47000000));

    private static final Palette DARK = new Palette(
            rgb(0x1E1E21), rgb(0x28282C), rgb(0x19191B), rgb(0x232328), rgb(0x2A2A2F),
            rgb(0x37373C), argb(0x33FFFFFF), rgb(0xECECEE), rgb(0x9C9CA1), rgb(0x6C6C71),
            rgb(0x7D7AFF), rgb(0xFFFFFF), rgb(0x32D74B), rgb(0xFF6961), rgb(0xFFB340),
            argb(0x14FFFFFF), argb(0x387D7AFF), argb(0x14FFFFFF), rgb(0x4A4A4E),
            argb(0x40FF453A), argb(0x40FF9F0A), argb(0x80000000));

    private static Mode mode = Mode.SYSTEM;
    private static boolean dark;
    private static final List<Runnable> listeners = new ArrayList<>();

    private Theme() {
    }

    static Palette p() {
        return dark ? DARK : LIGHT;
    }

    static boolean isDark() {
        return dark;
    }

    static Mode mode() {
        return mode;
    }

    /** Called after every theme switch (on the EDT). */
    static void onChange(Runnable r) {
        listeners.add(r);
    }

    static void installFonts() {
        FlatJetBrainsMonoFont.install();
        if (!MAC) {
            FlatInterFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
            FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);
        }
        FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
    }

    static Font mono(float size) {
        return new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 1).deriveFont(size);
    }

    static Font ui(int style, float size) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    static Font semibold(float size) {
        if (MAC) return ui(Font.BOLD, size);
        return new Font(FlatInterFont.FAMILY_SEMIBOLD, Font.PLAIN, 1).deriveFont(size);
    }

    static void apply(Mode newMode, boolean animate) {
        mode = newMode;
        boolean newDark = switch (newMode) {
            case LIGHT -> false;
            case DARK -> true;
            case SYSTEM -> systemPrefersDark();
        };
        boolean changed = newDark != dark || !(UIManager.getLookAndFeel() instanceof FlatLaf);
        dark = newDark;
        if (!changed) return;

        if (animate) FlatAnimatedLafChange.showSnapshot();
        Palette p = p();
        Map<String, String> extra = new HashMap<>();
        extra.put("@accentColor", hex(p.accent()));
        FlatLaf.setGlobalExtraDefaults(extra);
        if (dark) FlatMacDarkLaf.setup();
        else FlatMacLightLaf.setup();
        customizeDefaults(p);
        if (animate) {
            FlatLaf.updateUI();
            for (Runnable r : listeners) r.run();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
    }

    private static void customizeDefaults(Palette p) {
        if (!MAC) UIManager.put("defaultFont", new Font(FlatInterFont.FAMILY, Font.PLAIN, 13));
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("SplitPane.background", p.hairline());
        UIManager.put("PopupMenu.borderCornerRadius", 10);
        UIManager.put("Popup.borderCornerRadius", 10);
        UIManager.put("MenuItem.selectionArc", 6);
        UIManager.put("MenuItem.selectionInsets", new java.awt.Insets(0, 4, 0, 4));
        UIManager.put("ToolTip.background", dark ? new Color(0x3A3A3E) : new Color(0xFFFFFF));
        UIManager.put("List.selectionArc", 8);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("OptionPane.buttonMinimumWidth", 90);
    }

    /** Detects a dark system appearance on macOS, Windows and GNOME/KDE. */
    static boolean systemPrefersDark() {
        try {
            if (MAC) {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase(Locale.ROOT).contains("dark");
            }
            if (WINDOWS) {
                String out = run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme");
                return out.contains("0x0");
            }
            String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (scheme.contains("dark")) return true;
            if (scheme.contains("light")) return false;
            String gtk = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
            if (gtk.toLowerCase(Locale.ROOT).contains("dark")) return true;
            String kde = run("kreadconfig5", "--group", "General", "--key", "ColorScheme");
            if (kde.isBlank()) kde = run("kreadconfig6", "--group", "General", "--key", "ColorScheme");
            return kde.toLowerCase(Locale.ROOT).contains("dark");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color rgb(int v) {
        return new Color(v);
    }

    private static Color argb(int v) {
        return new Color(v, true);
    }
}
