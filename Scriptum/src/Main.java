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
            return;
        }
        String Filename = args[0];
        try {
            Path path = Paths.get(Filename);
            if (!Files.exists(path)){
                System.out.println("Файл" + " " + Filename +" не найден");
                return;
            }
            List<String> lines = Files.readAllLines(path);
            System.out.println("Успешно прочитали файл!");
            for(String line: lines){
                System.out.println(line);
            }
        } catch (Exception e) {
            System.out.println("Ошибка чтения файла");;
        }
    }
}