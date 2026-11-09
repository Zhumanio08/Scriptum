package live.justzhuman.scriptum.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGUtils;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Image;
import java.util.List;
import java.util.function.Supplier;

/** SVG icons. Monochrome icons are tinted at paint time, so they follow theme changes. */
final class Icons {

    private static final String DIR = "live/justzhuman/scriptum/icons/";
    static final String APP_ICON = "/live/justzhuman/scriptum/app-icon.svg";

    private Icons() {
    }

    /** A monochrome icon tinted with the primary text color. */
    static FlatSVGIcon get(String name, int size) {
        return tinted(name, size, () -> Theme.p().text());
    }

    /** A monochrome icon tinted with a color that is looked up on every paint. */
    static FlatSVGIcon tinted(String name, int size, Supplier<Color> color) {
        FlatSVGIcon icon = new FlatSVGIcon(DIR + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
            Color t = color.get();
            return new Color(t.getRed(), t.getGreen(), t.getBlue(), c.getAlpha() * t.getAlpha() / 255);
        }));
        return icon;
    }

    /** A full-color icon (status badges). */
    static Icon colored(String name, int size) {
        return new FlatSVGIcon(DIR + name + ".svg", size, size);
    }

    static Icon app(int size) {
        return new FlatSVGIcon(APP_ICON.substring(1), size, size);
    }

    static List<Image> windowIcons() {
        return FlatSVGUtils.createWindowIconImages(APP_ICON);
    }
}
