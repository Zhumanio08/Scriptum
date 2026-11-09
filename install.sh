#!/usr/bin/env bash
# Scriptum installer for Linux and macOS.
#
#   curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
#
# Builds Scriptum from source and installs:
#   ~/.local/bin/scriptum                         the command (CLI + `scriptum gui`)
#   ~/.local/share/applications/scriptum.desktop  a launcher in the app menu (Linux)
# Needs: JDK 17+ and git. Maven is downloaded automatically if missing.
# Re-run the same command to update.
set -euo pipefail

REPO="${SCRIPTUM_REPO:-https://github.com/Zhumanio08/Scriptum.git}"
BRANCH="${SCRIPTUM_BRANCH:-master}"
DIR="${SCRIPTUM_HOME:-$HOME/.local/share/scriptum}"
BIN_DIR="$HOME/.local/bin"
MAVEN_VERSION="3.9.9"
MAVEN_DIR="$HOME/.local/share/scriptum-maven/apache-maven-$MAVEN_VERSION"

if [ -t 1 ]; then B=$'\033[1m'; C=$'\033[1;35m'; R=$'\033[1;31m'; G=$'\033[1;32m'; N=$'\033[0m'; else B= C= R= G= N=; fi
step() { printf '%s==>%s %s%s%s\n' "$C" "$N" "$B" "$*" "$N"; }
die()  { printf '%serror:%s %s\n' "$R" "$N" "$*" >&2; exit 1; }

# --------------------------------------------------------------- requirements
step "Checking Java"
command -v java >/dev/null 2>&1 || die "Java not found. Install JDK 17 or newer (see README → Install)."
command -v javac >/dev/null 2>&1 || die "javac not found: a JDK is required, not just a JRE (e.g. openjdk-17-jdk / java-17-openjdk-devel)."
JAVA_VERSION=$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')
JAVA_MAJOR=${JAVA_VERSION%%.*}
[ "$JAVA_MAJOR" = "1" ] && JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d. -f2)
[ "${JAVA_MAJOR:-0}" -ge 17 ] 2>/dev/null || die "Java $JAVA_VERSION found, but Scriptum needs 17 or newer."
echo "    Java $JAVA_VERSION"

command -v git >/dev/null 2>&1 || die "git not found. Install git and run this script again."

step "Checking Maven"
if command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
else
    if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
        echo "    Maven not found — downloading Maven $MAVEN_VERSION (only used for building)"
        command -v curl >/dev/null 2>&1 || die "curl not found."
        mkdir -p "$(dirname "$MAVEN_DIR")"
        curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" \
            | tar xz -C "$(dirname "$MAVEN_DIR")"
    fi
    MVN="$MAVEN_DIR/bin/mvn"
fi
"$MVN" -v 2>/dev/null | head -n 1 | sed 's/^/    /'

# --------------------------------------------------------------- source + build
step "Getting the source → $DIR"
if [ -d "$DIR/.git" ]; then
    git -C "$DIR" fetch --quiet --depth 1 origin "$BRANCH"
    git -C "$DIR" reset --quiet --hard "origin/$BRANCH"
else
    rm -rf "$DIR"
    git clone --quiet --depth 1 --branch "$BRANCH" "$REPO" "$DIR"
fi

step "Building (this takes a minute the first time)"
(cd "$DIR" && "$MVN" -q -B -DskipTests package)
JAR="$DIR/target/scriptum.jar"
[ -f "$JAR" ] || die "Build finished but $JAR is missing."

# --------------------------------------------------------------- install
step "Installing the scriptum command → $BIN_DIR/scriptum"
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/scriptum" <<EOF
#!/usr/bin/env bash
exec java -jar "$JAR" "\$@"
EOF
chmod +x "$BIN_DIR/scriptum"

if [ "$(uname -s)" = "Linux" ]; then
    step "Adding Scriptum to the application menu"
    APPS="$HOME/.local/share/applications"
    mkdir -p "$APPS"
    cat > "$APPS/scriptum.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Scriptum
GenericName=Markdown Java Runner
Comment=Run the Java inside your Markdown
Exec=java -jar "$JAR" gui %f
Icon=$DIR/packaging/icon.png
Terminal=false
Categories=Development;IDE;
MimeType=text/markdown;text/x-markdown;
StartupWMClass=live-justzhuman-scriptum-Launcher
Keywords=java;markdown;notebook;runner;
EOF
    command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q "$APPS" || true
fi

# --------------------------------------------------------------- smoke test
step "Testing"
"$BIN_DIR/scriptum" --version
"$BIN_DIR/scriptum" --quiet "$DIR/examples/hello.md"

echo
printf '%s✓ Scriptum is installed.%s\n' "$G" "$N"
echo "    scriptum notes.md      run the Java blocks of a Markdown file"
echo "    scriptum gui           open the desktop app"
case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *)
        SHELL_RC="$HOME/.bashrc"
        [ "$(basename "${SHELL:-}")" = "zsh" ] && SHELL_RC="$HOME/.zshrc"
        echo
        echo "    $BIN_DIR is not on your PATH yet. Run:"
        echo "      echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> $SHELL_RC && source $SHELL_RC"
        ;;
esac
