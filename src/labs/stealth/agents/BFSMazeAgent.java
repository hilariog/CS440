package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;


import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;           // will need for bfs


// JAVA PROJECT IMPORTS


public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(int playerNum)
    {
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

    private String getKey(Vertex v) {//turns coords into string for hash key
        return v.getXCoordinate() + "," + v.getYCoordinate();
    }

    @Override
    public Path search(Vertex src,
                   Vertex goal,
                   StateView state)
    {
        Queue<Path> queue = new LinkedList<>(); // BFS queue
        Set<String> visited = new HashSet<>();  // Visited set

        queue.offer(new Path(src, 0, null));    // Enqueue starting path
        visited.add(getKey(src));               // Mark visited
        System.out.println("Queued and visited " + getKey(src));
        System.out.println("Starting BFS from " + getKey(src) + " to " + getKey(goal));

        while(!queue.isEmpty()){
            Path currentPath = queue.poll();    // Dequeue
            System.out.println("Dequeued: " + getKey(currentPath.getDestination()));
            Vertex currentVertex = currentPath.getDestination();    // Retrieve last element from path

            System.out.println("Exploring: " + getKey(currentVertex));

            if(currentVertex.equals(goal)){
                System.out.println("Goal reached: " + getKey(goal));
                return currentPath;
            }

            for (int[] dir : DIRECTIONS) {
                int newX = currentVertex.getXCoordinate() + dir[0];
                int newY = currentVertex.getYCoordinate() + dir[1];

                if (isValidMove(newX, newY, state, visited, goal)) {
                    Vertex neighbor = new Vertex(newX, newY);
                    Path newPath = new Path(neighbor, 1f, currentPath);
                    
                    System.out.println("Adding neighbor to queue: " + getKey(neighbor));

                    queue.offer(newPath);
                    visited.add(getKey(neighbor));
                }
            }
        }
        
        System.out.println("No path found from " + getKey(src) + " to " + getKey(goal));
        return null; // No path found
    }

}
