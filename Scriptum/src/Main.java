import java.nio.file.Path; //  Понимает различия между путями винды и макОС
import java.nio.file.Paths;
import java.nio.file.Files;  // Самая главная библиотека, читает, удаляет и копирует файлов. Все нужные методы отсюда
import java.util.List;  // Массивы, но более навароченные.
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class Main{
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Error 302");
            return; // тут мы проверяем, есть ли вообще какие то аргументы в консоли
        }
        String Filename = args[0]; // Тут мы получаем название файла в консоли
        try {
            Path path = Paths.get(Filename);
            if (!Files.exists(path)){
                System.out.println("Файл" + " " + Filename +" не найден");
                return; // тут из переменной Filename мы узнаем существует ли файл при помощи библиотеки Paths и метода .get()
            }
            List<String> lines = Files.readAllLines(path);
            System.out.println("Успешно прочитали файл!");
            // каждый блок -> свой кусок кода, порядок важен
            LinkedHashMap<String, StringBuilder> files = new LinkedHashMap<>();
            boolean inside = false; // сейчас внутри блока или нет
            StringBuilder cur = null; // куда копим текущий блок
            String curName = null; // имя файла для текущего блока
            int autoN = 0; // счетчик для блоков без имени
            for(String line: lines){ // идем по строкам файла (lines = все строки из Filename)
                String t = line.trim(); // убрали пробелы, чтобы ``` было видно
                if (!inside && t.startsWith("```java")) {
                    curName = classFromFence(t); // имя из class="...", может не быть
                    cur = new StringBuilder();
                    inside = true; // зашли в блок
                    continue; // саму строку ``` не сохраняем
                }
                if (inside && t.startsWith("```")) {
                    inside = false; // вышли из блока
                    String code = cur.toString();
                    if (curName == null || curName.isEmpty()) {
                        curName = classFromCode(code); // ищем "class Имя" в коде
                    }
                    if (curName == null || curName.isEmpty()) {
                        autoN++;
                        curName = "Block" + autoN; // совсем без имени
                    }
                    // одинаковые имена склеиваем в один файл
                    files.computeIfAbsent(curName, k -> new StringBuilder()).append(code);
                    cur = null;
                    curName = null;
                    continue; // закрывающие кавычки тоже не сохраняем
                }
                if (inside) {
                    cur.append(line).append("\n"); // копим строки блока
                }
            }
            if (files.isEmpty()) {
                System.out.println("Java-блоки не найдены");
                return; // нечего сохранять и запускать
            }
            Path outDir = Paths.get("src"); // кладем рядом с Main
            if (!Files.isDirectory(outDir)) outDir = Paths.get(".");
            List<String> names = new ArrayList<>(files.keySet());
            List<String> jfs = new ArrayList<>();
            for (Map.Entry<String, StringBuilder> e : files.entrySet()) {
                Path f = outDir.resolve(e.getKey() + ".java"); // каждый класс в свой файл
                Files.writeString(f, e.getValue().toString());
                System.out.println("Сохранен " + f);
                jfs.add(f.toString());
            }
            String jHome = System.getProperty("java.home");
            List<String> cmd = new ArrayList<>();
            cmd.add(jHome + "/bin/javac"); // компилятор
            cmd.addAll(jfs); // все файлы разом, чтобы видели друг друга
            ProcessBuilder cb = new ProcessBuilder(cmd);
            cb.inheritIO();
            if (cb.start().waitFor() != 0) {
                System.out.println("Ошибка компиляции");
                return; // битый код не запускаем
            }
            String javaBin = jHome + "/bin/java";
            List<String> toRun = new ArrayList<>();
            for (String n : names) {
                // запускаем только те, где есть main
                if (files.get(n).toString().contains("main(")) toRun.add(n);
            }
            if (toRun.isEmpty()) toRun = names; // main не нашли — пробуем все
            for (String n : toRun) {
                ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", outDir.toString(), n);
                pb.inheritIO();
                System.out.println("Запущен класс " + n);
                pb.start().waitFor(); // ждем каждый по очереди
            }


        } catch (Exception e) {
            System.out.println("Ошибка чтения файла");
            e.printStackTrace();
        }
    }

    static String classFromFence(String fence) {
        // достает class="..." из строки ```java class="..."
        int ci = fence.indexOf("class=");
        if (ci == -1) return null; // класса нет, имя возьмем из кода
        int vs = ci + 6; // перепрыгнули через class=
        if (vs >= fence.length()) return null;
        char q = fence.charAt(vs); // кавычка или первая буква
        if (q == '"' || q == '\'') {
            int ve = fence.indexOf(q, vs + 1); // ищем закрывающую кавычку
            if (ve == -1) return null;
            return fence.substring(vs + 1, ve).trim();
        }
        int ve = vs; // без кавычек, идем до пробела
        while (ve < fence.length() && fence.charAt(ve) != ' ') ve++;
        return fence.substring(vs, ve).trim();
    }

    static String classFromCode(String code) {
        // ищет "class Имя" внутри кода блока
        Matcher m = Pattern.compile("class\\s+(\\w+)").matcher(code);
        if (m.find()) return m.group(1);
        return null;
    }
}