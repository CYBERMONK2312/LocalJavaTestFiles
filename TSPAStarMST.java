import java.util.*;

public class TSPAStarMST {

    static int n;
    static double[][] dist;
    static final int START = 0;
    static long FULL_MASK;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        n = readNodeCount(scanner);
        int maxExpansions = readMaxExpansions(scanner);
        long seed = readSeed(scanner);

        generateRandomCompleteGraph(n, seed);
        FULL_MASK = (n == 64) ? -1L : ((1L << n) - 1);

        System.out.println("Generated a complete graph with " + n + " nodes.");

        long t0 = System.currentTimeMillis();
        Result result = solveAStarMST(maxExpansions);
        long t1 = System.currentTimeMillis();

        System.out.println();
        System.out.println("===== RESULT =====");
        System.out.println("Status       : " + (result.optimal ? "OPTIMAL (goal reached before budget ran out)"
                : "APPROXIMATE (expansion budget exhausted, completed greedily)"));
        System.out.println("Nodes expanded: " + result.expansions);
        System.out.println("Time         : " + (t1 - t0) + " ms");
        System.out.println("Tour cost    : " + String.format("%.2f", result.cost));
        System.out.println("Tour         : " + result.tour);
    }


    static int readNodeCount(Scanner scanner) {
        while (true) {
            System.out.print("Enter number of nodes (>50 recommended, max 60 for this bitmask implementation): ");
            String line = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(line);
                if (val >= 2 && val <= 60) return val;
                System.out.println("Please enter a value between 2 and 60.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    static int readMaxExpansions(Scanner scanner) {
        System.out.print("Enter node-expansion budget (press Enter for default 300000): ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) return 300_000;
        try {
            int val = Integer.parseInt(line);
            return val > 0 ? val : 300_000;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input, using default of 300000.");
            return 300_000;
        }
    }

    static long readSeed(Scanner scanner) {
        System.out.print("Enter random seed (press Enter for a random one): ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) return System.nanoTime();
        try {
            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            System.out.println("Invalid seed, using a random one.");
            return System.nanoTime();
        }
    }

    // Graph Generation
    static void generateRandomCompleteGraph(int n, long seed) {
        Random rand = new Random(seed);
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rand.nextDouble() * 1000.0;
            y[i] = rand.nextDouble() * 1000.0;
        }
        dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dist[i][j] = 0;
                } else {
                    double dx = x[i] - x[j];
                    double dy = y[i] - y[j];
                    dist[i][j] = Math.sqrt(dx * dx + dy * dy);
                }
            }
        }
    }

    //A* state
    static class State implements Comparable<State> {
        long mask;
        int current;
        double g;
        double f;
        State parent;

        State(long mask, int current, double g, double f, State parent) {
            this.mask = mask;
            this.current = current;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(State o) {
            return Double.compare(this.f, o.f);
        }
    }

    static class Result {
        List<Integer> tour;
        double cost;
        boolean optimal;
        int expansions;
    }

// A* main loop
    static Result solveAStarMST(int maxExpansions) {
        PriorityQueue<State> open = new PriorityQueue<>();
        Map<String, Double> bestG = new HashMap<>();

        long startMask = 1L << START;
        double h0 = heuristic(START, startMask);
        State s0 = new State(startMask, START, 0.0, h0, null);
        open.add(s0);
        bestG.put(key(startMask, START), 0.0);

        State bestIncumbent = s0; // lowest-f state seen so far, for fallback if budget runs out
        int expansions = 0;

        while (!open.isEmpty() && expansions < maxExpansions) {
            State cur = open.poll();
            expansions++;

            String curKey = key(cur.mask, cur.current);
            if (cur.g > bestG.getOrDefault(curKey, Double.MAX_VALUE) + 1e-9) {
                continue; // stale entry, a cheaper path to this state was already found
            }

            if (cur.f < bestIncumbent.f) {
                bestIncumbent = cur;
            }

            // Goal: every node visited -> just close the tour back to START.
            if (cur.mask == FULL_MASK) {
                double totalCost = cur.g + dist[cur.current][START];
                Result res = new Result();
                res.tour = reconstructPath(cur);
                res.tour.add(START);
                res.cost = totalCost;
                res.optimal = true;
                res.expansions = expansions;
                return res;
            }

            for (int next = 0; next < n; next++) {
                if ((cur.mask & (1L << next)) != 0) continue; // already visited

                long newMask = cur.mask | (1L << next);
                double newG = cur.g + dist[cur.current][next];
                String newKey = key(newMask, next);

                if (newG < bestG.getOrDefault(newKey, Double.MAX_VALUE) - 1e-9) {
                    bestG.put(newKey, newG);
                    double h = heuristic(next, newMask);
                    State ns = new State(newMask, next, newG, newG + h, cur);
                    open.add(ns);
                }
            }
        }
        return completeGreedily(bestIncumbent, expansions);
    }

    static double heuristic(int current, long mask) {
        List<Integer> unvisited = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((mask & (1L << i)) == 0) unvisited.add(i);
        }

        if (unvisited.isEmpty()) {
            return dist[current][START];
        }

        // S = unvisited nodes plus START (tour must return to START eventually)
        List<Integer> s = new ArrayList<>(unvisited);
        if (!s.contains(START)) s.add(START);

        double mstCost = primMST(s);

        double minEdgeToS = Double.MAX_VALUE;
        for (int node : s) {
            minEdgeToS = Math.min(minEdgeToS, dist[current][node]);
        }

        return mstCost + minEdgeToS;
    }

    /** Prim's algorithm restricted to the given subset of nodes. O(k^2). */
    static double primMST(List<Integer> nodes) {
        int k = nodes.size();
        if (k <= 1) return 0.0;

        boolean[] inTree = new boolean[k];
        double[] minEdge = new double[k];
        Arrays.fill(minEdge, Double.MAX_VALUE);
        minEdge[0] = 0.0;

        double total = 0.0;
        for (int iter = 0; iter < k; iter++) {
            int u = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < k; i++) {
                if (!inTree[i] && minEdge[i] < best) {
                    best = minEdge[i];
                    u = i;
                }
            }
            inTree[u] = true;
            total += best;

            for (int v = 0; v < k; v++) {
                if (!inTree[v]) {
                    double d = dist[nodes.get(u)][nodes.get(v)];
                    if (d < minEdge[v]) minEdge[v] = d;
                }
            }
        }
        return total;
    }

    static Result completeGreedily(State partial, int expansions) {
        List<Integer> path = reconstructPath(partial);
        long mask = partial.mask;
        int current = partial.current;
        double cost = partial.g;

        while (mask != FULL_MASK) {
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if ((mask & (1L << i)) == 0 && dist[current][i] < bestDist) {
                    bestDist = dist[current][i];
                    best = i;
                }
            }
            path.add(best);
            cost += bestDist;
            mask |= (1L << best);
            current = best;
        }

        cost += dist[current][START];
        path.add(START);

        Result res = new Result();
        res.tour = path;
        res.cost = cost;
        res.optimal = false;
        res.expansions = expansions;
        return res;
    }

    static List<Integer> reconstructPath(State state) {
        LinkedList<Integer> path = new LinkedList<>();
        State cur = state;
        while (cur != null) {
            path.addFirst(cur.current);
            cur = cur.parent;
        }
        return new ArrayList<>(path);
    }

    static String key(long mask, int current) {
        return mask + "_" + current;
    }
}

















// For reference Purpose...... 






// public class TSPAStarMST {
 
//     static int n;
//     static double[][] dist;
//     static final int START = 0;
//     static long FULL_MASK;
 
//     public static void main(String[] args) {
//         Scanner scanner = new Scanner(System.in);
 
//         n = readNodeCount(scanner);
//         long seed = readSeed(scanner);
 
//         generateRandomCompleteGraph(n, seed);
//         FULL_MASK = (n == 64) ? -1L : ((1L << n) - 1);
 
//         System.out.println("Generated a complete graph with " + n + " nodes.");
//         System.out.println("Running EXACT A* + MST search (no cap, no approximation)...");
//         System.out.println("This may take a very long time or exhaust memory for n > ~20. Press Ctrl+C to abort.");
 
//         long t0 = System.currentTimeMillis();
//         Result result = solveAStarMST();
//         long t1 = System.currentTimeMillis();
 
//         System.out.println();
//         System.out.println("===== RESULT =====");
//         if (result == null) {
//             System.out.println("Search space exhausted without finding a solution (should not happen on a connected complete graph).");
//         } else {
//             System.out.println("Status       : OPTIMAL (exact search, guaranteed shortest tour)");
//             System.out.println("Nodes expanded: " + result.expansions);
//             System.out.println("Time         : " + (t1 - t0) + " ms");
//             System.out.println("Tour cost    : " + String.format("%.2f", result.cost));
//             System.out.println("Tour         : " + result.tour);
//         }
//     }
 
//     // ---------------------------------------------------------------
//     // User input
//     // ---------------------------------------------------------------
 
//     static int readNodeCount(Scanner scanner) {
//         while (true) {
//             System.out.print("Enter number of nodes (>50 recommended, max 60 for this bitmask implementation): ");
//             String line = scanner.nextLine().trim();
//             try {
//                 int val = Integer.parseInt(line);
//                 if (val >= 2 && val <= 60) return val;
//                 System.out.println("Please enter a value between 2 and 60.");
//             } catch (NumberFormatException e) {
//                 System.out.println("Please enter a valid integer.");
//             }
//         }
//     }
 
//     static long readSeed(Scanner scanner) {
//         System.out.print("Enter random seed (press Enter for a random one): ");
//         String line = scanner.nextLine().trim();
//         if (line.isEmpty()) return System.nanoTime();
//         try {
//             return Long.parseLong(line);
//         } catch (NumberFormatException e) {
//             System.out.println("Invalid seed, using a random one.");
//             return System.nanoTime();
//         }
//     }
 
//     // ---------------------------------------------------------------
//     // Graph generation: random 2D points, Euclidean distances
//     // ---------------------------------------------------------------
 
//     static void generateRandomCompleteGraph(int n, long seed) {
//         Random rand = new Random(seed);
//         double[] x = new double[n];
//         double[] y = new double[n];
//         for (int i = 0; i < n; i++) {
//             x[i] = rand.nextDouble() * 1000.0;
//             y[i] = rand.nextDouble() * 1000.0;
//         }
//         dist = new double[n][n];
//         for (int i = 0; i < n; i++) {
//             for (int j = 0; j < n; j++) {
//                 if (i == j) {
//                     dist[i][j] = 0;
//                 } else {
//                     double dx = x[i] - x[j];
//                     double dy = y[i] - y[j];
//                     dist[i][j] = Math.sqrt(dx * dx + dy * dy);
//                 }
//             }
//         }
//     }
 
//     // ---------------------------------------------------------------
//     // A* state
//     // ---------------------------------------------------------------
 
//     static class State implements Comparable<State> {
//         long mask;
//         int current;
//         double g;
//         double f;
//         State parent;
 
//         State(long mask, int current, double g, double f, State parent) {
//             this.mask = mask;
//             this.current = current;
//             this.g = g;
//             this.f = f;
//             this.parent = parent;
//         }
 
//         @Override
//         public int compareTo(State o) {
//             return Double.compare(this.f, o.f);
//         }
//     }
 
//     static class Result {
//         List<Integer> tour;
//         double cost;
//         int expansions;
//     }
 
//     // ---------------------------------------------------------------
//     // A* main loop -- EXACT, unbounded. No cap, no approximation.
//     // ---------------------------------------------------------------
 
//     static Result solveAStarMST() {
//         PriorityQueue<State> open = new PriorityQueue<>();
//         Map<String, Double> bestG = new HashMap<>();
 
//         long startMask = 1L << START;
//         double h0 = heuristic(START, startMask);
//         State s0 = new State(startMask, START, 0.0, h0, null);
//         open.add(s0);
//         bestG.put(key(startMask, START), 0.0);
 
//         int expansions = 0;
 
//         while (!open.isEmpty()) {
//             State cur = open.poll();
//             expansions++;
 
//             if (expansions % 100_000 == 0) {
//                 System.out.println("...expanded " + expansions + " states, open set size = " + open.size());
//             }
 
//             String curKey = key(cur.mask, cur.current);
//             if (cur.g > bestG.getOrDefault(curKey, Double.MAX_VALUE) + 1e-9) {
//                 continue; // stale entry, a cheaper path to this state was already found
//             }
 
//             // Goal: every node visited -> just close the tour back to START.
//             if (cur.mask == FULL_MASK) {
//                 double totalCost = cur.g + dist[cur.current][START];
//                 Result res = new Result();
//                 res.tour = reconstructPath(cur);
//                 res.tour.add(START);
//                 res.cost = totalCost;
//                 res.expansions = expansions;
//                 return res;
//             }
 
//             for (int next = 0; next < n; next++) {
//                 if ((cur.mask & (1L << next)) != 0) continue; // already visited
 
//                 long newMask = cur.mask | (1L << next);
//                 double newG = cur.g + dist[cur.current][next];
//                 String newKey = key(newMask, next);
 
//                 if (newG < bestG.getOrDefault(newKey, Double.MAX_VALUE) - 1e-9) {
//                     bestG.put(newKey, newG);
//                     double h = heuristic(next, newMask);
//                     State ns = new State(newMask, next, newG, newG + h, cur);
//                     open.add(ns);
//                 }
//             }
//         }
 
//         return null; // open exhausted without reaching a full tour (won't happen on a connected complete graph)
//     }
 
//     // ---------------------------------------------------------------
//     // MST-based admissible heuristic
//     // ---------------------------------------------------------------
 
//     static double heuristic(int current, long mask) {
//         List<Integer> unvisited = new ArrayList<>();
//         for (int i = 0; i < n; i++) {
//             if ((mask & (1L << i)) == 0) unvisited.add(i);
//         }
 
//         if (unvisited.isEmpty()) {
//             return dist[current][START];
//         }
 
//         // S = unvisited nodes plus START (tour must return to START eventually)
//         List<Integer> s = new ArrayList<>(unvisited);
//         if (!s.contains(START)) s.add(START);
 
//         double mstCost = primMST(s);
 
//         double minEdgeToS = Double.MAX_VALUE;
//         for (int node : s) {
//             minEdgeToS = Math.min(minEdgeToS, dist[current][node]);
//         }
 
//         return mstCost + minEdgeToS;
//     }
 
//     /** Prim's algorithm restricted to the given subset of nodes. O(k^2). */
//     static double primMST(List<Integer> nodes) {
//         int k = nodes.size();
//         if (k <= 1) return 0.0;
 
//         boolean[] inTree = new boolean[k];
//         double[] minEdge = new double[k];
//         Arrays.fill(minEdge, Double.MAX_VALUE);
//         minEdge[0] = 0.0;
 
//         double total = 0.0;
//         for (int iter = 0; iter < k; iter++) {
//             int u = -1;
//             double best = Double.MAX_VALUE;
//             for (int i = 0; i < k; i++) {
//                 if (!inTree[i] && minEdge[i] < best) {
//                     best = minEdge[i];
//                     u = i;
//                 }
//             }
//             inTree[u] = true;
//             total += best;
 
//             for (int v = 0; v < k; v++) {
//                 if (!inTree[v]) {
//                     double d = dist[nodes.get(u)][nodes.get(v)];
//                     if (d < minEdge[v]) minEdge[v] = d;
//                 }
//             }
//         }
//         return total;
//     }
 
//     static List<Integer> reconstructPath(State state) {
//         LinkedList<Integer> path = new LinkedList<>();
//         State cur = state;
//         while (cur != null) {
//             path.addFirst(cur.current);
//             cur = cur.parent;
//         }
//         return new ArrayList<>(path);
//     }
 
//     static String key(long mask, int current) {
//         return mask + "_" + current;
//     }
// }