import java.util.*;

public class BidirectionalIDS {

    static int N;      // board dimension (N x N)
    static int SIZE;   // N * N
    static int[] goal;

    static final int[] DR = {-1, 1, 0, 0};
    static final int[] DC = {0, 0, -1, 1};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        N = readDimension(scanner);
        int maxDepthPerSide = readMaxDepth(scanner);
        SIZE = N * N;

        goal = new int[SIZE];
        for (int i = 0; i < SIZE - 1; i++) goal[i] = i + 1;
        goal[SIZE - 1] = 0;

        int[] initial = generateSolvableRandomState();

        System.out.println("Board size: " + N + " x " + N);
        System.out.println("Initial state:");
        printState(initial);
        System.out.println("Goal state:");
        printState(goal);

        long startTime = System.currentTimeMillis();
        List<int[]> solution = solveBidirectionalIDS(initial, goal, maxDepthPerSide);
        long elapsed = System.currentTimeMillis() - startTime;

        if (solution == null) {
            System.out.println("No solution found within max depth per side ("
                    + maxDepthPerSide + "). Time: " + elapsed + " ms");
        } else {
            System.out.println("Solved in " + (solution.size() - 1) + " moves. Time: " + elapsed + " ms");
            System.out.println("---- Solution path ----");
            for (int[] s : solution) {
                printState(s);
            }
        }
    }


    static int readDimension(Scanner scanner) {
        int n;
        while (true) {
            System.out.print("Enter N (dimension of the square puzzle, e.g. 3 for 8-puzzle): ");
            String line = scanner.nextLine().trim();
            try {
                n = Integer.parseInt(line);
                if (n >= 2) break;
                System.out.println("N must be 2 or greater. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
        return n;
    }

    static int readMaxDepth(Scanner scanner) {
        System.out.print("Enter max depth PER SIDE (press Enter for default 15): ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) return 15;
        try {
            int depth = Integer.parseInt(line);
            return depth > 0 ? depth : 15;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input, using default max depth of 15.");
            return 15;
        }
    }


    static int[] generateSolvableRandomState() {
        Random rand = new Random();
        int[] state = Arrays.copyOf(goal, SIZE);
        do {
            shuffle(state, rand);
        } while (!isSolvable(state) || Arrays.equals(state, goal));
        return state;
    }

    static void shuffle(int[] arr, Random rand) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    static int countInversions(int[] state) {
        int inversions = 0;
        for (int i = 0; i < SIZE; i++) {
            if (state[i] == 0) continue;
            for (int j = i + 1; j < SIZE; j++) {
                if (state[j] == 0) continue;
                if (state[i] > state[j]) inversions++;
            }
        }
        return inversions;
    }

    static boolean isSolvable(int[] state) {
        int inversions = countInversions(state);
        if (N % 2 == 1) {
            return inversions % 2 == 0;
        } else {
            int blankIndex = indexOf(state, 0);
            int rowFromTop = blankIndex / N;
            int rowFromBottom = N - rowFromTop;
            return (inversions + rowFromBottom) % 2 == 1;
        }
    }

    static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return -1;
    }


    static List<int[]> solveBidirectionalIDS(int[] start, int[] goalState, int maxDepthPerSide) {

        String startKey = Arrays.toString(start);
        String goalKey = Arrays.toString(goalState);

        Map<String, List<int[]>> forwardVisited = new HashMap<>();
        Map<String, List<int[]>> backwardVisited = new HashMap<>();

        List<int[]> startPath = new ArrayList<>();
        startPath.add(start);
        forwardVisited.put(startKey, startPath);

        List<int[]> goalPath = new ArrayList<>();
        goalPath.add(goalState);
        backwardVisited.put(goalKey, goalPath);

        // Trivial case: start already equals goal.
        if (startKey.equals(goalKey)) {
            return startPath;
        }

        for (int d = 1; d <= maxDepthPerSide; d++) {
            System.out.println("Expanding both sides to depth: " + d);

            // ---- Grow the FORWARD frontier by one level ----
            Map<String, List<int[]>> forwardFrontier = frontierAtDepth(start, d);
            for (Map.Entry<String, List<int[]>> e : forwardFrontier.entrySet()) {
                forwardVisited.putIfAbsent(e.getKey(), e.getValue());
            }
            List<int[]> meetForward = checkIntersection(forwardFrontier, backwardVisited,
                    forwardVisited, backwardVisited);
            if (meetForward != null) return meetForward;

            // ---- Grow the BACKWARD frontier by one level ----
            Map<String, List<int[]>> backwardFrontier = frontierAtDepth(goalState, d);
            for (Map.Entry<String, List<int[]>> e : backwardFrontier.entrySet()) {
                backwardVisited.putIfAbsent(e.getKey(), e.getValue());
            }
            List<int[]> meetBackward = checkIntersection(backwardFrontier, forwardVisited,
                    forwardVisited, backwardVisited);
            if (meetBackward != null) return meetBackward;
        }

        return null; // no meeting point found within maxDepthPerSide on each side
    }

    static List<int[]> checkIntersection(Map<String, List<int[]>> newFrontier,
                                          Map<String, List<int[]>> otherSideVisited,
                                          Map<String, List<int[]>> forwardVisited,
                                          Map<String, List<int[]>> backwardVisited) {
        for (String key : newFrontier.keySet()) {
            if (otherSideVisited.containsKey(key)) {
                return buildFullPath(key, forwardVisited, backwardVisited);
            }
        }
        return null;
    }


    static List<int[]> buildFullPath(String meetKey,
                                      Map<String, List<int[]>> forwardVisited,
                                      Map<String, List<int[]>> backwardVisited) {
        List<int[]> forwardPath = forwardVisited.get(meetKey);   // start ... meet
        List<int[]> backwardPath = backwardVisited.get(meetKey); // goal ... meet

        List<int[]> fullPath = new ArrayList<>(forwardPath);

        List<int[]> tail = new ArrayList<>(backwardPath.subList(0, backwardPath.size() - 1));
        Collections.reverse(tail);
        fullPath.addAll(tail);

        return fullPath;
    }


    static Map<String, List<int[]>> frontierAtDepth(int[] start, int depth) {
        Map<String, List<int[]>> frontier = new HashMap<>();
        List<int[]> path = new ArrayList<>();
        path.add(start);
        Set<String> onPath = new HashSet<>();
        onPath.add(Arrays.toString(start));

        collectFrontier(start, depth, path, onPath, frontier);
        return frontier;
    }

    static void collectFrontier(int[] state, int depthRemaining, List<int[]> path,
                                 Set<String> onPath, Map<String, List<int[]>> frontier) {
        if (depthRemaining == 0) {
            String key = Arrays.toString(state);
            frontier.putIfAbsent(key, new ArrayList<>(path));
            return;
        }

        int blankIndex = indexOf(state, 0);
        int row = blankIndex / N;
        int col = blankIndex % N;

        for (int m = 0; m < 4; m++) {
            int newRow = row + DR[m];
            int newCol = col + DC[m];
            if (newRow < 0 || newRow >= N || newCol < 0 || newCol >= N) continue;

            int newBlankIndex = newRow * N + newCol;
            int[] next = Arrays.copyOf(state, SIZE);
            next[blankIndex] = next[newBlankIndex];
            next[newBlankIndex] = 0;

            String key = Arrays.toString(next);
            if (onPath.contains(key)) continue; // avoid immediate backtracking

            path.add(next);
            onPath.add(key);

            collectFrontier(next, depthRemaining - 1, path, onPath, frontier);

            path.remove(path.size() - 1);
            onPath.remove(key);
        }
    }


    static void printState(int[] state) {
        for (int r = 0; r < N; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < N; c++) {
                int v = state[r * N + c];
                sb.append(v == 0 ? "_" : String.valueOf(v)).append("\t");
            }
            System.out.println(sb.toString().trim());
        }
        System.out.println();
    }
}