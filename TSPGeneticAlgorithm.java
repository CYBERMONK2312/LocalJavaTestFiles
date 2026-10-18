import java.util.*;

public class TSPGeneticAlgorithm {

    // ------------------------------------------------------------------ problem data
    static final double INF = Double.POSITIVE_INFINITY;
    static int n, START;
    static double[] d;          // flattened distance matrix, d[i * n + j]
    static int[][] order;       // order[i] = the other cities sorted by distance from i

    // ------------------------------------------------------------------ GA parameters
    static final double CROSSOVER_RATE = 0.90;      // probability that two parents are recombined
    static final double MUTATION_RATE = 0.30;       // probability that an offspring is mutated
    static final double LOCAL_SEARCH_RATE = 0.20;   // memetic variant: fraction of offspring improved by 2-opt
    static final int TOURNAMENT_SIZE = 4;
    static final int ELITES = 2;
    static final int STAGNATION_LIMIT = 3000;       // stop after this many generations without improvement
    static final int TOURNAMENT = 1, ROULETTE = 2;

    static int popSize, maxGenerations, selection;
    static boolean memetic;

    // ------------------------------------------------------------------ GA state
    static int[][] pop, nxt;
    static double[] cost, nxtCost, cum;
    static SplittableRandom rnd;
    static int[] stamp;
    static int stampCounter;

    // statistics
    static long evaluations;

    static final class Result {
        double cost; int[] tour; String status;
        int generations, bestGeneration; long millis;
        double nearestNeighbourCost, finalAverage;
        double[] bestHistory, avgHistory;       // index = generation
    }

    // ================================================================== instance generation
    // (same generator as TSPSMAStar: same seed -> same cities and same START)
    static void generateInstance(int cities, long seed) {
        n = cities;
        Random r = new Random(seed);
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = r.nextDouble() * 1000.0; y[i] = r.nextDouble() * 1000.0; }
        START = r.nextInt(n);                                // random initial state
        d = new double[n * n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i * n + j] = (i == j) ? 0 : Math.hypot(x[i] - x[j], y[i] - y[j]);
        order = new int[n][n - 1];
        for (int i = 0; i < n; i++) {
            final int from = i;
            Integer[] idx = new Integer[n - 1];
            int k = 0;
            for (int j = 0; j < n; j++) if (j != i) idx[k++] = j;
            Arrays.sort(idx, (p, q) -> Double.compare(d[from * n + p], d[from * n + q]));
            for (k = 0; k < n - 1; k++) order[i][k] = idx[k];
        }
    }

    // ================================================================== fitness
    static double tourCost(int[] t) {
        double s = 0;
        int prev = t[0];
        for (int i = 1; i < n; i++) { int c = t[i]; s += d[prev * n + c]; prev = c; }
        return s + d[prev * n + t[0]];
    }

    /** Plain greedy tour from START - used only as a baseline to show how much the GA improves. */
    static double nearestNeighbourCost() {
        boolean[] seen = new boolean[n];
        int cur = START; seen[cur] = true;
        double s = 0;
        for (int k = 1; k < n; k++) {
            for (int c : order[cur]) if (!seen[c]) { s += d[cur * n + c]; seen[c] = true; cur = c; break; }
        }
        return s + d[cur * n + START];
    }

    // ================================================================== genetic operators
    static void randomTour(int[] t) {
        t[0] = START;
        int k = 1;
        for (int c = 0; c < n; c++) if (c != START) t[k++] = c;
        for (int i = n - 1; i > 1; i--) {                    // Fisher-Yates on t[1..n-1]
            int j = 1 + rnd.nextInt(i);
            int tmp = t[i]; t[i] = t[j]; t[j] = tmp;
        }
    }

    static int selectTournament() {
        int best = rnd.nextInt(popSize);
        for (int k = 1; k < TOURNAMENT_SIZE; k++) {
            int c = rnd.nextInt(popSize);
            if (cost[c] < cost[best]) best = c;
        }
        return best;
    }

    /** Fitness-proportionate selection with windowed fitness (worst - cost). */
    static void buildRoulette() {
        double worst = -INF, best = INF;
        for (int i = 0; i < popSize; i++) { worst = Math.max(worst, cost[i]); best = Math.min(best, cost[i]); }
        double floor = 0.02 * (worst - best) + 1e-9;         // every individual keeps a small chance
        double acc = 0;
        for (int i = 0; i < popSize; i++) { acc += (worst - cost[i]) + floor; cum[i] = acc; }
    }
    static int selectRoulette() {
        double r = rnd.nextDouble() * cum[popSize - 1];
        int lo = 0, hi = popSize - 1;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (cum[mid] < r) lo = mid + 1; else hi = mid; }
        return lo;
    }
    static int select() { return selection == ROULETTE ? selectRoulette() : selectTournament(); }

    /** Order crossover (OX): keep a slice of p1, fill the rest in the order the cities occur in p2. */
    static void orderCrossover(int[] p1, int[] p2, int[] child) {
        int m = n - 1;
        int a = rnd.nextInt(m), b = rnd.nextInt(m);
        if (a > b) { int tmp = a; a = b; b = tmp; }
        if (++stampCounter == Integer.MAX_VALUE) { Arrays.fill(stamp, 0); stampCounter = 1; }
        int st = stampCounter;
        child[0] = START;
        for (int i = a; i <= b; i++) { int c = p1[1 + i]; child[1 + i] = c; stamp[c] = st; }
        int f = b + 1 == m ? 0 : b + 1, s = f;
        for (int k = 0; k < m; k++) {
            int c = p2[1 + s];
            if (++s == m) s = 0;
            if (stamp[c] != st) { child[1 + f] = c; if (++f == m) f = 0; }
        }
    }

    /** Swap two cities, or reverse a segment (a 2-opt move) - chosen with equal probability. */
    static void mutate(int[] t) {
        int m = n - 1;
        if (m < 2) return;
        int i = 1 + rnd.nextInt(m), j = 1 + rnd.nextInt(m);
        if (i == j) return;
        if (i > j) { int tmp = i; i = j; j = tmp; }
        if (rnd.nextBoolean()) { int tmp = t[i]; t[i] = t[j]; t[j] = tmp; }
        else reverse(t, i, j);
    }

    static void reverse(int[] t, int i, int j) {
        while (i < j) { int tmp = t[i]; t[i++] = t[j]; t[j--] = tmp; }
    }

    /** 2-opt local search (first improvement) used by the memetic variant. t[0] = START stays in place. */
    static void twoOpt(int[] t) {
        boolean improved;
        do {
            improved = false;
            for (int i = 0; i < n - 1; i++)
                for (int j = i + 2; j < n; j++) {
                    if (i == 0 && j == n - 1) continue;
                    int a = t[i], b = t[i + 1], c = t[j], e = t[(j + 1) % n];
                    double delta = d[a * n + c] + d[b * n + e] - d[a * n + b] - d[c * n + e];
                    if (delta < -1e-9) { reverse(t, i + 1, j); improved = true; }
                }
        } while (improved);
    }

    // ================================================================== driver
    static Result solve(double seconds) {
        long t0 = System.nanoTime(), deadline = t0 + (long) (seconds * 1e9);

        rnd = new SplittableRandom(seedForGa);
        pop = new int[popSize][n]; nxt = new int[popSize][n];
        cost = new double[popSize]; nxtCost = new double[popSize]; cum = new double[popSize];
        stamp = new int[n]; stampCounter = 0; evaluations = 0;

        for (int i = 0; i < popSize; i++) { randomTour(pop[i]); cost[i] = tourCost(pop[i]); }
        evaluations += popSize;

        Result r = new Result();
        r.nearestNeighbourCost = nearestNeighbourCost();
        r.bestHistory = new double[maxGenerations + 1];
        r.avgHistory = new double[maxGenerations + 1];

        int elites = Math.min(ELITES, popSize);
        int[] eliteIdx = new int[elites];
        int[] bestTour = new int[n];
        double bestCost = INF;
        int bestGen = 0, stagnant = 0, gen = 0;
        String stop = "generation limit reached";

        while (true) {
            // ---- record the current generation
            double sum = 0;
            int bi = 0;
            for (int i = 0; i < popSize; i++) { sum += cost[i]; if (cost[i] < cost[bi]) bi = i; }
            if (cost[bi] < bestCost - 1e-9) {
                bestCost = cost[bi]; System.arraycopy(pop[bi], 0, bestTour, 0, n);
                bestGen = gen; stagnant = 0;
            } else stagnant++;
            r.bestHistory[gen] = bestCost; r.avgHistory[gen] = sum / popSize;

            if (gen == maxGenerations) break;
            if (stagnant >= STAGNATION_LIMIT) { stop = "no improvement for " + STAGNATION_LIMIT + " generations"; break; }
            if (System.nanoTime() > deadline) { stop = "time limit reached"; break; }

            // ---- elitism: copy the best individuals unchanged
            for (int e = 0; e < elites; e++) {
                int pick = -1;
                for (int i = 0; i < popSize; i++) {
                    boolean taken = false;
                    for (int q = 0; q < e; q++) if (eliteIdx[q] == i) { taken = true; break; }
                    if (!taken && (pick < 0 || cost[i] < cost[pick])) pick = i;
                }
                eliteIdx[e] = pick;
                System.arraycopy(pop[pick], 0, nxt[e], 0, n);
                nxtCost[e] = cost[pick];
            }
            if (selection == ROULETTE) buildRoulette();

            // ---- breed the rest of the population
            int k = elites;
            while (k < popSize) {
                int[] p1 = pop[select()], p2 = pop[select()];
                int[] c1 = nxt[k];
                int[] c2 = (k + 1 < popSize) ? nxt[k + 1] : null;
                if (rnd.nextDouble() < CROSSOVER_RATE) {
                    orderCrossover(p1, p2, c1);
                    if (c2 != null) orderCrossover(p2, p1, c2);
                } else {
                    System.arraycopy(p1, 0, c1, 0, n);
                    if (c2 != null) System.arraycopy(p2, 0, c2, 0, n);
                }
                breedFinish(c1, k);
                k++;
                if (c2 != null) { breedFinish(c2, k); k++; }
            }

            int[][] tmpPop = pop; pop = nxt; nxt = tmpPop;
            double[] tmpCost = cost; cost = nxtCost; nxtCost = tmpCost;
            gen++;
        }

        if (memetic) {                                       // final polish of the best tour
            twoOpt(bestTour);
            double c = tourCost(bestTour); evaluations++;
            if (c < bestCost - 1e-9) { bestCost = c; r.bestHistory[Math.min(gen, maxGenerations)] = bestCost; }
        }
        r.cost = bestCost; r.tour = bestTour; r.generations = gen; r.bestGeneration = bestGen;
        r.finalAverage = r.avgHistory[Math.min(gen, maxGenerations)];
        r.bestHistory = Arrays.copyOf(r.bestHistory, gen + 1);
        r.avgHistory = Arrays.copyOf(r.avgHistory, gen + 1);
        r.status = "BEST FOUND, not proven optimal (GA is a heuristic; stopped: " + stop + ")";
        r.millis = (System.nanoTime() - t0) / 1_000_000;
        return r;
    }

    /** Mutation, optional local search and evaluation of one offspring stored in nxt[idx]. */
    static void breedFinish(int[] child, int idx) {
        if (rnd.nextDouble() < MUTATION_RATE) mutate(child);
        if (memetic && rnd.nextDouble() < LOCAL_SEARCH_RATE) twoOpt(child);
        nxtCost[idx] = tourCost(child);
        evaluations++;
    }

    static long seedForGa;

    static Result run(int cities, long seed, int population, int generations, double seconds,
                      int selectionMethod, boolean useMemetic) {
        generateInstance(cities, seed);
        popSize = population; maxGenerations = generations; selection = selectionMethod; memetic = useMemetic;
        seedForGa = seed ^ 0x5DEECE66DL;
        return solve(seconds);
    }

    // ================================================================== console I/O
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int cities = readInt(sc, "Number of cities (2-50) [30]: ", 2, 50, 30);
        int pops = readInt(sc, "Population size (10-100000) [500]: ", 10, 100_000, 500);
        int gens = readInt(sc, "Maximum generations [10000]: ", 1, 10_000_000, 10_000);
        double secs = readDouble(sc, "Time limit in seconds [30]: ", 0.1, 86400, 30);
        long seed = readSeed(sc);
        int sel = readInt(sc, "Selection: 1 = tournament, 2 = roulette wheel [1]: ", 1, 2, 1);
        boolean mem = readYesNo(sc, "Memetic variant (2-opt on some offspring)? (y/N) [N]: ", false);

        generateInstance(cities, seed);
        popSize = pops; maxGenerations = gens; selection = sel; memetic = mem;
        seedForGa = seed ^ 0x5DEECE66DL;
        System.out.println("\nRandom instance: " + n + " cities, start city (random initial state) = " + START + ", seed = " + seed);
        Result r = solve(secs);

        System.out.println("\n===== RESULT =====");
        System.out.println("Status          : " + r.status);
        System.out.printf ("Tour cost       : %.2f%n", r.cost);
        System.out.printf ("Greedy baseline : %.2f (nearest neighbour from the start city) -> GA is %.1f%% shorter%n",
                r.nearestNeighbourCost, 100.0 * (r.nearestNeighbourCost - r.cost) / r.nearestNeighbourCost);
        System.out.println("Time            : " + r.millis + " ms");
        System.out.println("Generations run : " + r.generations + " (best tour first found in generation " + r.bestGeneration + ")");
        System.out.println("Evaluations     : " + evaluations);
        System.out.printf ("Final average   : %.2f (mean tour cost of the last population)%n", r.finalAverage);
        System.out.println("Settings        : population " + popSize + ", " + (selection == ROULETTE ? "roulette" : "tournament(" + TOURNAMENT_SIZE + ")")
                + " selection, OX crossover " + CROSSOVER_RATE + ", mutation " + MUTATION_RATE + ", elites " + ELITES
                + (memetic ? ", memetic 2-opt rate " + LOCAL_SEARCH_RATE : ", pure GA"));
        StringBuilder sb = new StringBuilder("Tour            : ");
        for (int c : r.tour) sb.append(c).append(" -> ");
        sb.append(r.tour[0]);
        System.out.println(sb);

        System.out.println("\nConvergence (best / average tour cost):");
        int steps = Math.min(10, r.generations);
        for (int s = 0; s <= steps; s++) {
            int g = steps == 0 ? 0 : (int) ((long) r.generations * s / steps);
            System.out.printf("  generation %-8d best %.2f   average %.2f%n", g, r.bestHistory[g], r.avgHistory[g]);
        }
    }

    static int readInt(Scanner sc, String prompt, int lo, int hi, int def) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            if (s.isEmpty()) return def;
            try {
                int v = Integer.parseInt(s.replace("_", ""));
                if (v >= lo && v <= hi) return v;
                System.out.println("Please enter a value between " + lo + " and " + hi + ".");
            } catch (NumberFormatException e) { System.out.println("Please enter a valid integer."); }
        }
    }
    static double readDouble(Scanner sc, String prompt, double lo, double hi, double def) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            if (s.isEmpty()) return def;
            try {
                double v = Double.parseDouble(s);
                if (v >= lo && v <= hi) return v;
                System.out.println("Please enter a value between " + lo + " and " + hi + ".");
            } catch (NumberFormatException e) { System.out.println("Please enter a valid number."); }
        }
    }
    static long readSeed(Scanner sc) {
        System.out.print("Random seed (Enter for a random one): ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) return System.nanoTime();
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { System.out.println("Invalid seed, using a random one."); return System.nanoTime(); }
    }
    static boolean readYesNo(Scanner sc, String prompt, boolean def) {
        System.out.print(prompt);
        String s = sc.nextLine().trim().toLowerCase();
        if (s.isEmpty()) return def;
        return s.startsWith("y");
    }
}






/**
 * Genetic Algorithm for the Travelling Salesperson Problem, up to 50 cities.
 *
 * Instance   : identical to TSPAStarMST / TSPSMAStar - random cities in a 1000 x 1000 square, Euclidean
 *              distances (complete graph), START city chosen at random from the seed. The same seed gives
 *              the same instance, so results can be compared directly with A* / SMA*.
 * Chromosome : permutation encoding. int[n] tour, tour[0] = START (fixed), tour[1..n-1] = the order in
 *              which the other cities are visited; the tour returns to START at the end.
 * Fitness    : tour length (to be minimised). For roulette-wheel selection a windowed fitness
 *              (worst cost - cost) is used so that shorter tours get proportionally larger slices.
 *
 * Operators (AIMA, 2nd ed., "genetic algorithms"):
 *   selection  - tournament (default) or fitness-proportionate / roulette wheel;
 *   crossover  - Order Crossover (OX): a slice of parent 1 is kept, the remaining cities are filled in
 *                the order they appear in parent 2 (always yields a valid permutation);
 *   mutation   - swap of two cities or inversion of a segment (a 2-opt style move);
 *   elitism    - the best ELITES individuals are copied unchanged into the next generation;
 *   (optional) - memetic variant: a 2-opt local search is applied to a fraction of the offspring.
 *
 * Engineering choices: flat distance matrix, two pre-allocated populations that are swapped each
 * generation (no allocation inside the loop), O(n) order crossover using a stamp array, SplittableRandom.
 *
 * A GA is a heuristic: it returns the best tour it found, but can never PROVE optimality.
 */