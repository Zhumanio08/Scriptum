package live.justzhuman.scriptum.gui;

import live.justzhuman.scriptum.Version;
import live.justzhuman.scriptum.core.JavaBuilder;
import live.justzhuman.scriptum.core.Problem;
import live.justzhuman.scriptum.core.Program;
import live.justzhuman.scriptum.core.ProgramRunner;
import live.justzhuman.scriptum.core.SourceUnit;
import org.fife.ui.rtextarea.RTATextTransferHandler;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** The Scriptum desktop window: wires the sidebar, editor, toolbar and console to the core. */
final class MainWindow {

    private static final int MENU = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    private static final String MOD = Theme.MAC ? "⌘" : "Ctrl+";
    private static final String SHIFT_MOD = Theme.MAC ? "⇧⌘" : "Ctrl+Shift+";

    private final MacWindow frame = new MacWindow();
    private final Sidebar sidebar;
    private final Editor editor;
    private final ConsolePanel console;
    private final ActivityPill pill = new ActivityPill();
    private final JLabel title = Widgets.label("Scriptum", Theme.semibold(13.5f), () -> Theme.p().text());
    private final JLabel subtitle = Widgets.label("", Theme.ui(Font.PLAIN, 11.5f), () -> Theme.p().text2());
    private final JTextField argsField = new JTextField();
    private final Widgets.PillButton runButton;
    private final JButton stopButton;
    private final JButton sidebarToggle;
    private final Component lights;
    private final JPanel leading = new JPanel();
    private final JPanel toolbar;
    private final JSplitPane split;
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final Timer reparseTimer;

    private Path file;
    private String untitledName = "Untitled.md";
    private String savedText = "";
    private boolean hasDocument;
    private boolean dirty;
    private boolean markersStale;
    private boolean sidebarVisible = Prefs.sidebar();
    private Program program = Program.fromMarkdown("");
    private Program runProgram;
    private RunSession session;

    // Actions (shared by shortcuts, the ⋯ menu and the macOS menu bar)
    private final Action newAction = action("New Document", KeyStroke.getKeyStroke(KeyEvent.VK_N, MENU), e -> newDocument());
    private final Action openAction = action("Open…", KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU), e -> chooseAndOpen());
    private final Action saveAction = action("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU), e -> save());
    private final Action saveAsAction = action("Save As…", KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU | KeyEvent.SHIFT_DOWN_MASK), e -> saveAs());
    private final Action exportAction = action("Export Java Sources…", KeyStroke.getKeyStroke(KeyEvent.VK_E, MENU | KeyEvent.SHIFT_DOWN_MASK), e -> exportSources());
    private final Action runAction = action("Run", KeyStroke.getKeyStroke(KeyEvent.VK_R, MENU), e -> run(null));
    private final Action stopAction = action("Stop", KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, MENU), e -> stop());
    private final Action clearAction = action("Clear Console", KeyStroke.getKeyStroke(KeyEvent.VK_K, MENU | KeyEvent.SHIFT_DOWN_MASK), e -> console().clear());
    private final Action sidebarAction = action("Toggle Sidebar", KeyStroke.getKeyStroke(KeyEvent.VK_0, MENU), e -> setSidebarVisible(!sidebarVisible));
    private final Action tourAction = action("Take the Tour", null, e -> openTour());
    private final Action aboutAction = action("About Scriptum", null, e -> about());
    private final Action closeAction = action(Theme.MAC ? "Close Window" : "Quit", KeyStroke.getKeyStroke(Theme.MAC ? KeyEvent.VK_W : KeyEvent.VK_Q, MENU), e -> requestClose());

    MainWindow() {
        frame.setTitle(Version.NAME);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });

        editor = new Editor(ep -> run(List.of(ep)));
        console = new ConsolePanel((line, col) -> editor.goTo(line, col), this::openStackFrame);
        sidebar = new Sidebar(new Sidebar.Actions() {
            @Override
            public void goToLine(int line) {
                editor.goTo(line, 1);
            }

            @Override
            public void runUnit(SourceUnit unit) {
                List<Program.EntryPoint> eps = new ArrayList<>();
                for (Program.EntryPoint ep : program.entryPoints()) if (ep.unit() == unit) eps.add(ep);
                run(eps);
            }

            @Override
            public void openRecent(Path p) {
                open(p);
            }

            @Override
            public void forgetRecent(Path p) {
                Prefs.removeRecent(p);
                sidebar.setRecent(Prefs.recent());
            }

            @Override
            public void setTheme(Theme.Mode mode) {
                changeTheme(mode);
            }
        });
        sidebar.setThemeMode(Theme.mode());
        sidebar.setRecent(Prefs.recent());

        // ---------------------------------------------------------------- toolbar
        lights = frame.customFrame ? new TrafficLights(frame) : Box.createRigidArea(new Dimension(Theme.MAC ? 68 : 0, 1));
        sidebarToggle = Widgets.toolButton(Icons.tinted("sidebar", 17, () -> Theme.p().text2()), "Show/hide sidebar (" + MOD + "0)");
        sidebarToggle.addActionListener(sidebarAction);

        runButton = new Widgets.PillButton("Run", Icons.tinted("play", 12, () -> Theme.p().onAccent()), () -> Theme.p().accent());
        runButton.setToolTipText("Compile and run all blocks (" + MOD + "R)");
        runButton.addActionListener(runAction);
        stopButton = Widgets.toolButton(Icons.tinted("stop", 15, () -> stopEnabled() ? Theme.p().red() : Theme.p().text3()), "Stop (" + MOD + ".)");
        stopButton.addActionListener(stopAction);
        stopButton.setEnabled(false);
        JButton more = Widgets.toolButton(Icons.tinted("more", 16, () -> Theme.p().text2()), "More");
        more.addActionListener(e -> buildMoreMenu().show(more, 0, more.getHeight() + 4));

        argsField.putClientProperty("JTextField.placeholderText", "Arguments");
        argsField.putClientProperty("JTextField.leadingIcon", Icons.tinted("terminal", 14, () -> Theme.p().text3()));
        argsField.putClientProperty("JTextField.showClearButton", true);
        argsField.putClientProperty("JComponent.roundRect", true);
        argsField.setToolTipText("Command-line arguments passed to every program, e.g.  42 \"two words\"");
        argsField.setFont(Theme.ui(Font.PLAIN, 12.5f));
        argsField.setPreferredSize(new Dimension(170, 28));
        argsField.addActionListener(e -> run(null));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(title);
        titleBlock.add(subtitle);
        leading.setOpaque(false);
        leading.setLayout(new BoxLayout(leading, BoxLayout.X_AXIS));

        JPanel trailing = new JPanel(new GridBagLayout());
        trailing.setOpaque(false);
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 10);
        trailing.add(argsField, gc);
        gc.insets = new Insets(0, 0, 0, 4);
        trailing.add(runButton, gc);
        gc.insets = new Insets(0, 0, 0, 0);
        trailing.add(stopButton, gc);
        trailing.add(more, gc);

        JPanel pillWrap = new JPanel(new GridBagLayout());
        pillWrap.setOpaque(false);
        pillWrap.add(pill);

        toolbar = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                MacWindow.paintRounded(this, g, Theme.p().window());
                g.setColor(Theme.p().hairline());
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 18, 1, 12));
        toolbar.setPreferredSize(new Dimension(10, 52));
        JPanel leadWrap = new JPanel(new BorderLayout());
        leadWrap.setOpaque(false);
        JPanel titleCenter = new JPanel(new GridBagLayout());
        titleCenter.setOpaque(false);
        java.awt.GridBagConstraints tc = new java.awt.GridBagConstraints();
        tc.anchor = java.awt.GridBagConstraints.WEST;
        tc.weightx = 1;
        titleCenter.add(titleBlock, tc);
        frame.installDragArea(titleCenter);
        leadWrap.add(leading, BorderLayout.WEST);
        leadWrap.add(titleCenter, BorderLayout.CENTER);
        leadWrap.setMinimumSize(new Dimension(150, 10));
        toolbar.add(leadWrap, BorderLayout.WEST);
        toolbar.add(pillWrap, BorderLayout.CENTER);
        toolbar.add(trailing, BorderLayout.EAST);
        for (JComponent c : new JComponent[]{toolbar, pillWrap, titleBlock, title, subtitle, pill, sidebar.headerSlot, leading, leadWrap}) {
            frame.installDragArea(c);
        }

        // ---------------------------------------------------------------- center
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor.scroll, console);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        split.setOpaque(false);
        console.setMinimumSize(new Dimension(10, 110));
        editor.scroll.setMinimumSize(new Dimension(10, 120));
        installConsoleDrag();

        WelcomePanel welcome = new WelcomePanel(this::newDocument, this::chooseAndOpen, this::openTour, MOD);
        frame.installDragArea(welcome);
        JPanel welcomeWrap = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                MacWindow.paintRounded(this, g, Theme.p().window());
            }
        };
        welcomeWrap.setOpaque(false);
        welcomeWrap.add(welcome);
        center.setOpaque(false);
        center.add(welcomeWrap, "welcome");
        center.add(split, "editor");

        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.add(toolbar, BorderLayout.NORTH);
        main.add(center, BorderLayout.CENTER);

        JPanel surface = frame.surface();
        surface.add(sidebar, BorderLayout.WEST);
        surface.add(main, BorderLayout.CENTER);
        placeWindowControls();

        // ---------------------------------------------------------------- behaviour
        reparseTimer = new Timer(220, e -> reparse());
        reparseTimer.setRepeats(false);
        editor.area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
        editor.area.addCaretListener(e -> updateCaretInfo());

        installShortcuts();
        installFileDrop();
        Theme.onChange(() -> {
            sidebar.setThemeMode(Theme.mode());
            frame.getRootPane().repaint();
        });
        if (Theme.MAC) installMacIntegration();

        frame.setMinimumSize(minimumFrameSize());
        restoreBounds();
        showWelcome();
    }

    void show() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(Prefs.consoleRatio()));
    }

    MacWindow frame() {
        return frame;
    }

    // ================================================================== documents

    void open(Path p) {
        if (!confirmDiscard()) return;
        String text;
        try {
            text = readText(p);
        } catch (IOException e) {
            error("Couldn't open “" + p.getFileName() + "”", e.getMessage());
            Prefs.removeRecent(p);
            sidebar.setRecent(Prefs.recent());
            return;
        }
        loadDocument(text, p, null);
        Prefs.addRecent(p);
        sidebar.setRecent(Prefs.recent());
    }

    private static String readText(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.defaultCharset());
        }
    }

    private void loadDocument(String text, Path p, String name) {
        stop();
        file = p;
        untitledName = name != null ? name : "Untitled.md";
        hasDocument = true;
        savedText = text;
        editor.setText(text);
        dirty = false;
        console.clear();
        console.setProblems(List.of());
        editor.setProblems(List.of());
        reparse();
        centerCards.show(center, "editor");
        updateTitle();
        updateCaretInfo();
        setRunEnabled(true);
        SwingUtilities.invokeLater(() -> editor.area.requestFocusInWindow());
    }

    void newDocument() {
        if (!confirmDiscard()) return;
        String template = """
                # Untitled

                Write notes in **Markdown** and put Java in fenced blocks.
                Press %sR to compile and run them.

                ```java
                String name = "World";
                System.out.println("Hello, " + name + "!");
                ```
                """.formatted(MOD);
        loadDocument(template, null, "Untitled.md");
        editor.goTo(1, 3);
        editor.area.select(2, 10);
    }

    void openTour() {
        if (!confirmDiscard()) return;
        try (InputStream in = MainWindow.class.getResourceAsStream("/live/justzhuman/scriptum/examples/tour.md")) {
            if (in == null) throw new IOException("The example is missing from this build.");
            loadDocument(new String(in.readAllBytes(), StandardCharsets.UTF_8), null, "tour.md");
        } catch (IOException e) {
            error("Couldn't open the tour", e.getMessage());
        }
    }

    private void chooseAndOpen() {
        FileDialog d = new FileDialog(frame, "Open Markdown File", FileDialog.LOAD);
        d.setFilenameFilter((dir, name) -> isMarkdown(name));
        if (Theme.WINDOWS) d.setFile("*.md;*.markdown");
        if (file != null) d.setDirectory(file.getParent().toString());
        d.setVisible(true);
        if (d.getFile() == null) return;
        open(Path.of(d.getDirectory(), d.getFile()));
    }

    private static boolean isMarkdown(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".mdown") || n.endsWith(".txt");
    }

    private boolean save() {
        if (!hasDocument) return false;
        if (file == null) return saveAs();
        return writeTo(file);
    }

    private boolean saveAs() {
        if (!hasDocument) return false;
        FileDialog d = new FileDialog(frame, "Save As", FileDialog.SAVE);
        d.setFile(file != null ? file.getFileName().toString() : untitledName);
        if (file != null) d.setDirectory(file.getParent().toString());
        d.setVisible(true);
        if (d.getFile() == null) return false;
        String name = d.getFile();
        if (!name.contains(".")) name += ".md";
        Path target = Path.of(d.getDirectory(), name);
        if (writeTo(target)) {
            file = target;
            Prefs.addRecent(target);
            sidebar.setRecent(Prefs.recent());
            updateTitle();
            return true;
        }
        return false;
    }

    private boolean writeTo(Path target) {
        String text = editor.text();
        try {
            Files.writeString(target, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            error("Couldn't save “" + target.getFileName() + "”", e.getMessage());
            return false;
        }
        savedText = text;
        dirty = false;
        updateTitle();
        return true;
    }

    private void exportSources() {
        if (!hasDocument) return;
        Program p = Program.fromMarkdown(editor.text());
        if (p.isEmpty()) {
            pill.done(Icons.colored("status-warning", 16), "Nothing to export — no Java blocks", "", null);
            return;
        }
        Path dir = chooseDirectory("Export Java Sources");
        if (dir == null) return;
        try {
            List<Path> written = JavaBuilder.writeSources(p, dir);
            pill.done(Icons.colored("status-ok", 16), "Exported " + plural(written.size(), "file") + " to " + dir.getFileName(), "", null);
            console.append("Exported " + plural(written.size(), "file") + " to " + dir + "\n", ConsolePanel.Kind.SYS);
        } catch (IOException e) {
            error("Export failed", e.getMessage());
        }
    }

    private Path chooseDirectory(String title) {
        if (Theme.MAC) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            FileDialog d = new FileDialog(frame, title, FileDialog.LOAD);
            d.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
            return d.getFile() == null ? null : Path.of(d.getDirectory(), d.getFile());
        }
        JFileChooser fc = new JFileChooser(file != null ? file.getParent().toFile() : null);
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setApproveButtonText("Export");
        return fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().toPath() : null;
    }

    /** Asks to save unsaved changes. Returns false if the user cancelled. */
    private boolean confirmDiscard() {
        if (!hasDocument || !dirty) return true;
        String name = file != null ? file.getFileName().toString() : untitledName;
        Object[] options = {"Save", "Don't Save", "Cancel"};
        int r = JOptionPane.showOptionDialog(frame,
                "<html><b>Do you want to save the changes you made to “" + escape(name) + "”?</b><br><br>"
                        + "Your changes will be lost if you don't save them.</html>",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                Icons.app(56), options, options[0]);
        if (r == 0) return save();
        return r == 1;
    }

    private void requestClose() {
        if (!confirmDiscard()) return;
        stop();
        if (!frame.isZoomed()) Prefs.bounds(frame.bodyBounds());
        Prefs.sidebar(sidebarVisible);
        if (hasDocument && split.getHeight() > 0) {
            Prefs.consoleRatio(split.getDividerLocation() / (double) split.getHeight());
        }
        frame.dispose();
        System.exit(0);
    }

    // ================================================================== running

    private void run(List<Program.EntryPoint> only) {
        if (!hasDocument || session != null) return;
        Program p = Program.fromMarkdown(editor.text());
        program = p;
        runProgram = p;
        sidebar.setProgram(p);
        editor.setProgram(p);
        console.clear();
        console.setProblems(List.of());
        editor.setProblems(List.of());
        console.showOutput();
        markersStale = false;
        if (p.isEmpty()) {
            pill.done(Icons.colored("status-warning", 16), "No ```java blocks in this document", "", null);
            console.append("Nothing to run: add a fenced ```java block to the document.\n", ConsolePanel.Kind.SYS);
            return;
        }
        for (String w : p.warnings()) console.append("⚠ " + w + "\n", ConsolePanel.Kind.SYS);

        List<Program.EntryPoint> entries;
        if (only == null) {
            entries = p.entryPoints();
        } else {
            // Re-resolve against the fresh program (the editor may have changed).
            entries = new ArrayList<>();
            for (Program.EntryPoint want : only) {
                for (Program.EntryPoint ep : p.entryPoints()) if (ep.className().equals(want.className())) entries.add(ep);
            }
        }
        List<String> args = ProgramRunner.splitArgs(argsField.getText());
        Path wd = file != null ? file.toAbsolutePath().getParent() : Path.of(System.getProperty("user.home"));
        session = new RunSession(p, entries, args, wd, new SessionListener());
        setRunning(true);
        session.start();
    }

    private void stop() {
        RunSession s = session;
        if (s != null) s.stop();
    }

    private boolean stopEnabled() {
        return session != null;
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running && hasDocument);
        runAction.setEnabled(!running && hasDocument);
        stopButton.setEnabled(running);
        stopAction.setEnabled(running);
        argsField.setEnabled(!running);
        stopButton.repaint();
    }

    private void setRunEnabled(boolean on) {
        runButton.setEnabled(on);
        runAction.setEnabled(on);
        argsField.setEnabled(on);
        saveAction.setEnabled(on);
        saveAsAction.setEnabled(on);
        exportAction.setEnabled(on);
    }

    private final class SessionListener implements RunSession.Listener {
        private final List<String> failures = new ArrayList<>();

        @Override
        public void compiling(int files) {
            ui(() -> pill.busy("Compiling " + plural(files, "file") + "…"));
            console.append("Compiling " + plural(files, "file") + "…\n", ConsolePanel.Kind.SYS);
        }

        @Override
        public void compiled(JavaBuilder.Result r) {
            List<Problem> problems = r.problems();
            if (r.success()) {
                String warn = r.warningCount() > 0 ? " · " + plural(r.warningCount(), "warning") : "";
                console.append("✓ Compiled in " + r.millis() + " ms" + warn + "\n", ConsolePanel.Kind.OK);
            } else {
                console.append("✗ Build failed — " + plural(r.errorCount(), "error") + ". See Problems.\n", ConsolePanel.Kind.FAIL);
                for (Problem pr : problems) {
                    if (!pr.isError()) continue;
                    String where = pr.markdownLine() > 0 ? "line " + pr.markdownLine() + ": " : "";
                    console.append("  " + where + pr.message().lines().findFirst().orElse("") + "\n", ConsolePanel.Kind.ERR);
                }
            }
            ui(() -> {
                console.setProblems(problems);
                editor.setProblems(problems);
                if (!r.success()) {
                    pill.done(Icons.colored("status-error", 16), "Build failed · " + plural(r.errorCount(), "error"),
                            ActivityPill.formatSeconds(r.millis()), Theme.p().red());
                    console.showProblems();
                    Problem first = problems.stream().filter(Problem::isError).findFirst().orElse(null);
                    if (first != null && first.markdownLine() > 0) editor.goTo(first.markdownLine(), first.column());
                }
            });
        }

        @Override
        public void started(Program.EntryPoint e) {
            console.append("\n▶ " + e.className() + "\n", ConsolePanel.Kind.SYS);
            ui(() -> {
                pill.busy("Running " + e.simpleName());
                console.setSession(session);
            });
        }

        @Override
        public void output(String text, boolean stderr) {
            console.append(text, stderr ? ConsolePanel.Kind.ERR : ConsolePanel.Kind.OUT);
        }

        @Override
        public void exited(Program.EntryPoint e, int code, long ms, boolean stopped) {
            String nl = "\n";
            if (stopped) {
                console.append(nl + "■ " + e.simpleName() + " stopped\n", ConsolePanel.Kind.SYS);
            } else if (code == 0) {
                console.append(nl + "✓ " + e.simpleName() + " finished in " + ActivityPill.formatSeconds(ms) + "\n", ConsolePanel.Kind.OK);
            } else {
                failures.add(e.simpleName());
                console.append(nl + "✗ " + e.simpleName() + " exited with code " + code + "\n", ConsolePanel.Kind.FAIL);
            }
            ui(() -> console.setSession(null));
        }

        @Override
        public void finished(RunSession.Outcome o) {
            ui(() -> {
                session = null;
                console.setSession(null);
                setRunning(false);
                String time = ActivityPill.formatSeconds(o.millis());
                switch (o.kind()) {
                    case SUCCESS -> pill.done(Icons.colored("status-ok", 16),
                            "Finished · " + plural(o.programs(), "program"), time, null);
                    case NOTHING_TO_RUN -> {
                        pill.done(Icons.colored("status-ok", 16), "Compiled · nothing to run (no main method)", time, null);
                        console.append("No class has a main method, so nothing was launched.\n", ConsolePanel.Kind.SYS);
                    }
                    case PROGRAM_FAILED -> {
                        String[] parts = o.detail().split(":");
                        pill.done(Icons.colored("status-error", 16), parts[0] + " exited with code " + parts[1], time, Theme.p().red());
                    }
                    case STOPPED -> pill.done(Icons.colored("status-stopped", 16), "Stopped", time, null);
                    case ERROR -> {
                        pill.done(Icons.colored("status-error", 16), "Couldn't run: " + o.detail(), "", Theme.p().red());
                        console.append("✗ " + o.detail() + "\n", ConsolePanel.Kind.FAIL);
                    }
                    case BUILD_FAILED -> { /* already reported */ }
                }
            });
        }
    }

    private void openStackFrame(String javaFile, int javaLine) {
        Program p = runProgram != null ? runProgram : program;
        SourceUnit u = p.unitByFile(javaFile);
        if (u == null) return;
        int line = u.markdownLine(javaLine);
        if (line > 0) editor.goTo(line, 1);
    }

    // ================================================================== editing state

    private void edited() {
        if (!hasDocument) return;
        if (!markersStale) {
            markersStale = true;
            editor.setProblems(List.of());
        }
        reparseTimer.restart();
    }

    private void reparse() {
        String text = editor.text();
        program = Program.fromMarkdown(text);
        sidebar.setProgram(program);
        editor.setProgram(program);
        boolean nowDirty = !text.equals(savedText);
        if (nowDirty != dirty) {
            dirty = nowDirty;
            updateTitle();
        }
        if (session == null) idleStatus();
    }

    private void idleStatus() {
        if (!hasDocument) {
            pill.idle("Open or create a document to get started");
            return;
        }
        int units = program.units().size();
        int runnable = program.entryPoints().size();
        if (units == 0) pill.idle("No Java blocks yet");
        else pill.idle(plural(units, "file") + " · " + runnable + " runnable  —  " + MOD + "R to run");
    }

    private void updateTitle() {
        String name = file != null ? file.getFileName().toString() : untitledName;
        title.setText(hasDocument ? name : Version.NAME);
        String where = !hasDocument ? Version.TAGLINE : file != null ? Sidebar.shortPath(file.toAbsolutePath().getParent()) : "Not saved";
        subtitle.setText(dirty ? where + "  ·  Edited" : where);
        frame.setTitle(hasDocument ? name + (dirty ? " •" : "") + " — " + Version.NAME : Version.NAME);
        frame.getRootPane().putClientProperty("Window.documentModified", dirty);
        if (file != null) frame.getRootPane().putClientProperty("Window.documentFile", file.toFile());
    }

    private void updateCaretInfo() {
        if (!hasDocument) {
            console.setCaretInfo("");
            return;
        }
        int line = editor.area.getCaretLineNumber() + 1;
        int col = editor.area.getCaretOffsetFromLineStart() + 1;
        console.setCaretInfo("Ln " + line + ", Col " + col);
    }

    private void showWelcome() {
        hasDocument = false;
        centerCards.show(center, "welcome");
        setRunEnabled(false);
        stopButton.setEnabled(false);
        sidebar.setProgram(null);
        updateTitle();
        idleStatus();
    }

    // ================================================================== layout

    private void setSidebarVisible(boolean visible) {
        sidebarVisible = visible;
        placeWindowControls();
    }

    private void placeWindowControls() {
        sidebar.setVisible(sidebarVisible);
        leading.removeAll();
        if (sidebarVisible) {
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JPanel left = new JPanel(new GridBagLayout());
            left.setOpaque(false);
            left.add(lights);
            header.add(left, BorderLayout.WEST);
            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            right.add(sidebarToggle);
            header.add(right, BorderLayout.EAST);
            frame.installDragArea(header);
            sidebar.setHeader(header);
        } else {
            sidebar.setHeader(null);
            leading.add(lights);
            leading.add(Box.createHorizontalStrut(lights instanceof TrafficLights || Theme.MAC ? 16 : 0));
            leading.add(sidebarToggle);
            leading.add(Box.createHorizontalStrut(12));
        }
        leading.revalidate();
        frame.surface().revalidate();
        frame.surface().repaint();
    }

    private void installConsoleDrag() {
        MouseAdapter drag = new MouseAdapter() {
            private int startY;
            private int startLoc;

            @Override
            public void mousePressed(MouseEvent e) {
                startY = e.getYOnScreen();
                startLoc = split.getDividerLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int loc = startLoc + (e.getYOnScreen() - startY);
                int max = split.getHeight() - console.getMinimumSize().height;
                split.setDividerLocation(Math.max(editor.scroll.getMinimumSize().height, Math.min(max, loc)));
            }
        };
        console.header().addMouseListener(drag);
        console.header().addMouseMotionListener(drag);
    }

    private Dimension minimumFrameSize() {
        Insets m = frame.margins();
        return new Dimension(860 + m.left + m.right, 540 + m.top + m.bottom);
    }

    private void restoreBounds() {
        Rectangle saved = Prefs.bounds();
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets si = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle usable = new Rectangle(screen.x + si.left, screen.y + si.top,
                screen.width - si.left - si.right, screen.height - si.top - si.bottom);
        boolean visible = false;
        if (saved != null) {
            for (var dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                if (dev.getDefaultConfiguration().getBounds().intersects(saved)) visible = true;
            }
        }
        if (saved != null && visible) {
            frame.setBodyBounds(saved);
        } else {
            int w = Math.min(1280, usable.width * 9 / 10);
            int h = Math.min(820, usable.height * 9 / 10);
            frame.setBodyBounds(new Rectangle(usable.x + (usable.width - w) / 2, usable.y + (usable.height - h) / 2, w, h));
        }
    }

    // ================================================================== menus & shortcuts

    private JPopupMenu buildMoreMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(item(newAction, "doc-plus"));
        m.add(item(openAction, "folder"));
        List<Path> recent = Prefs.recent();
        if (!recent.isEmpty()) {
            JMenu r = new JMenu("Open Recent");
            for (Path p : recent) {
                JMenuItem it = new JMenuItem(p.getFileName() + "  —  " + Sidebar.shortPath(p.getParent()));
                it.addActionListener(e -> open(p));
                r.add(it);
            }
            m.add(r);
        }
        m.addSeparator();
        m.add(item(saveAction, "save"));
        m.add(item(saveAsAction, null));
        m.add(item(exportAction, "export"));
        m.addSeparator();
        m.add(item(runAction, "play"));
        m.add(item(stopAction, "stop"));
        m.add(item(clearAction, "trash"));
        m.addSeparator();
        m.add(item(sidebarAction, "sidebar"));
        m.add(appearanceMenu());
        m.addSeparator();
        m.add(item(tourAction, "book"));
        m.add(item(aboutAction, "info"));
        m.addSeparator();
        m.add(item(closeAction, null));
        return m;
    }

    private JMenu appearanceMenu() {
        JMenu menu = new JMenu("Appearance");
        ButtonGroup group = new ButtonGroup();
        for (Theme.Mode mode : Theme.Mode.values()) {
            String label = switch (mode) {
                case SYSTEM -> "Match System";
                case LIGHT -> "Light";
                case DARK -> "Dark";
            };
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(label, Theme.mode() == mode);
            it.addActionListener(e -> changeTheme(mode));
            group.add(it);
            menu.add(it);
        }
        return menu;
    }

    private static JMenuItem item(Action a, String icon) {
        JMenuItem it = new JMenuItem(a);
        if (icon != null) it.setIcon(Icons.tinted(icon, 15, () -> Theme.p().text2()));
        return it;
    }

    private void installShortcuts() {
        if (Theme.MAC) return; // the screen menu bar owns the accelerators on macOS
        JRootPane rp = frame.getRootPane();
        for (Action a : new Action[]{newAction, openAction, saveAction, saveAsAction, exportAction, runAction,
                stopAction, clearAction, sidebarAction, closeAction}) {
            KeyStroke ks = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
            if (ks == null) continue;
            String key = (String) a.getValue(Action.NAME);
            rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, key);
            rp.getActionMap().put(key, a);
            // RSyntaxTextArea has its own bindings; make ours win inside the editor.
            editor.area.getInputMap().put(ks, key);
            editor.area.getActionMap().put(key, a);
        }
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run-f5");
        rp.getActionMap().put("run-f5", runAction);
    }

    private void installMacIntegration() {
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        for (Action a : new Action[]{newAction, openAction, saveAction, saveAsAction, exportAction, closeAction}) fileMenu.add(new JMenuItem(a));
        JMenu runMenu = new JMenu("Run");
        for (Action a : new Action[]{runAction, stopAction, clearAction}) runMenu.add(new JMenuItem(a));
        JMenu viewMenu = new JMenu("View");
        viewMenu.add(new JMenuItem(sidebarAction));
        viewMenu.add(appearanceMenu());
        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(new JMenuItem(tourAction));
        bar.add(fileMenu);
        bar.add(runMenu);
        bar.add(viewMenu);
        bar.add(helpMenu);
        frame.setJMenuBar(bar);
        if (Desktop.isDesktopSupported()) {
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.APP_ABOUT)) d.setAboutHandler(e -> about());
            if (d.isSupported(Desktop.Action.APP_QUIT_HANDLER)) d.setQuitHandler((e, r) -> {
                r.cancelQuit();
                requestClose();
            });
            if (d.isSupported(Desktop.Action.APP_OPEN_FILE)) d.setOpenFileHandler(e -> {
                if (!e.getFiles().isEmpty()) SwingUtilities.invokeLater(() -> open(e.getFiles().get(0).toPath()));
            });
        }
    }

    private void installFileDrop() {
        frame.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(JComponent c, DataFlavor[] flavors) {
                return hasFiles(flavors);
            }

            @Override
            public boolean importData(JComponent c, Transferable t) {
                return openDropped(t);
            }
        });
        // Keep the editor's own text drag & drop, but open dropped files.
        editor.area.setTransferHandler(new RTATextTransferHandler() {
            @Override
            public boolean canImport(JComponent c, DataFlavor[] flavors) {
                return hasFiles(flavors) || super.canImport(c, flavors);
            }

            @Override
            public boolean importData(JComponent c, Transferable t) {
                if (hasFiles(t.getTransferDataFlavors())) return openDropped(t);
                return super.importData(c, t);
            }
        });
    }

    private static final DataFlavor URI_LIST = uriListFlavor();

    private static DataFlavor uriListFlavor() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean hasFiles(DataFlavor[] flavors) {
        for (DataFlavor f : flavors) {
            if (f.equals(DataFlavor.javaFileListFlavor) || (URI_LIST != null && f.equals(URI_LIST))) return true;
        }
        return false;
    }

    /** Opens the first file of a drop (file managers send either a file list or a text/uri-list). */
    private boolean openDropped(Transferable t) {
        try {
            List<File> files = new ArrayList<>();
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                for (Object o : (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor)) files.add((File) o);
            } else if (URI_LIST != null && t.isDataFlavorSupported(URI_LIST)) {
                for (String line : ((String) t.getTransferData(URI_LIST)).split("\r?\n")) {
                    if (line.startsWith("file:")) files.add(new File(URI.create(line.trim())));
                }
            }
            if (files.isEmpty()) return false;
            File f = files.get(0);
            SwingUtilities.invokeLater(() -> open(f.toPath()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================== misc

    private void changeTheme(Theme.Mode mode) {
        Prefs.theme(mode);
        Theme.apply(mode, true);
        sidebar.setThemeMode(mode);
    }

    private void about() {
        String html = "<html><div style='width:260px'>"
                + "<b style='font-size:15px'>" + Version.NAME + "</b><br>"
                + "Version " + Version.get() + "<br><br>"
                + Version.TAGLINE + ": write notes in Markdown, keep runnable Java next to the text.<br><br>"
                + "<span style='color:gray'>Java " + Runtime.version() + " · " + System.getProperty("os.name") + "</span><br>"
                + "<span style='color:gray'>github.com/Zhumanio08/Scriptum</span>"
                + "</div></html>";
        JOptionPane.showMessageDialog(frame, html, "About " + Version.NAME, JOptionPane.PLAIN_MESSAGE, Icons.app(72));
    }

    private void error(String title, String detail) {
        JOptionPane.showMessageDialog(frame, "<html><b>" + escape(title) + "</b><br><br>" + escape(detail == null ? "" : detail) + "</html>",
                title, JOptionPane.ERROR_MESSAGE);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private static void ui(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    private static Action action(String name, KeyStroke key, Consumer<ActionEvent> body) {
        AbstractAction a = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) body.accept(e);
            }
        };
        if (key != null) a.putValue(Action.ACCELERATOR_KEY, key);
        return a;
    }

    /** Used by the screenshot tool to show a realistic state. */
    void loadForScreenshot(String markdown, String name) {
        loadDocument(markdown, null, name);
    }

    void runForScreenshot() {
        run(null);
    }

    boolean running() {
        return session != null;
    }

    void showWelcomeForScreenshot() {
        showWelcome();
    }

    ConsolePanel console() {
        return console;
    }

    Editor editor() {
        return editor;
    }
}
