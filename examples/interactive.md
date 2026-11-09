# Talking to your program

Programs can read standard input. In the terminal just type; in the desktop
app use the input field under the console.

```java
import java.util.Scanner;

public class Greeter {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.print("What is your name? ");
        String name = in.hasNextLine() ? in.nextLine().trim() : "stranger";
        System.out.println("Nice to meet you, " + (name.isEmpty() ? "stranger" : name) + "!");
    }
}
```
