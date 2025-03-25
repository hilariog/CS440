package edu.bu.labs.stealth.graph;


// SYSTEM IMPORTS



// JAVA PROJECT IMPORTS


/**
 * This is a class that represents a single Vertex in a graph produced from a sepia Game.
 * A Vertex in this lab is just an (x,y) coordinate pair
 *
 * @author      Andrew Wood
 */
public class Vertex
    extends Object
{
    private int xCoord, yCoord;

    /**
     * The constructor for a Vertex object. Given two ints (an x and y position), this constructor
     * initialized the Vertex type.
     *
     * @param xCoord    the x-coordinate of the Vertex.
     * @param yCoord    the y-coordinate of the Vertex.
     */
    public Vertex(int xCoord,
                  int yCoord)
    {
        this.xCoord = xCoord;
        this.yCoord = yCoord;
    }

    /**
     * Getter method to get the x-coordinate of this Vertex object.
     *
     * @return          the x-coordinate of this Vertex object.
     */
    public int getXCoordinate() { return this.xCoord; }

    /**
     * Getter method to get the y-coordinate of this Vertex object.
     *
     * @return          the y-coordinate of this Vertex object.
     */
    public int getYCoordinate() { return this.yCoord; }

    /**
     * A method to get the String representation of this Vertex object. The String representation of a Vertex is defined
     * as "(x-coordinate, y-coordinate)".
     *
     * @return          the String representation of this Vertex object.
     */
    @Override
    public String toString() { return "Vertex(x=" + this.getXCoordinate() + ", y=" + this.getYCoordinate() + ")"; }

    /**
     * A method to determine if another Object is equal to this Vertex object. An Object is equal to this Vertex object
     * iff (if and only if) the other object is also a Vertex, and the two Vertex objects have the same coordinates.
     *
     * @param other     The other object to compare against this Vertex.
     * @return          <code>true</code> if this Vertex is equal to the argument. <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object other)
    {
        boolean isEqual = false;

        if(other instanceof Vertex)
        {
            isEqual = this.getXCoordinate() == ((Vertex)other).getXCoordinate() &&
                this.getYCoordinate() == ((Vertex)other).getYCoordinate();
        }

        return isEqual;
    }

    /**
     * A method to calculate the hashcode of this Vertex object. The hashcode of a Vertex is defined using the
     * following equation:
     * (x-coordinate + y-coordinate) * (x-coordinate + y-coordinate + 1)
     *
     * @return          the hashcode of this Vertex object using the equation above.
     */
    @Override
    public int hashCode()
    {
        return (this.getXCoordinate() + this.getYCoordinate()) + (this.getXCoordinate() + this.getYCoordinate() + 1);
    }
}
