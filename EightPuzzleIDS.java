import java.util.*;

public class EightPuzzleIDS {

    // Goal state
    static final int[] GOAL = {1, 2, 3, 4, 5, 6, 7, 8, 0};

    // Moves: Up, Down, Left, Right
    static final int[] ROW_MOVE = {-1, 1, 0, 0};
    static final int[] COL_MOVE = {0, 0, -1, 1};
    static final char[] MOVE_NAME = {'U', 'D', 'L', 'R'};

    static int nodesGenerated = 0;

    // Node of the search tree
    static class Node {
        int[] state;
        int zeroIndex;
        String path;

        Node(int[] state, int zeroIndex, String path) {
            this.state = state;
            this.zeroIndex = zeroIndex;
            this.path = path;
        }
    }

    // Generate a random puzzle
    static int[] generateRandomState() {
        List<Integer> numbers = new ArrayList<>();

        for (int i = 0; i <= 8; i++) {
            numbers.add(i);
        }

        Collections.shuffle(numbers);

        int[] state = new int[9];

        for (int i = 0; i < 9; i++) {
            state[i] = numbers.get(i);
        }

        return state;
    }

    // Count inversions
    static int countInversions(int[] state) {
        int inversions = 0;

        for (int i = 0; i < 9; i++) {
            if (state[i] == 0)
                continue;

            for (int j = i + 1; j < 9; j++) {
                if (state[j] == 0)
                    continue;

                if (state[i] > state[j]) {
                    inversions++;
                }
            }
        }

        return inversions;
    }

    // Check whether puzzle is solvable
    static boolean isSolvable(int[] state) {
        int inversions = countInversions(state);

        // For 3 x 3 puzzle:
        // Even number of inversions => solvable
        return inversions % 2 == 0;
    }

    // Check whether state is goal
    static boolean isGoal(int[] state) {
        return Arrays.equals(state, GOAL);
    }

    // Create a unique string for a state
    static String stateToString(int[] state) {
        return Arrays.toString(state);
    }

    /*
     * Depth Limited Search
     *
     * Returns solution path if found.
     * Returns null otherwise.
     */
    static String depthLimitedSearch(
            int[] state,
            int zeroIndex,
            int depth,
            String path,
            Set<String> visited) {

        nodesGenerated++;

        if (isGoal(state)) {
            return path;
        }

        if (depth == 0) {
            return null;
        }

        int row = zeroIndex / 3;
        int col = zeroIndex % 3;

        String currentState = stateToString(state);
        visited.add(currentState);

        for (int i = 0; i < 4; i++) {

            int newRow = row + ROW_MOVE[i];
            int newCol = col + COL_MOVE[i];

            // Check boundary
            if (newRow < 0 || newRow >= 3 ||
                    newCol < 0 || newCol >= 3) {
                continue;
            }

            int newZeroIndex = newRow * 3 + newCol;

            // Create new state
            int[] newState = state.clone();

            // Move tile into blank space
            newState[zeroIndex] = newState[newZeroIndex];
            newState[newZeroIndex] = 0;

            String newStateString = stateToString(newState);

            // Avoid cycles in current DFS path
            if (visited.contains(newStateString)) {
                continue;
            }

            String result = depthLimitedSearch(
                    newState,
                    newZeroIndex,
                    depth - 1,
                    path + MOVE_NAME[i],
                    visited
            );

            if (result != null) {
                return result;
            }
        }

        visited.remove(currentState);

        return null;
    }

    // Iterative Deepening Search
    static String iterativeDeepeningSearch(int[] initialState) {

        int zeroIndex = 0;

        for (int i = 0; i < 9; i++) {
            if (initialState[i] == 0) {
                zeroIndex = i;
                break;
            }
        }

        // Increase depth gradually
        for (int depth = 0; ; depth++) {

            System.out.println("Searching at depth: " + depth);

            Set<String> visited = new HashSet<>();

            String result = depthLimitedSearch(
                    initialState,
                    zeroIndex,
                    depth,
                    "",
                    visited
            );

            if (result != null) {
                return result;
            }
        }
    }

    // Print puzzle
    static void printState(int[] state) {

        for (int i = 0; i < 9; i++) {

            if (state[i] == 0)
                System.out.print("  ");
            else
                System.out.print(state[i] + " ");

            if (i % 3 == 2)
                System.out.println();
        }

        System.out.println();
    }

    // Print solution step by step
    static void printSolution(int[] initialState, String solution) {

        int[] current = initialState.clone();

        int zeroIndex = 0;

        for (int i = 0; i < 9; i++) {
            if (current[i] == 0) {
                zeroIndex = i;
                break;
            }
        }

        System.out.println("\nInitial State:");
        printState(current);

        System.out.println("Moves: " + solution);
        System.out.println("Number of moves: " + solution.length());

        for (int step = 0; step < solution.length(); step++) {

            char move = solution.charAt(step);

            int row = zeroIndex / 3;
            int col = zeroIndex % 3;

            int direction = 0;

            if (move == 'U')
                direction = 0;
            else if (move == 'D')
                direction = 1;
            else if (move == 'L')
                direction = 2;
            else if (move == 'R')
                direction = 3;

            int newRow = row + ROW_MOVE[direction];
            int newCol = col + COL_MOVE[direction];

            int newZeroIndex = newRow * 3 + newCol;

            // Swap blank and tile
            current[zeroIndex] = current[newZeroIndex];
            current[newZeroIndex] = 0;

            zeroIndex = newZeroIndex;

            System.out.println("Move " + (step + 1) + ": " + move);
            printState(current);
        }
    }

    public static void main(String[] args) {

        int[] initialState;

        int attempts = 0;

        // Generate until solvable state is obtained
        while (true) {

            attempts++;

            initialState = generateRandomState();

            System.out.println("\nGenerated State:");
            printState(initialState);

            int inversions = countInversions(initialState);

            System.out.println("Inversions: " + inversions);

            if (isSolvable(initialState)) {
                System.out.println("Solvable!");
                break;
            } else {
                System.out.println("Not solvable. Generating another state...");
            }
        }

        System.out.println("\nNumber of attempts: " + attempts);

        // Solve using IDS
        nodesGenerated = 0;

        long startTime = System.currentTimeMillis();

        String solution = iterativeDeepeningSearch(initialState);

        long endTime = System.currentTimeMillis();

        // Print solution
        printSolution(initialState, solution);

        System.out.println("Nodes generated: " + nodesGenerated);
        System.out.println("Time taken: " +
                (endTime - startTime) + " ms");
    }
}