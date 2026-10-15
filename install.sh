#!/bin/bash
# Scriptum installer: скачивает исходники, собирает JAR и ставит команду `scriptum`.
# Использование:
#   curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
set -e

REPO="https://github.com/Zhumanio08/Scriptum.git"
DIR="$HOME/.local/share/scriptum"
BIN="$HOME/.local/bin/scriptum"
MVN="$HOME/.local/maven/apache-maven-3.9.9/bin/mvn"

echo "==> Проверка Java..."
if ! command -v java >/dev/null 2>&1; then
    echo "ОШИБКА: Java не найдена. Установи JDK 17+: sudo dnf install java-17-openjdk-devel"
    exit 1
fi
java -version 2>&1 | head -n 1

echo "==> Проверка Maven..."
if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven не найден, ставлю локально в ~/.local/maven..."
    mkdir -p "$HOME/.local/maven"
    cd "$HOME/.local/maven"
    if [ ! -d "apache-maven-3.9.9" ]; then
        curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz -o maven.tar.gz
        tar xzf maven.tar.gz
        rm -f maven.tar.gz
    fi
    export PATH="$HOME/.local/maven/apache-maven-3.9.9/bin:$PATH"
else
    MVN="mvn"
fi
"$MVN" -version 2>&1 | head -n 1

echo "==> Скачивание Scriptum в $DIR..."
if [ -d "$DIR/.git" ]; then
    git -C "$DIR" pull --ff-only
else
    rm -rf "$DIR"
    git clone --depth 1 "$REPO" "$DIR"
fi

echo "==> Сборка..."
cd "$DIR"
"$MVN" -q clean package

echo "==> Установка команды scriptum в $BIN..."
mkdir -p "$HOME/.local/bin"
JAR="$DIR/target/scriptum-0.1.0-SNAPSHOT.jar"
cat > "$BIN" << EOF
#!/bin/bash
exec java -jar "$JAR" "\$@"
EOF
chmod +x "$BIN"

echo ""
echo "ГОТОВО! Проверяю:"
"$BIN" "$DIR/test.md"

echo ""
echo "Теперь можно запускать из любой папки:  scriptum файл.md"
echo "Если команда не найдена — добавь в ~/.bashrc:  export PATH=\"\$HOME/.local/bin:\$PATH\""
