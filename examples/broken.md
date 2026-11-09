# When things go wrong

Compiler errors point to the line **in this Markdown file**, not in some
generated `.java` file.

```java
public class Broken {
    public static void main(String[] args) {
        int answer = "forty-two";
        System.out.println(answer)
    }
}
```
