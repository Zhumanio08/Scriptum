# Scriptum

## Русский

### Что это

Scriptum — CLI-утилита на Java. Она читает Markdown-файл, находит в нём блоки кода
` ```java `, сохраняет каждый блок в отдельный `.java`-файл, компилирует их через
`javac` и запускает те классы, в которых есть метод `main`.

Пример Markdown:

````markdown
# Пример

```java
class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Scriptum!");
    }
}
```
````

### Требования

- JDK 17 или новее (`java -version`);
- Maven 3.8+ (`mvn -version`).

### Установка

Установщик собирает проект из исходников, поэтому нужен JDK 17+ и git.
Maven ставить не обязательно — скрипт скачает его сам, если не найдёт.
После установки команда `scriptum` работает из любой папки:

```bash
cd ~/мой-проект
scriptum notes.md
```

Извлечённый код выполняется во временной папке, проект пользователя не засоряется.

#### Linux

1. Поставь JDK 17+ и git. Примеры для популярных дистрибутивов:

Debian / Ubuntu:

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk git curl
```

Fedora:

```bash
sudo dnf install -y java-17-openjdk-devel git curl
```

Arch:

```bash
sudo pacman -S --noconfirm jdk17-openjdk git curl
```

2. Запусти установщик одной командой:

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

Скрипт клонирует репозиторий в `~/.local/share/scriptum`, выполняет
`mvn clean package` и кладёт команду `scriptum` в `~/.local/bin`.

3. Если shell пишет `scriptum: command not found`, добавь в `~/.bashrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

#### macOS

1. Поставь JDK 17+ и git (через Homebrew):

```bash
brew install openjdk@17 git
```

Если `java -version` всё равно показывает старую версию, привяжи JDK:

```bash
sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

2. Запусти установщик (тот же, что для Linux):

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

3. На macOS по умолчанию shell — zsh, поэтому если команда не найдена,
добавь путь в `~/.zshrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

#### Windows

1. Поставь JDK 17+ и git. Через winget (в PowerShell):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Git.Git
```

Или вручную: [Adoptium Temurin 17](https://adoptium.net/temurin/releases/) и
[git для Windows](https://git-scm.com/download/win). Maven ставить не надо —
установщик скачает portable-версию сам.

2. Скачай и запусти установщик (в PowerShell):

```powershell
irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat; .\install.bat
```

Альтернатива без консоли: скачай `install.bat` со страницы репозитория
и запусти двойным кликом. Скрипт положит репозиторий в
`%LOCALAPPDATA%\scriptum\repo`, соберёт JAR и скопирует `scriptum.cmd`
в `%LOCALAPPDATA%\Microsoft\WindowsApps` (эта папка уже в PATH),
поэтому команда видна из любой папки:

```bat
scriptum notes.md
```

#### Без установщика (любая ОС)

Нужны JDK 17+, Maven 3.8+ и git:

```bash
git clone https://github.com/Zhumanio08/Scriptum.git
cd Scriptum
mvn clean package
java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
```

### Сборка и запуск

```bash
mvn clean package
java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
```

Аргумент — путь к Markdown-файлу. Без аргументов программа печатает `Error 302` и выходит.

### Пример (проверен реальным запуском)

Входной `test.md`:

````markdown
# Test

```java
class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Scriptum!");
    }
}
```
````

Команда и вывод:

```text
$ java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
Успешно прочитали файл!
Временная папка: /tmp/scriptum-XXXXXXXX
Сохранен /tmp/scriptum-XXXXXXXX/Hello.java
Запущен класс Hello
Hello, Scriptum!
```

### Как именуются классы

Имя `.java`-файла для каждого блока выбирается по первому сработавшему правилу:

1. Атрибут забора: ` ```java class="Имя" ` (кавычки `"` / `'` или без кавычек);
2. Первое вхождение `class Имя` внутри кода блока;
3. Иначе — `Block1`, `Block2`, … по порядку блоков без имени.

### Ограничения текущей версии

- Извлечённые `.java`-файлы сохраняются во временную папку (`/tmp/scriptum-*`),
  поэтому проект пользователя не засоряется — запускать можно из любой папки.
- Блоки с одинаковым именем класса склеиваются в один файл (содержимое дописывается
  друг за другом), что может дать невалидный Java-код.
- Запускаются только классы, в тексте которых есть подстрока `main(`; если таких нет,
  пробуются все извлечённые классы подряд.
- Закрытием блока считается любая строка, начинающаяся с ` ``` ` после trim;
  вложенные заборы не поддерживаются.
- Пути к `javac`/`java` берутся как `<java.home>/bin/javac` — нужен полный JDK,
  на чистом JRE компиляция не запустится.
- Обработка ошибок минимальная: битый код даёт `Ошибка компиляции`, любое исключение —
  `Ошибка чтения файла` + stack trace.

### Структура проекта

```text
scriptum/
├── pom.xml                  # Maven-сборка, mainClass = Main
├── README.md
├── test.md                  # пример для проверки запуска
├── install.sh               # установщик одной командой (Linux/macOS)
├── install.bat              # установщик (Windows)
├── done.txt                 # заметки по истории релизов
├── src/
│   ├── main/java/Main.java  # вся логика (один класс, без пакетов)
│   └── test/java/           # пустая, под будущие тесты
└── target/
    └── scriptum-0.1.0-SNAPSHOT.jar
```

Старая IDEA-раскладка (`Scriptum/src/`, `out/`, `*.iml`) оставлена нетронутой
и в сборке не участвует.

### Статус

Учебный проект, версия `0.1.0-SNAPSHOT`. API и формат вывода нестабильны
и могут меняться без предупреждения.

---

## English

### What it is

Scriptum is a small Java CLI tool. It reads a Markdown file, extracts ` ```java `
code fences, saves each block as a `.java` file, compiles them with `javac`,
and runs the classes that contain a `main` method.

Example Markdown:

````markdown
# Example

```java
class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Scriptum!");
    }
}
```
````

### Requirements

- JDK 17 or newer (`java -version`);
- Maven 3.8+ (`mvn -version`).

### Install

The installer builds the project from source, so you need JDK 17+ and git.
Maven is optional — the script downloads it automatically if missing.
After install the `scriptum` command works from any folder:

```bash
cd ~/my-project
scriptum notes.md
```

Extracted code runs in a temp directory, so the user project stays clean.

#### Linux

1. Install JDK 17+ and git. Examples for popular distros:

Debian / Ubuntu:

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk git curl
```

Fedora:

```bash
sudo dnf install -y java-17-openjdk-devel git curl
```

Arch:

```bash
sudo pacman -S --noconfirm jdk17-openjdk git curl
```

2. Run the one-line installer:

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

The script clones the repo into `~/.local/share/scriptum`, runs
`mvn clean package`, and puts the `scriptum` command into `~/.local/bin`.

3. If the shell says `scriptum: command not found`, add to `~/.bashrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

#### macOS

1. Install JDK 17+ and git via Homebrew:

```bash
brew install openjdk@17 git
```

If `java -version` still shows an old version, link the JDK:

```bash
sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

2. Run the same installer as on Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

3. The default shell on macOS is zsh, so if the command is not found,
add the path to `~/.zshrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

#### Windows

1. Install JDK 17+ and git. Via winget (in PowerShell):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Git.Git
```

Or manually: [Adoptium Temurin 17](https://adoptium.net/temurin/releases/) and
[git for Windows](https://git-scm.com/download/win). No need to install Maven —
the installer fetches a portable copy itself.

2. Download and run the installer (in PowerShell):

```powershell
irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat; .\install.bat
```

Alternative without a console: download `install.bat` from the repo page
and double-click it. The script puts the repo into
`%LOCALAPPDATA%\scriptum\repo`, builds the JAR, and copies `scriptum.cmd`
into `%LOCALAPPDATA%\Microsoft\WindowsApps` (already on PATH),
so the command is visible from any folder:

```bat
scriptum notes.md
```

#### Without the installer (any OS)

JDK 17+, Maven 3.8+ and git are required:

```bash
git clone https://github.com/Zhumanio08/Scriptum.git
cd Scriptum
mvn clean package
java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
```

### Build and run

```bash
mvn clean package
java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
```

The argument is the path to a Markdown file. With no arguments the program prints
`Error 302` and exits.

### Example (verified by an actual run)

Input `test.md`:

````markdown
# Test

```java
class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Scriptum!");
    }
}
```
````

Command and output:

```text
$ java -jar target/scriptum-0.1.0-SNAPSHOT.jar test.md
Успешно прочитали файл!
Временная папка: /tmp/scriptum-XXXXXXXX
Сохранен /tmp/scriptum-XXXXXXXX/Hello.java
Запущен класс Hello
Hello, Scriptum!
```

(The status lines are printed in Russian by the program; only the last line
is produced by the extracted `Hello` class.)

### How classes are named

Each block becomes one `.java` file. The file name comes from the first rule
that matches:

1. Fence attribute: ` ```java class="Name" ` (double quotes, single quotes, or no quotes);
2. The first `class Name` occurrence inside the block code;
3. Otherwise — `Block1`, `Block2`, … in order of unnamed blocks.

### Current limitations

- Extracted `.java` files go into a temp directory (`/tmp/scriptum-*`),
  so the user project stays clean — you can run from any folder.
- Blocks sharing one class name are concatenated into a single file (contents appended
  one after another), which may produce invalid Java.
- Only classes whose text contains the substring `main(` are launched; if none match,
  every extracted class is tried in order.
- Any line starting with ` ``` ` (after trim) closes a block; nested fences are not supported.
- `javac`/`java` resolve as `<java.home>/bin/javac`, so a full JDK is required —
  a plain JRE cannot compile.
- Error handling is minimal: broken code yields `Ошибка компиляции`, any exception
  yields `Ошибка чтения файла` plus a stack trace.

### Project layout

```text
scriptum/
├── pom.xml                  # Maven build, mainClass = Main
├── README.md
├── test.md                  # sample file for smoke-testing the run
├── install.sh               # one-line installer (Linux/macOS)
├── install.bat              # installer (Windows)
├── done.txt                 # release history notes (Russian)
├── src/
│   ├── main/java/Main.java  # all logic (single class, default package)
│   └── test/java/           # empty, reserved for future tests
└── target/
    └── scriptum-0.1.0-SNAPSHOT.jar
```

The legacy IDEA layout (`Scriptum/src/`, `out/`, `*.iml`) is left untouched
and is not part of the build.

### Status

Learning project, version `0.1.0-SNAPSHOT`. Output format and behavior are
unstable and may change without notice.
