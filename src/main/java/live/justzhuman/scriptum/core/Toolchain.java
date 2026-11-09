package live.justzhuman.scriptum.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Locates the {@code java} and {@code javac} executables. */
public final class Toolchain {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private Toolchain() {
    }

    /** The {@code java} launcher used to run programs: the one running Scriptum, else JAVA_HOME, else PATH. */
    public static Path java() {
        Path p = find("java");
        return p != null ? p : Path.of(exe("java"));
    }

    /** The {@code javac} compiler, or {@code null} if no JDK can be found. */
    public static Path javac() {
        return find("javac");
    }

    /** Feature version of the runtime that will run the programs (e.g. 21). */
    public static int runtimeVersion() {
        return Runtime.version().feature();
    }

    private static Path find(String tool) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(System.getProperty("java.home"), "bin", exe(tool)));
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) candidates.add(Path.of(javaHome, "bin", exe(tool)));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) candidates.add(Path.of(dir, exe(tool)));
            }
        }
        for (Path c : candidates) {
            if (Files.isRegularFile(c) && Files.isExecutable(c)) return c;
        }
        return null;
    }

    private static String exe(String tool) {
        return WINDOWS ? tool + ".exe" : tool;
    }
}
