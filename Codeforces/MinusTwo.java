package Codeforces;
import java.util.Arrays;
import java.util.Scanner;

public class MinusTwo {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int s = sc.nextInt();
            int[] arr = new int[s];
            for (int i = 0; i < s; i++) {
                arr[i] = sc.nextInt();
            }

            // for (int i = 0; i < s; i++) {
            //     System.out.println(arr[i]);
            // }
        }
        sc.close();
    }

    private static int kadane(int[] arr) {
        int max = 0;
        int count = 1;
        int val = arr[0];
        Arrays.sort(arr);
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] == val) {
                
            }
        }
        return max;
    }
}
