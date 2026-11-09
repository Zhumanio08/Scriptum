# Changelog

## 1.0.0 — 2026-11-09

A rewrite of the prototype into a proper tool with a desktop app.

### Added
- **Desktop app** (`scriptum gui`): macOS-style window on every OS (rounded corners,
  shadow, traffic lights), Markdown editor with real Java highlighting inside fences,
  sidebar with the document's code blocks, one-click run per block, streaming console
  with stdin input, Problems list, light / dark / system themes, recent files,
  drag & drop, keyboard shortcuts.
- **Compiler errors in Markdown coordinates**: errors point to the line and column of the
  `.md` file, not of a generated `.java` file.
- **Snippets**: a block with plain statements is wrapped into a `main` method automatically.
- Java 25 compact source files (`void main() { ... }`) are recognised.
- `package` declarations, records, enums, interfaces; the entry point is the class that
  actually declares `main`.
- Fence attributes `norun` and `skip`; `name="..."` as an alias of `class="..."`.
- CLI options: `--list`, `--run`, `--out`, `--keep`, `--timeout`, `--quiet`, `--no-color`,
  `--version`, reading Markdown from stdin (`-`), passing program arguments after `--`.
- Meaningful exit codes (0 / 1 / 2 / 3 / 124), colored output, status on stderr.
- Unit and end-to-end tests, GitHub Actions CI on Linux, macOS and Windows.
- Installers add a launcher to the app menu (Linux) / Start menu (Windows).

### Fixed
- Code inside a longer fence (e.g. a ```` ````markdown ```` example) is no longer extracted.
- Comments and strings no longer confuse class or `main` detection.
- A block whose first class is a helper no longer launches the helper instead of the app.
- `java`/`javac` are located correctly on Windows; a missing JDK gives a clear message.

## 0.x — 2025

Prototype: a single `Main.java` that extracted ```` ```java ```` blocks, wrote them to files,
compiled with `javac` and ran classes containing `main(`. See `docs/devlog.ru.txt`
for the original development diary (in Russian).
