package Codeforces;
import java.util.*;

class MadamantsSkatingDynasty {
    static final long MOD = 998244353L;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int t = sc.nextInt();

        while (t-- > 0) {
            int n = sc.nextInt();

            long[] a = new long[n];

            for (int i = 0; i < n; i++) {
                a[i] = sc.nextLong();
            }

            Arrays.sort(a);

            if (n == 1) {
                System.out.println(0);
                continue;
            }

            long[] fact = new long[n + 1];
            long[] invFact = new long[n + 1];

            fact[0] = 1;

            for (int i = 1; i <= n; i++) {
                fact[i] = fact[i - 1] * i % MOD;
            }

            invFact[n] = power(fact[n], MOD - 2);

            for (int i = n; i >= 1; i--) {
                invFact[i - 1] = invFact[i] * i % MOD;
            }

            long[] suffixSum = new long[n + 1];

            for (int i = n - 1; i >= 0; i--) {
                suffixSum[i] = (suffixSum[i + 1] + a[i]) % MOD;
            }

            long ans = 0;

            for (int i = 0; i < n - 1; i++) {
                long higher = n - i - 1;

                long sumDifferences =
                        (suffixSum[i + 1]
                        - (higher % MOD) * (a[i] % MOD) % MOD
                        + MOD) % MOD;

                long ways = fact[n - 1] * power(higher, MOD - 2) % MOD;

                ans = (ans + ways * sumDifferences) % MOD;
            }

            System.out.println(ans);
        }

        sc.close();
    }

    static long power(long a, long b) {
        long result = 1;

        while (b > 0) {
            if ((b & 1) == 1) {
                result = result * a % MOD;
            }

            a = a * a % MOD;
            b >>= 1;
        }

        return result;
    }
}