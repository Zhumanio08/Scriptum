#!/usr/bin/env bash
# Builds a self-contained native app with jpackage (no Java installation needed to run it).
#   ./packaging/package.sh            → app image in dist/
#   ./packaging/package.sh dmg|deb|msi|exe|rpm|pkg
# Requires a JDK 17+ that includes jpackage and jlink (e.g. Eclipse Temurin).
set -euo pipefail
cd "$(dirname "$0")/.."
TYPE="${1:-app-image}"
VERSION=$(sed -n 's:.*<version>\([0-9][0-9.]*\)</version>.*:\1:p' pom.xml | head -n 1)

[ -f target/scriptum.jar ] || mvn -q -B -DskipTests package
rm -rf dist/input && mkdir -p dist/input
cp target/scriptum.jar dist/input/

ICON=()
case "$(uname -s)" in
    Linux*) ICON=(--icon packaging/icon.png) ;;
esac

# jdk.compiler is included on purpose: Scriptum compiles the user's code in-process.
jpackage \
    --type "$TYPE" \
    --name Scriptum \
    --app-version "$VERSION" \
    --vendor "Zhumanio08" \
    --description "Run the Java inside your Markdown" \
    --input dist/input \
    --main-jar scriptum.jar \
    --main-class live.justzhuman.scriptum.Launcher \
    --arguments gui \
    --add-modules java.base,java.desktop,java.prefs,java.logging,java.datatransfer,java.compiler,jdk.compiler,jdk.zipfs,jdk.unsupported \
    "${ICON[@]}" \
    --dest dist
echo "Done: dist/"
