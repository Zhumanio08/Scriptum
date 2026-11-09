# A tour of Scriptum

Scriptum runs the Java code that lives inside a Markdown document.
Prose stays prose; every fenced Java block becomes real, compiled code.

## 1. Snippets

A block with plain statements is wrapped into a `main` method for you:

```java
import java.time.LocalDate;

var today = LocalDate.now();
System.out.println("Snippet says hi on " + today.getDayOfWeek());
```

## 2. Classes that work together

Blocks are compiled together, so a class can use code from another block.

```java
record Book(String title, int year) {
    boolean isClassic() {
        return year < 1950;
    }
}
```

```java
import java.util.List;

public class Library {
    public static void main(String[] args) {
        List<Book> shelf = List.of(
                new Book("Dune", 1965),
                new Book("1984", 1949),
                new Book("The Hobbit", 1937));

        shelf.stream()
             .filter(Book::isClassic)
             .forEach(b -> System.out.println("Classic: " + b.title() + " (" + b.year() + ")"));
    }
}
```

## 3. Splitting a class across blocks

Give blocks the same name and they are merged into one file, so you can explain
a long class step by step.

```java class="Fibonacci"
public class Fibonacci {
    static long fib(int n) {
        long a = 0, b = 1;
        for (int i = 0; i < n; i++) { long t = a + b; a = b; b = t; }
        return a;
    }
```

The `main` method comes later in the text:

```java class="Fibonacci"
    public static void main(String[] args) {
        for (int n = 0; n <= 10; n++) System.out.print(fib(n) + " ");
        System.out.println();
    }
}
```

## 4. Showing code without running it

`norun` compiles the block but does not launch it; `skip` ignores it entirely.

```java norun
class Server {
    public static void main(String[] args) throws Exception {
        Thread.sleep(Long.MAX_VALUE); // would block forever
    }
}
```

```java skip
// pseudo-code is fine here
for each request -> handle(request)
```
