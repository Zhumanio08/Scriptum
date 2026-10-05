import java.lang.foreign.StructLayout;
import java.nio.file.Path; //  Понимает различия между путями винды и макОС
import java.io.IOException;  // Для оповещениях о багах, которые произошли неожиданно
import java.nio.file.Paths;
import java.nio.file.Files;  // Самая главная библиотека, читает, удаляет и копирует файлов. Все нужные методы отсюда
import java.util.List;  // Массивы, но более навароченные.



public class Main{
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Error 302");
            return; // тут мы проверяем, есть ли вообще какие то аргументы в консоли
        }
        StringBuilder builder = new StringBuilder();
        String Filename = args[0]; // Тут мы получаем название файла в консоли
        try {
            Path path = Paths.get(Filename);
            if (!Files.exists(path)){
                System.out.println("Файл" + " " + Filename +" не найден");
                return; // тут из переменной Filename мы узнаем существует ли файл при помощи библиотеки Paths и метода .get()
            }
            List<String> lines = Files.readAllLines(path);
            System.out.println("Успешно прочитали файл!");
            boolean inside = false;
            for(String line: lines){ // тут сложный цикл прочтения файл и из каждого lines ( lines = path, который = Filename)
                if (line.trim().startsWith("```")){
                    inside = !inside;
                    continue; // тут мы идеально выводим джава блоки! Идея в том, чтобы использовать .trim(), чтобы убрать пробелы ( потому что могут быть пробелы в строке)
                                // и поэтому когда доходим до джава блока, мы переключаем наш inside и пропускаем кавычки и выводим чисто код
                }
                if (inside) {
                    builder.append(line).append("\n");
                }
            }
            Path Newfile = Paths.get("src","run.java");
            Files.writeString(Newfile, builder.toString());
            System.out.println("Файл run.java успешно сохранен");
            String javaBin = System.getProperty("java.home") + "/bin/java";
            ProcessBuilder pb = new ProcessBuilder(javaBin, Newfile.toString());
            pb.inheritIO();
            Process comp  = pb.start();
            System.out.println("Файл успешно запущен");
            comp.waitFor();


        } catch (Exception e) {
            System.out.println("Ошибка чтения файла");
            e.printStackTrace();
        }

    }
}