package Codeforces;
import java.util.*;
import java.util.stream.IntStream;

public class HalloumniBoxes {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int k = sc.nextInt();
            int[] arr = new int[n];
            for(int i=0; i<n; i++){
                arr[i] = sc.nextInt();
            }

            if(IntStream.range(0, arr.length - 1)
                    .noneMatch(i -> arr[i] > arr[i + 1])) {
                        System.out.println("YES");
            }
            else if (k == 1)
                System.out.println("NO");
            else if(k > 1) System.out.println("YES");
        }
        sc.close();
    }
}