import java.util.*;

public class NPuzzleIDS {

    static int N;      // board dimension (N x N)
    static int SIZE;   // N * N
    static int[] goal;

    // Move deltas: up, down, left, right (as row/col offsets)
    static final int[] DR = {-1, 1, 0, 0};
    static final int[] DC = {0, 0, -1, 1};
    static final String[] MOVE_NAMES = {"UP", "DOWN", "LEFT", "RIGHT"};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        N = readDimension(scanner);
        int maxDepth = readMaxDepth(scanner);
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
        List<int[]> solution = solveIDS(initial, maxDepth);
        long elapsed = System.currentTimeMillis() - startTime;

        if (solution == null) {
            System.out.println("No solution found within max depth (" + maxDepth + "). Time: " + elapsed + " ms");
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
                if (n >= 2) {
                    break;
                }
                System.out.println("N must be 2 or greater. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
        return n;
    }

    static int readMaxDepth(Scanner scanner) {
        System.out.print("Enter max search depth (press Enter for default 40): ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) {
            return 40;
        }
        try {
            int depth = Integer.parseInt(line);
            return depth > 0 ? depth : 40;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input, using default max depth of 40.");
            return 40;
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
            int rowFromTop = blankIndex / N;          // 0-indexed
            int rowFromBottom = N - rowFromTop;        // 1-indexed from bottom
            return (inversions + rowFromBottom) % 2 == 1;
        }
    }

    static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return -1;
    }


//IDS
    static List<int[]> solveIDS(int[] start, int maxDepth) {
        for (int limit = 0; limit <= maxDepth; limit++) {
            Set<String> onPath = new HashSet<>();
            List<int[]> path = new ArrayList<>();
            path.add(start);
            onPath.add(Arrays.toString(start));

            System.out.println("Trying depth limit: " + limit);

            if (depthLimitedDFS(start, limit, path, onPath)) {
                return path;
            }
        }
        return null;
    }

    static boolean depthLimitedDFS(int[] state, int limit, List<int[]> path, Set<String> onPath) {
        if (Arrays.equals(state, goal)) {
            return true;
        }
        if (limit == 0) {
            return false;
        }

        int blankIndex = indexOf(state, 0);
        int row = blankIndex / N;
        int col = blankIndex % N;

        for (int m = 0; m < 4; m++) {
            int newRow = row + DR[m];
            int newCol = col + DC[m];

            if (newRow < 0 || newRow >= N || newCol < 0 || newCol >= N) {
                continue; // move goes off the board
            }

            int newBlankIndex = newRow * N + newCol;
            int[] next = Arrays.copyOf(state, SIZE);
            // swap blank with target tile
            next[blankIndex] = next[newBlankIndex];
            next[newBlankIndex] = 0;

            String key = Arrays.toString(next);
            if (onPath.contains(key)) {
                continue; 
            }

            path.add(next);
            onPath.add(key);

            if (depthLimitedDFS(next, limit - 1, path, onPath)) {
                return true;
            }

            // backtrack
            path.remove(path.size() - 1);
            onPath.remove(key);
        }

        return false;
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