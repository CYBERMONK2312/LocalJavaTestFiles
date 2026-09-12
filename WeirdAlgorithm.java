import java.io.*;
import java.util.*;

public class WeirdAlgorithm {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        long input = Long.parseLong(br.readLine().trim());

        StringBuilder sb = new StringBuilder();
        sb.append(input);

        while (input != 1) {
            if (input % 2 == 1) {
                input = input * 3 + 1;
            } else {
                input /= 2;
            }
            sb.append(' ').append(input);
        }

        PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
        pw.print(sb);
        pw.flush();
    }
}