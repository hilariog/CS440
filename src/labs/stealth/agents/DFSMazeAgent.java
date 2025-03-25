package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;

import edu.cwru.sepia.environment.model.state.State.StateView;

import java.util.HashSet;   // Needed for DFS
import java.util.Stack;     // Needed for DFS
import java.util.Set;       // Needed for DFS

// JAVA PROJECT IMPORTS

public class DFSMazeAgent extends MazeAgent {

    public DFSMazeAgent(int playerNum) {
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
        Stack<Path> stack = new Stack<>();   // DFS stack
        Set<String> visited = new HashSet<>(); // Visited set

        stack.push(new Path(src, 0, null)); // Push initial path onto stack
        visited.add(getKey(src)); // Mark as visited
        System.out.println("Starting DFS from " + getKey(src) + " to " + getKey(goal));

        while (!stack.isEmpty()) {
            Path currentPath = stack.pop(); // Pop the top path
            Vertex currentVertex = currentPath.getDestination();

            System.out.println("Exploring: " + getKey(currentVertex));

            if (currentVertex.equals(goal)) {
                System.out.println("Goal reached: " + getKey(goal));
                return currentPath;
            }

            for (int[] dir : DIRECTIONS) {
                int newX = currentVertex.getXCoordinate() + dir[0];
                int newY = currentVertex.getYCoordinate() + dir[1];

                if (isValidMove(newX, newY, state, visited, goal)) {
                    Vertex neighbor = new Vertex(newX, newY);
                    Path newPath = new Path(neighbor, 1f, currentPath); // Fixed edge cost of 1

                    System.out.println("Adding to stack: " + getKey(neighbor));

                    stack.push(newPath);
                    visited.add(getKey(neighbor));
                }
            }
        }

        System.out.println("No path found from " + getKey(src) + " to " + getKey(goal));
        return null; // No path found
    }
}
