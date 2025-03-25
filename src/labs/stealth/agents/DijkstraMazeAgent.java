package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;

import edu.cwru.sepia.environment.model.state.State.StateView;

import java.util.*;   // Needed for Dijkstra (PriorityQueue, HashSet)

// JAVA PROJECT IMPORTS

public class DijkstraMazeAgent extends MazeAgent {

    public DijkstraMazeAgent(int playerNum) {
        super(playerNum);
    }

    // Possible movement directions: Up, Down, Left, Right, and Diagonals
    private static final int[][] DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, 1}, // Top-left, Top, Top-right
        {0, -1},         {0, 1},    // Left, Right
        {1, -1}, {1, 0}, {1, 1}     // Bottom-left, Bottom, Bottom-right
    };

    private boolean isValidMove(int x, int y, StateView state, Set<String> visited, Vertex goal) {
        Vertex temp = new Vertex(x, y);
        return (state.inBounds(x, y) &&
               !state.isResourceAt(x, y) &&
               !state.isUnitAt(x, y) &&
               !visited.contains(x + "," + y)) || 
               temp.equals(goal);
    }

    private String getKey(Vertex v) { // Converts coordinates into a string key
        return v.getXCoordinate() + "," + v.getYCoordinate();
    }

    @Override
    public Path search(Vertex src, Vertex goal, StateView state) {
        System.out.println("In search");
        PriorityQueue<Path> pq = new PriorityQueue<>(Comparator.comparingDouble(Path::getTrueCost)); // Min-heap priority queue
        Map<String, Float> costMap = new HashMap<>(); // Keeps track of shortest known cost to each node
        Set<String> visited = new HashSet<>(); // Keeps track of visited nodes

        pq.offer(new Path(src, 0, null)); // Start with the source
        costMap.put(getKey(src), 0f);

        System.out.println("Starting Dijkstra's from " + getKey(src) + " to " + getKey(goal));

        while (!pq.isEmpty()) {
            Path currentPath = pq.poll(); // Get the lowest-cost path
            Vertex currentVertex = currentPath.getDestination();
            String currentKey = getKey(currentVertex);

            if (visited.contains(currentKey)) continue; // Skip if already visited

            System.out.println("Exploring: " + currentKey);

            visited.add(currentKey); // Mark as visited

            if (currentVertex.equals(goal)) {
                System.out.println("Goal reached: " + getKey(goal));
                return currentPath;
            }

            for (int[] dir : DIRECTIONS) {
                int newX = currentVertex.getXCoordinate() + dir[0];
                int newY = currentVertex.getYCoordinate() + dir[1];
                String neighborKey = newX + "," + newY;

                if (isValidMove(newX, newY, state, visited, goal)) {
                    float newCost = costMap.get(currentKey) + 1; // Fixed edge cost of 1
                    if (!costMap.containsKey(neighborKey) || newCost < costMap.get(neighborKey)) {
                        costMap.put(neighborKey, newCost);
                        Vertex neighbor = new Vertex(newX, newY);
                        Path newPath = new Path(neighbor, newCost, currentPath);

                        System.out.println("Adding to priority queue: " + neighborKey + " with cost " + newCost);

                        pq.offer(newPath);
                    }
                }
            }
        }

        System.out.println("No path found from " + getKey(src) + " to " + getKey(goal));
        return null; // No path found
    }
}
