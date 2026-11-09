package live.justzhuman.scriptum;

import live.justzhuman.scriptum.cli.Cli;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/**
 * Entry point of the JAR.
 *
 * <ul>
 *   <li>{@code scriptum file.md ...} — command-line mode;</li>
 *   <li>{@code scriptum gui [file.md]} — desktop app;</li>
 *   <li>no arguments: the desktop app when started by double-click, help when started from a terminal.</li>
 * </ul>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("gui") || args[0].equals("--gui"))) {
            startGui(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length == 0) {
            if (!GraphicsEnvironment.isHeadless() && !startedFromTerminal()) {
                startGui(args);
                return;
            }
            System.out.println(Cli.usage());
            System.exit(Cli.USAGE);
        }
        System.exit(Cli.run(args));
    }

    private static void startGui(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("scriptum: no display available, the desktop app cannot start. Use 'scriptum <file.md>'.");
            System.exit(Cli.USAGE);
        }
        // Loaded reflectively-free but lazily, so the CLI never touches Swing.
        live.justzhuman.scriptum.gui.ScriptumApp.launch(args.length > 0 ? args[0] : null);
    }

    private static boolean startedFromTerminal() {
        java.io.Console c = System.console();
        if (c == null) return false;
        try {
            return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(c);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
