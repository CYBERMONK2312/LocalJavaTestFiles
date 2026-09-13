import java.util.*;
import java.io.*;

public class Main {
    public static String process(String str) {
        Set<Character> xvowels = new HashSet<>(Arrays.asList('a', 'e', 'i', 'l', 'o', 't', 'v'));

        StringBuilder result = new StringBuilder();
        int consecutiveCount = 0;

        for (int i = 0; i <  str.length(); i++) {
            char currentChar = str.charAt(i);

            if (xvowels.contains(currentChar)) {
                consecutiveCount++;
            } else {
                if (consecutiveCount >= 2) {
                    result.append('X');
                } else if (consecutiveCount == 1) {
                    result.append(str.charAt(i - 1)); 
                }
                result.append(currentChar); 
                consecutiveCount = 0;
            }
        }

        if (consecutiveCount >= 2) {
            result.append('X');
        } else if (consecutiveCount == 1) {
            result.append(str.charAt(str.length() - 1));
        }

        return result.toString();
    }

    // Do not change anything in the `main` method
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String inputLine = scanner.nextLine().trim();
        String outputLine = process(inputLine);
        System.out.println(outputLine);
        scanner.close();
    }
}
