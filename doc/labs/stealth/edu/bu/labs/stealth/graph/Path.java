package edu.bu.labs.stealth.graph;


// SYSTEM IMPORTS



// JAVA PROJECT IMPORTS
import edu.bu.labs.stealth.graph.Vertex;


/**
 * A class to represent a path through the graph (from a source {@link Vertex} to a destination {@link Vertex})
 * produced from a Sepia game. A Path object is a node in a reversed linked-list. The tail of the linked list is a
 * Path object that contains the source {@link Vertex} (and its parent is set to <code>null</code>).
 * The head of the linked list is a Path object that contains the destination {@link Vertex} and the parent of the head
 * is set to a Path object containing the {@link Vertex} before the destination in the path.
 *
 * There is an edge case where a path has not been expanded beyond the source {@link Vertex}. In this case the head and
 * the tail of the linked-list are the same Path object.
 *
 * Here is an example to build a path from coordinate (0,1) to adjacent coordinate (1,1):
 * <code>
 * Path p = new Path(new Vertex(0, 1));     // tail of the linked list
 * p = new Path(new Vertex(1, 1), 1.0, p);  // add edge (0, 1) - &gt; (1, 1) with cost 1.0 to the head of the path.
 * </code>
 *
 *
 * @author              Andrew Wood
 * @see                 Vertex
 */
public class Path
    extends Object
{

    private Vertex dst;                     // the "destination" vertex of this path. This path terminates here.
    private float trueCost;                 // the path cost from a src vertex to the destination "dst"
    private float estimatedPathCostToGoal;  // the estimated path cost from src to the goal passing through "dst"

    private Path parentPath;                // recursive path definition. The path from src --> dst
                                            // here is broken up into a reverse linked list. The "parentPath"
                                            // field is the path from src --> dst.parent where dst.parent is the vertex
                                            // with an edge leading to dst.

    /**
     * A constructor to create the tail of a Path object.
     *
     * @param dst           The destination (and also source) of this path
     */
    public Path(Vertex dst)
    {
        this(dst,
             0f,
             -1f,                           // invalid state
             null);
    }

    /**
     * A constructor to create a Path object that represents an edge between the destination of another path to a
     * new {@link Vertex} (including edge cost).
     *
     * @param dst           The destination {@link Vertex} of the edge being added to the path
     * @param edgeCost      The weight of the edge being added to the path
     * @param parentPath    The rest of the path the edge is being added to. The destination of this Path object is
     *                      the source {@link Vertex} of the edge being added
     */
    public Path(Vertex dst,
                float edgeCost,
                Path parentPath)
    {
        this(dst,
             edgeCost,
             -1f,                           // invalid state
             parentPath);
    }

    /**
     * A constructor to create a Path object that represents an edge between the destination of another path to a
     * new {@link Vertex} (including edge cost). This constructor also, as a convenience for A* implementations,
     * also will store the heuristic value evaluated on the new destination {@link Vertex} of this path.
     *
     * @param dst                           The destination {@link Vertex} of the edge being added to the path
     * @param edgeCost                      The weight of the edge being added to the path
     * @param newEstimatedPathCostToGoal    The heuristic value of the new destination {@link Vertex}
     * @param parentPath                    The rest of the path the edge is being added to. The destination of this
     *                                      Path object is the source {@link Vertex} of the edge being added.
     */
    public Path(Vertex dst,
                float edgeCost,
                float newEstimatedPathCostToGoal,
                Path parentPath)
    {
        this.dst = dst;

        if(parentPath != null)
        {
            this.trueCost = parentPath.getTrueCost() + edgeCost; // we know this cost
        } else
        {
            this.trueCost = edgeCost;
        }

        this.estimatedPathCostToGoal = newEstimatedPathCostToGoal;
        this.parentPath = parentPath;
    }

    /**
     * A getter method to get the destination {@link Vertex} of this Path object. 
     *
     * @return      The destination {@link Vertex} of this Path object
     */
    public final Vertex getDestination() { return this.dst; }

    /**
     * A getter method to get the cost of the path from the source {@link Vertex} to the destination {@link Vertex}
     * (i.e. the "observed" cost of this path).
     *
     * @return      The cost of the path from the source {@link Vertex} to the destination {@link Vertex}.
     */
    public float getTrueCost() { return this.trueCost; }

    /**
     * A getter method to get the estimated path cost from the destination {@link Vertex} to a goal vertex
     * (i.e. the heuristic value of the destination {@link Vertex} of this path). This is useful for the A* algorithm.
     *
     * @return      The estimated path cost from the destination {@link Vertex} to a goal vertex.
     */
    public float getEstimatedPathCostToGoal() { return this.estimatedPathCostToGoal; }

    /**
     * A getter method to get the path up to the edge (not including) that leads to the destination {@link Vertex} of
     * this Path object. If our path is v_dst &lt;- u &lt;- w &lt;- ... &lt;- src, then this method returns the path
     * u &lt;- w &lt;- ... &lt;- src.
     *
     * @return      The rest of the path not including the destination nor the edge that ends at the destination
     *              {@link Vertex} of this Path object.
     */
    public final Path getParentPath() { return this.parentPath; }

    /**
     * A setter method to set the estimated path cost to the goal from the destination {@link Vertex} of this Path
     * object (i.e. set the heuristic value of the destination {@link Vertex} of this Path object). Useful for
     * the A* algorithm.
     *
     * @param estimatedCost     The new heuristic value of the destination {@link Vertex} of this Path object.
     */
    public void setEstimatedPathCostToGoal(float estimatedCost) { this.estimatedPathCostToGoal = estimatedCost; }

    /**
     * A method to convert a path into it's String representation. The String representation of a path is a sequence
     * of the {@link Vertex} objects contained within the path. The string is prepended by the String "Path(" and
     * the String ")" is added at the end. Since the Path class implements a path as a reversed linked-list, the
     * vertices are also listed in the reverse order: the destination {@link Vertex} of the path is listed first
     * and the source {@link Vertex} of the path is listed last.
     *
     * @return          The String representation of this Path object (which serves as the destination of a path)
     */
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Path(");

        Path p = this;
        while(p != null)
        {
            builder.append(p.getDestination());
            p = p.getParentPath();

            if(p != null)
            {
                builder.append(", ");
            }
        }

        builder.append(")");
        return builder.toString();
    }

    /**
     * A method to determine if Object other is equal to this Path object. An Object is equal to this Path
     * if the object is also a Path object that shares the same destination {@link Vertex}. The entirety of the path
     * is not considered to allow graph traversal algorithms to use Path objects in data structures and quickly
     * infer whether the destination {@link Vertex} of the Path object has been discovered or not.
     *
     * @param other         The object to compare against this Path object
     * @return              <code>true</code> if this Path is equal to the object, and <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object other)
    {
        boolean isEqual = false;

        if(other instanceof Path)
        {
            isEqual = (this.getDestination() == null && ((Path)other).getDestination() == null) ||
                this.getDestination().equals(((Path)other).getDestination());
        }

        return isEqual;
    }

    /**
     * A method to get the hashcode of this Path object. The hashcode of a Path object is defined as the hashcode of
     * the destination {@link Vertex} contained within the Path object. This is done to allow graph traversal algorithms
     * to put Path objects in hashing data structures yet infer based on the destination {@link Vertex} objects.
     *
     * @return          The hashcode of this Path object.
     */
    @Override
    public int hashCode()
    {
        int hashCode = 0;

        if(this.getDestination() != null)
        {
            hashCode = this.getDestination().hashCode();
        }

        return hashCode;
    }
}

