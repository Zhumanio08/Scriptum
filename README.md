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

Установка одной командой (Linux / macOS). Требуется только JDK 17+ — Maven и git
скрипт проверит и докачает сам:

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

Скрипт клонирует репозиторий в `~/.local/share/scriptum`, собирает JAR командой
`mvn clean package` и ставит команду `scriptum` в `~/.local/bin`. После этого
в любом своём проекте достаточно:

```bash
cd ~/мой-проект
scriptum notes.md
```

Если shell пишет `scriptum: command not found`, добавь в `~/.bashrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Windows — скачай `install.bat` со страницы репозитория и запусти его
(нужны JDK 17+ и git; Maven скрипт скачает сам):

```powershell
irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat; .\install.bat
```

Без установщика — вручную на любой ОС (нужны JDK 17+, Maven, git):

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

One-line install (Linux / macOS). Only JDK 17+ is required — the script checks
for Maven and git itself:

```bash
curl -fsSL https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.sh | bash
```

The script clones the repo into `~/.local/share/scriptum`, builds the JAR with
`mvn clean package`, and installs the `scriptum` command into `~/.local/bin`.
After that, inside any of your projects just run:

```bash
cd ~/my-project
scriptum notes.md
```

If the shell says `scriptum: command not found`, add to `~/.bashrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Windows — download `install.bat` from the repo page and run it
(JDK 17+ and git required; Maven is downloaded automatically):

```powershell
irm https://raw.githubusercontent.com/Zhumanio08/Scriptum/master/install.bat -OutFile install.bat; .\install.bat
```

Without the installer — manually on any OS (JDK 17+, Maven, git required):

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
