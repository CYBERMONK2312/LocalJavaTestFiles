import java.util.*;

public class GraphBFS {
    
    static void addEdge(ArrayList<ArrayList<Integer>> adj, int u, int v) {
        // for undirected graph connect u -> v then v -> u
        adj.get(u).add(v);
        adj.get(v).add(u);
    }

    static void bfs(ArrayList<ArrayList<Integer>> adj, int v) {
        boolean[] visited = new boolean[v + 1];
        for (int i = 1; i < v + 1; i++) {
            visited[i] = false;
        }
        int source = 1;
        visited[source] = true;
        Queue<Integer> queue = new LinkedList<>();
        queue.add(source);
        while (queue.size() != 0) {
            //pop front element of the queue
            source = queue.poll();
            System.out.println(source + ",");

            //get the number of adjacent vertices of current source
            int size = adj.get(source).size();
            for (int i = 0; i < size; i++) {
                //get adjacent nodes
                int adjNode = adj.get(source).get(i);

                //check if this adjacent node is visited or not
                if (visited[adjNode] == false) {
                    visited[adjNode] = true;
                    queue.offer(adjNode);
                }
            }
        }
    }
    
    static void printGraph(ArrayList<ArrayList<Integer>> adj) {
        for (int i = 0; i < adj.size(); i++) {
            System.out.println(i + ":");
            for (int j = 0; j < adj.get(i).size(); j++) {
                System.out.println(adj.get(i).get(j) + ",");
            }
            System.out.println();
        }
    }
    
    public static void main(String[] args) {
        int v = 6;
        ArrayList<ArrayList<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < v+1; i++) {
            adj.add(new ArrayList<>());
        }
        addEdge(adj, 1, 2);
        addEdge(adj, 1, 3);
        addEdge(adj, 2, 4);
        addEdge(adj, 2, 5);
        addEdge(adj, 3, 5);
        addEdge(adj, 4, 6);
        addEdge(adj, 4, 5);
        addEdge(adj, 5, 6);

        printGraph(adj);

        bfs(adj, v);
    }
}
