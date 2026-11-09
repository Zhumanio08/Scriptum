package live.justzhuman.scriptum;

/** Application name and version (the version comes from the JAR manifest). */
public final class Version {

    public static final String NAME = "Scriptum";
    public static final String TAGLINE = "Run the Java inside your Markdown";

    private Version() {
    }

    public static String get() {
        String v = Version.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
