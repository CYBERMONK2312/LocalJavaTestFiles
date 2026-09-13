package Codeforces;
import java.util.*;

public class KnifePill {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int t = sc.nextInt();

        while (t-- > 0) {
            int n = sc.nextInt();
            int m = sc.nextInt();

            long[] a = new long[n];

            for (int i = 0; i < n; i++) {
                a[i] = sc.nextLong();
            }

            if (m == 1) {
                long ans = Long.MIN_VALUE;

                for (long x : a) {
                    ans = Math.max(ans, x);
                }

                System.out.println(ans);
                continue;
            }

            PriorityQueue<Long> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

            long sum = 0;
            long ans = Long.MIN_VALUE;

            for (int i = 0; i < n; i++) {

                if (maxHeap.size() == m - 1) {
                    ans = Math.max(ans, m * a[i] - sum);
                }

                maxHeap.add(a[i]);
                sum += a[i];

                if (maxHeap.size() > m - 1) {
                    sum -= maxHeap.poll();
                }
            }

            System.out.println(ans);
        }

        sc.close();
    }
}