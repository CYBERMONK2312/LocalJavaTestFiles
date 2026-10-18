import java.util.*;

/**
 * SMA* (Simplified Memory-bounded A*) for the Travelling Salesperson Problem, up to 50 cities.
 *
 * Instance : random cities in a 1000 x 1000 square, Euclidean distances (complete graph).
 *            The START city (initial state) is also chosen at random from the seed.
 * State    : (set of visited cities as a 64-bit mask, current city).
 * g, h     : g = distance travelled so far.
 *            h = MST(unvisited cities + START) + cheapest edge from the current city into that set.
 *            This is a lower bound on the remaining cost (admissible and consistent).
 *
 * SMA* rules implemented (AIMA, 2nd ed., "memory-bounded heuristic search"):
 *   - expand the DEEPEST, LEAST-f leaf, generating ONE successor at a time;
 *   - when the node limit is reached, FORGET the SHALLOWEST, HIGHEST-f leaf and back its f up
 *     into its parent (the parent remembers the best forgotten child, so the subtree is
 *     regenerated only when everything else looks worse);
 *   - once all successors of a node have been generated, its f becomes the min of its
 *     successors' f values (backed up towards the root);
 *   - f never decreases along a path (path-max).
 *
 * Engineering choices (all optional to the algorithm, all aimed at speed / memory):
 *   - an initial upper bound (nearest-neighbour from every city + 2-opt + Or-opt) is used to prune
 *     every node with f >= bound. The search ends with a PROOF of optimality as soon as no open
 *     node has f < bound. Without the bound (option "n") plain SMA* is run.
 *   - states already in memory with equal/lower g are not duplicated (dominance pruning);
 *   - one-successor-at-a-time generation, indexed binary heaps (O(log N) update/removal, no stale
 *     entries), flat distance matrix, allocation-free heuristic, direct-mapped MST cache.
 *
 * Because f values are always lower bounds, forgetting nodes can only cost time, never correctness.
 * TSP is NP-hard: for large n (roughly > 25-30) the search will normally stop at the time limit
 * and report the best tour found, clearly labelled as not proven optimal.
 */
public class TSPSMAStar {

    // ------------------------------------------------------------------ problem data
    static final double INF = Double.POSITIVE_INFINITY;
    static final double EPS = 1e-7;
    static int n, START;
    static double[] d;          // flattened distance matrix, d[i * n + j]
    static int[][] order;       // order[i] = the other cities sorted by distance from i
    static long FULL;           // mask with all n cities visited

    // ------------------------------------------------------------------ SMA* state
    static int maxNodes, nodeCount;
    static long ageCounter;
    static Node root;
    static NodeHeap open;       // expandable nodes: best = least f, then deepest, then newest
    static NodeHeap leaves;     // forgettable leaves: worst = highest f, then shallowest, then oldest
    static StateMap seen;       // (mask, city) -> node in memory, for dominance pruning
    static double ub;           // best complete tour cost known (pruning bound)
    static int[] incumbent;     // best complete tour known
    static boolean rootDead;

    // statistics
    static long generated, forgottenCnt, killedCnt, prunedBound, prunedDom, peakNodes, mstEvals, mstHits;

    // heuristic scratch space + MST cache
    static int[] L, nd;
    static double[] kv;
    static final int CACHE_BITS = 18;
    static long[] cacheKey;
    static double[] cacheVal;

    // ================================================================== tree node
    static final class Node {
        final long mask;            // visited cities
        final Node parent;
        final int city, depth;      // depth = number of visited cities
        final double g;
        final long age;
        double f;                   // current (backed-up) f value
        double forgotten = INF;     // best f among forgotten children
        int forgottenCity = -1;     // which child that was (hint for regeneration)
        long childMask;             // children currently in memory
        long genMask;               // children generated at least once
        long pruned;                // children that can never help (bound / dominance / dead)
        int childCount;
        Node firstChild, nextSib, prevSib;
        int pos0 = -1, pos1 = -1;   // positions in the two heaps

        Node(long mask, Node parent, int city, double g, double f) {
            this.mask = mask; this.parent = parent; this.city = city;
            this.g = g; this.f = f;
            this.depth = Long.bitCount(mask);
            this.age = ++ageCounter;
        }
    }

    // ================================================================== indexed binary heap
    static final class NodeHeap {
        final Node[] a;
        int size;
        final boolean isOpen;

        NodeHeap(int capacity, boolean isOpen) { a = new Node[capacity]; this.isOpen = isOpen; }

        private boolean before(Node x, Node y) {
            if (isOpen) {                                   // least f, deepest, newest
                if (x.f != y.f) return x.f < y.f;
                if (x.depth != y.depth) return x.depth > y.depth;
                return x.age > y.age;
            }                                               // highest f, shallowest, oldest
            if (x.f != y.f) return x.f > y.f;
            if (x.depth != y.depth) return x.depth < y.depth;
            return x.age < y.age;
        }
        private int pos(Node x) { return isOpen ? x.pos0 : x.pos1; }
        private void setPos(Node x, int p) { if (isOpen) x.pos0 = p; else x.pos1 = p; }

        Node peek() { return size == 0 ? null : a[0]; }

        void add(Node x) {
            a[size] = x; setPos(x, size); size++;
            siftUp(size - 1);
        }
        void remove(Node x) {
            int i = pos(x);
            if (i < 0) return;
            setPos(x, -1);
            int last = --size;
            if (i == last) { a[last] = null; return; }
            Node m = a[last]; a[last] = null; a[i] = m; setPos(m, i);
            if (!siftUp(i)) siftDown(i);
        }
        void fix(Node x) {
            int i = pos(x);
            if (i >= 0 && !siftUp(i)) siftDown(i);
        }
        private boolean siftUp(int i) {
            Node x = a[i]; int start = i;
            while (i > 0) {
                int p = (i - 1) >>> 1; Node y = a[p];
                if (!before(x, y)) break;
                a[i] = y; setPos(y, i); i = p;
            }
            a[i] = x; setPos(x, i);
            return i != start;
        }
        private void siftDown(int i) {
            Node x = a[i]; int half = size >>> 1;
            while (i < half) {
                int c = 2 * i + 1; Node y = a[c]; int r = c + 1;
                if (r < size && before(a[r], y)) { c = r; y = a[r]; }
                if (!before(y, x)) break;
                a[i] = y; setPos(y, i); i = c;
            }
            a[i] = x; setPos(x, i);
        }
    }

    // ================================================================== open-addressing hash map
    static final class StateMap {
        final long[] keys; final Node[] vals; final int mask, shift;

        StateMap(int minCapacity) {
            int cap = 16, bits = 4;
            while (cap < minCapacity) { cap <<= 1; bits++; }
            keys = new long[cap]; vals = new Node[cap]; mask = cap - 1; shift = 64 - bits;
        }
        private int hash(long k) { return (int) ((k * 0x9E3779B97F4A7C15L) >>> shift); }

        Node get(long key) {
            int i = hash(key);
            while (keys[i] != 0) { if (keys[i] == key) return vals[i]; i = (i + 1) & mask; }
            return null;
        }
        void put(long key, Node v) {
            int i = hash(key);
            while (keys[i] != 0 && keys[i] != key) i = (i + 1) & mask;
            keys[i] = key; vals[i] = v;
        }
        /** Removes the entry only if it still points at 'expect'. */
        void remove(long key, Node expect) {
            int i = hash(key);
            while (keys[i] != 0 && keys[i] != key) i = (i + 1) & mask;
            if (keys[i] == 0 || vals[i] != expect) return;
            int j = i;
            while (true) {                                  // backward-shift deletion
                j = (j + 1) & mask;
                if (keys[j] == 0) break;
                int h = hash(keys[j]);
                boolean stays = (i <= j) ? (i < h && h <= j) : (i < h || h <= j);
                if (stays) continue;
                keys[i] = keys[j]; vals[i] = vals[j]; i = j;
            }
            keys[i] = 0; vals[i] = null;
        }
    }

    static final class Result {
        double cost; int[] tour; boolean proved; String status; boolean fallback;
        double initialBound; long millis;
    }

    // ================================================================== instance generation
    static void generateInstance(int cities, long seed) {
        n = cities;
        Random rnd = new Random(seed);
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = rnd.nextDouble() * 1000.0; y[i] = rnd.nextDouble() * 1000.0; }
        START = rnd.nextInt(n);                              // random initial state
        d = new double[n * n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i * n + j] = (i == j) ? 0 : Math.hypot(x[i] - x[j], y[i] - y[j]);
        FULL = (1L << n) - 1;
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

    // ================================================================== initial tour (upper bound)
    static double tourCost(int[] t) {
        double s = 0;
        for (int i = 0; i < n; i++) s += d[t[i] * n + t[(i + 1) % n]];
        return s;
    }

    static int[] nearestNeighbourTour(int s) {
        boolean[] seenCity = new boolean[n];
        int[] t = new int[n];
        t[0] = s; seenCity[s] = true;
        for (int k = 1; k < n; k++) {
            int cur = t[k - 1];
            for (int c : order[cur]) if (!seenCity[c]) { t[k] = c; seenCity[c] = true; break; }
        }
        return t;
    }

    static void reverse(int[] t, int i, int j) {
        while (i < j) { int tmp = t[i]; t[i++] = t[j]; t[j--] = tmp; }
    }

    static boolean twoOpt(int[] t) {
        boolean any = false, improved;
        do {
            improved = false;
            for (int i = 0; i < n - 1; i++)
                for (int j = i + 2; j < n; j++) {
                    if (i == 0 && j == n - 1) continue;
                    int a = t[i], b = t[i + 1], c = t[j], e = t[(j + 1) % n];
                    double delta = d[a * n + c] + d[b * n + e] - d[a * n + b] - d[c * n + e];
                    if (delta < -1e-9) { reverse(t, i + 1, j); improved = true; any = true; }
                }
        } while (improved);
        return any;
    }

    /** Moves a segment of 1..3 cities (optionally reversed) to a better position. */
    static boolean orOpt(int[] t) {
        for (int len = 1; len <= 3; len++) {
            if (n - len < 3) continue;
            for (int i = 0; i < n; i++) {
                int prev = t[(i - 1 + n) % n], first = t[i], last = t[(i + len - 1) % n], next = t[(i + len) % n];
                double removeGain = d[prev * n + first] + d[last * n + next] - d[prev * n + next];
                for (int k = 0; k < n - len - 1; k++) {
                    int a = t[(i + len + k) % n], b = t[(i + len + k + 1) % n];
                    double base = d[a * n + b];
                    double fwd = d[a * n + first] + d[last * n + b] - base;
                    double rev = d[a * n + last] + d[first * n + b] - base;
                    boolean useRev = rev < fwd;
                    if (Math.min(fwd, rev) < removeGain - 1e-9) {
                        int[] nt = new int[n];
                        int p = 0;
                        for (int q = 0; q <= k; q++) nt[p++] = t[(i + len + q) % n];
                        if (useRev) for (int q = len - 1; q >= 0; q--) nt[p++] = t[(i + q) % n];
                        else        for (int q = 0; q < len; q++)      nt[p++] = t[(i + q) % n];
                        for (int q = k + 1; q < n - len; q++) nt[p++] = t[(i + len + q) % n];
                        System.arraycopy(nt, 0, t, 0, n);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static int[] bestLocalSearchTour() {
        int[] best = null; double bestCost = INF;
        for (int s = 0; s < n; s++) {
            int[] t = nearestNeighbourTour(s);
            while (twoOpt(t) | orOpt(t)) { /* repeat until no move improves */ }
            double c = tourCost(t);
            if (c < bestCost - 1e-12) { bestCost = c; best = t; }
        }
        return rotateToStart(best);
    }

    static int[] rotateToStart(int[] t) {
        int p = 0;
        while (t[p] != START) p++;
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = t[(p + i) % n];
        return r;
    }

    // ================================================================== heuristic
    /** h(c, mask) = MST(unvisited + START) + cheapest edge from c into that set. */
    static double h(int c, long mask) {
        long unv = FULL & ~mask;
        if (unv == 0) return d[c * n + START];
        long set = unv | (1L << START);
        double minEdge = INF;
        int k = 0;
        for (long m = set; m != 0; m &= m - 1) {
            int v = Long.numberOfTrailingZeros(m);
            L[k++] = v;
            double w = d[c * n + v];
            if (w < minEdge) minEdge = w;
        }
        int slot = (int) ((set * 0x9E3779B97F4A7C15L) >>> (64 - CACHE_BITS));
        double mst;
        if (cacheKey[slot] == set) { mst = cacheVal[slot]; mstHits++; }
        else { mst = prim(k); cacheKey[slot] = set; cacheVal[slot] = mst; mstEvals++; }
        return mst + minEdge;
    }

    /** Prim's algorithm over the k cities in L[0..k-1]; O(k^2), no allocation. */
    static double prim(int k) {
        if (k <= 1) return 0;
        int u0 = L[0], rc = k - 1;
        for (int i = 0; i < rc; i++) { nd[i] = L[i + 1]; kv[i] = d[u0 * n + nd[i]]; }
        double total = 0;
        while (rc > 0) {
            int bi = 0; double bk = kv[0];
            for (int i = 1; i < rc; i++) if (kv[i] < bk) { bk = kv[i]; bi = i; }
            total += bk;
            int u = nd[bi]; rc--; nd[bi] = nd[rc]; kv[bi] = kv[rc];
            int base = u * n;
            for (int i = 0; i < rc; i++) { double w = d[base + nd[i]]; if (w < kv[i]) kv[i] = w; }
        }
        return total;
    }

    // ================================================================== SMA* machinery
    static void link(Node p, Node s) {
        s.nextSib = p.firstChild; s.prevSib = null;
        if (p.firstChild != null) p.firstChild.prevSib = s;
        p.firstChild = s;
    }
    static void unlink(Node p, Node s) {
        if (s.prevSib != null) s.prevSib.nextSib = s.nextSib; else p.firstChild = s.nextSib;
        if (s.nextSib != null) s.nextSib.prevSib = s.prevSib;
        s.nextSib = s.prevSib = null;
    }
    static long key(Node x) { return (x.mask << 6) | x.city; }

    /** A node is expandable (in 'open') iff some successor is neither in memory nor pruned. */
    static void syncOpen(Node x) {
        long cand = FULL & ~x.mask & ~x.childMask & ~x.pruned;
        if (cand == 0) { if (x.pos0 >= 0) open.remove(x); }
        else if (x.pos0 < 0) open.add(x);
    }

    /** NEXT-SUCCESSOR: never-generated children first (nearest first), then forgotten ones. */
    static int nextSuccessor(Node x) {
        long cand = FULL & ~x.mask & ~x.childMask & ~x.pruned;
        if (cand == 0) return -1;
        if (x.forgottenCity >= 0 && ((cand >>> x.forgottenCity) & 1) != 0) return x.forgottenCity;
        long fresh = cand & ~x.genMask;
        long pick = fresh != 0 ? fresh : cand;
        for (int c : order[x.city]) if (((pick >>> c) & 1) != 0) return c;
        return -1;
    }

    static void settle(Node x) { syncOpen(x); backup(x); }

    /** Back f up the tree: once all successors were generated, f = min over successors. */
    static void backup(Node x) {
        while (x != null) {
            long unv = FULL & ~x.mask;
            if ((unv & ~x.genMask & ~x.pruned) != 0) return;        // not all successors generated yet
            if ((x.genMask & ~x.childMask & ~x.pruned & unv) == 0) { x.forgotten = INF; x.forgottenCity = -1; }
            double nf = x.forgotten;
            for (Node c = x.firstChild; c != null; c = c.nextSib) if (c.f < nf) nf = c.f;
            if (nf == INF) {                                        // no successor can ever help: dead end
                if (x == root) { rootDead = true; return; }
                Node p = x.parent;
                kill(x);
                p.pruned |= 1L << x.city;
                syncOpen(p);
                x = p;
                continue;
            }
            if (nf <= x.f) return;
            x.f = nf;
            if (x.pos0 >= 0) open.fix(x);
            if (x.pos1 >= 0) leaves.fix(x);
            x = x.parent;
        }
    }

    /** Removes a childless node that is a proven dead end. */
    static void kill(Node x) {
        Node p = x.parent;
        open.remove(x); leaves.remove(x); seen.remove(key(x), x);
        unlink(p, x);
        p.childMask &= ~(1L << x.city); p.childCount--;
        nodeCount--; killedCnt++;
        if (p.childCount == 0 && p != root && p.pos1 < 0) leaves.add(p);
    }

    /** Forgets a leaf, remembering its f value in the parent. */
    static void forget(Node v) {
        Node p = v.parent;
        open.remove(v); leaves.remove(v); seen.remove(key(v), v);
        unlink(p, v);
        p.childMask &= ~(1L << v.city); p.childCount--;
        if (v.f < p.forgotten) { p.forgotten = v.f; p.forgottenCity = v.city; }
        nodeCount--; forgottenCnt++;
        if (p.pos0 < 0) open.add(p);                               // p again has a successor not in memory
        if (p.childCount == 0 && p != root && p.pos1 < 0) leaves.add(p);
    }

    /** Frees one node: forgets the shallowest, highest-f leaf (never 'protect'). */
    static boolean makeRoom(Node protect) {
        boolean wasLeaf = protect.pos1 >= 0;
        if (wasLeaf) leaves.remove(protect);
        Node v = leaves.peek();
        if (v == null) { if (wasLeaf) leaves.add(protect); return false; }
        forget(v);
        return true;
    }

    static int[] buildTour(Node cur, int last) {
        int[] t = new int[n];
        int k = cur.depth - 1;
        for (Node x = cur; x != null; x = x.parent) t[k--] = x.city;
        t[cur.depth] = last;
        return t;
    }

    /** Generates ONE successor of cur (city c). Returns false if memory is too small to continue. */
    static boolean expand(Node cur, int c) {
        long bit = 1L << c;
        long mask2 = cur.mask | bit;
        double g2 = cur.g + d[cur.city * n + c];
        generated++;

        if (mask2 == FULL) {                                        // last city: a complete tour
            double total = g2 + d[c * n + START];
            if (total < ub - EPS) { ub = total; incumbent = buildTour(cur, c); }
            cur.pruned |= bit; settle(cur);
            return true;
        }
        long k = (mask2 << 6) | c;
        Node ex = seen.get(k);
        if (ex != null && ex.g <= g2 + 1e-12) {                     // same state already reached at least as cheaply
            prunedDom++; cur.pruned |= bit; settle(cur);
            return true;
        }
        double f2 = g2 + h(c, mask2);
        if (c == cur.forgottenCity) { f2 = Math.max(f2, cur.forgotten); cur.forgottenCity = -1; }
        if (f2 < cur.f) f2 = cur.f;                                 // path-max: f never decreases
        if (f2 >= ub - EPS) {                                       // cannot beat the best known tour
            prunedBound++; cur.pruned |= bit; settle(cur);
            return true;
        }
        if (nodeCount >= maxNodes && !makeRoom(cur)) return false;

        Node s = new Node(mask2, cur, c, g2, f2);
        link(cur, s);
        cur.childMask |= bit; cur.genMask |= bit; cur.childCount++;
        if (cur.pos1 >= 0) leaves.remove(cur);                      // cur is no longer a leaf
        open.add(s); leaves.add(s); seen.put(k, s);
        nodeCount++;
        if (nodeCount > peakNodes) peakNodes = nodeCount;
        settle(cur);
        return true;
    }

    // ================================================================== driver
    static Result solve(boolean seedBound, double seconds) {
        long t0 = System.nanoTime(), deadline = t0 + (long) (seconds * 1e9);

        L = new int[n]; nd = new int[n]; kv = new double[n];
        cacheKey = new long[1 << CACHE_BITS]; cacheVal = new double[1 << CACHE_BITS];
        open = new NodeHeap(maxNodes + 8, true);
        leaves = new NodeHeap(maxNodes + 8, false);
        seen = new StateMap(2 * maxNodes);
        nodeCount = 0; ageCounter = 0; rootDead = false;
        generated = forgottenCnt = killedCnt = prunedBound = prunedDom = peakNodes = mstEvals = mstHits = 0;

        int[] ls = bestLocalSearchTour();
        double lsCost = tourCost(ls);
        incumbent = null; ub = INF;
        if (seedBound) { incumbent = ls.clone(); ub = lsCost; }

        root = new Node(1L << START, null, START, 0, 0);
        root.f = h(START, root.mask);
        open.add(root); nodeCount = 1; peakNodes = 1;

        String stop = null; boolean proved = false;
        long iter = 0;
        while (true) {
            if ((++iter & 1023) == 0 && System.nanoTime() > deadline) { stop = "time limit reached"; break; }
            Node cur = open.peek();
            if (rootDead || cur == null || cur.f >= ub - EPS) { proved = true; break; }
            int c = nextSuccessor(cur);
            if (c < 0) { syncOpen(cur); continue; }
            if (!expand(cur, c)) { stop = "memory limit too small to continue"; break; }
        }

        Result r = new Result();
        r.initialBound = lsCost;
        if (incumbent == null) { incumbent = ls; r.fallback = true; }
        r.tour = incumbent; r.cost = tourCost(incumbent); r.proved = proved && !r.fallback;
        if (proved && incumbent != null && !r.fallback)
            r.status = "OPTIMAL (proved: no open node can beat this tour)";
        else if (r.fallback)
            r.status = "NOT PROVEN - SMA* found no complete tour (" + stop + "); showing the local-search tour";
        else
            r.status = "BEST FOUND, not proven optimal (" + stop + ")";
        r.millis = (System.nanoTime() - t0) / 1_000_000;
        return r;
    }

    static Result run(int cities, long seed, int memNodes, double seconds, boolean seedBound) {
        generateInstance(cities, seed);
        maxNodes = memNodes;
        return solve(seedBound, seconds);
    }

    // ================================================================== console I/O
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int cities = readInt(sc, "Number of cities (2-50) [30]: ", 2, 50, 30);
        int mem = readInt(sc, "Memory limit in nodes (>= 100, ~110 bytes each) [500000]: ", 100, 20_000_000, 500_000);
        double secs = readDouble(sc, "Time limit in seconds [30]: ", 0.1, 86400, 30);
        long seed = readSeed(sc);
        boolean bound = readYesNo(sc, "Seed an initial upper bound with 2-opt/Or-opt to prune? (Y/n) [Y]: ", true);

        generateInstance(cities, seed);
        maxNodes = mem;
        System.out.println("\nRandom instance: " + n + " cities, start city (random initial state) = " + START + ", seed = " + seed);
        Result r = solve(bound, secs);

        System.out.println("\n===== RESULT =====");
        System.out.println("Status          : " + r.status);
        System.out.printf ("Tour cost       : %.2f%n", r.cost);
        System.out.printf ("Initial bound   : %.2f (nearest-neighbour + 2-opt + Or-opt)%n", r.initialBound);
        System.out.println("Time            : " + r.millis + " ms");
        System.out.println("Nodes generated : " + generated);
        System.out.println("Forgotten       : " + forgottenCnt + "   dead ends removed: " + killedCnt);
        System.out.println("Pruned by bound : " + prunedBound + "   by dominance: " + prunedDom);
        System.out.println("Peak nodes held : " + peakNodes + " / " + maxNodes);
        long totalH = mstEvals + mstHits;
        System.out.printf ("MST evaluations : %d (cache hit rate %.1f%%)%n", mstEvals, totalH == 0 ? 0.0 : 100.0 * mstHits / totalH);
        StringBuilder sb = new StringBuilder("Tour            : ");
        for (int c : r.tour) sb.append(c).append(" -> ");
        sb.append(r.tour[0]);
        System.out.println(sb);
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
