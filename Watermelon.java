import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int n = scanner.nextInt();
        if (n >= 4 && n % 2 == 0) {
            System.out.println("YES");
        }
        else {
            System.out.println("NO");
        }
        scanner.close();
    }
}