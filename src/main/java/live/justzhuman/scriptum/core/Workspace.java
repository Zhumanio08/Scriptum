package live.justzhuman.scriptum.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A throw-away directory with {@code src/} and {@code classes/} where a program is built.
 * Nothing is ever written next to the user's Markdown file.
 */
public final class Workspace implements AutoCloseable {

    private final Path root;
    private boolean keep;

    private Workspace(Path root) {
        this.root = root;
    }

    public static Workspace create() throws IOException {
        return new Workspace(Files.createTempDirectory("scriptum-"));
    }

    public Path root() {
        return root;
    }

    public Path sources() {
        return root.resolve("src");
    }

    public Path classes() {
        return root.resolve("classes");
    }

    /** Keeps the directory on {@link #close()} (useful for debugging). */
    public void keep(boolean keep) {
        this.keep = keep;
    }

    @Override
    public void close() {
        if (!keep) deleteRecursively(root);
    }

    public static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best effort: the OS cleans the temp folder eventually.
        }
    }
}
