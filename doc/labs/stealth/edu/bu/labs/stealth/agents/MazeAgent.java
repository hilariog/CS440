package edu.bu.labs.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


// JAVA PROJECT IMPORTS
import edu.bu.labs.stealth.graph.Path;
import edu.bu.labs.stealth.graph.Vertex;


/**
 * The base type for {@link Agent}'s that solve mazes in Sepia. MazeAgents implement a specific state machine that
 * allows them to move from one coordinate on the map to another using a precomputed plan (i.e. a path).
 * 
 * {@link Agent}s in Sepia must override a few abstract methods, most notably <code>initialStep</code>,
 * <code>middleStep</code>, and <code>terminalStep</code>. All of these methods are handled for you, to both control
 * the API (and prevent you from issuing Sepia compound actions) as well as provide an API for the autograders to use.
 *
 * In this assignment, you will need to implement two methods:
 * <code>public Path search(Vertex src, Vertex goal, StateView state)</code>.
 * This method is called by MazeAgent to calculate the path (called a plan) from the source {@link Vertex} to the
 * goal {@link Vertex}. Note that the goal {@link Vertex} is occupied (by the enemy base), and therefore your plan
 * should not ultimately contain this vertex in it.
 *
 * <code> public boolean shouldReplacePlan(StateView state)</code>
 * This method is called by MazeAgent to decide if the current plan is no longer valid (for instance if the current
 * plan is blocked by an enemy unit or something). If this method returns <code>true</code>, then MazeAgent will call
 * the <code>search</code> method again to calculate a new plan, which it will then follow.
 *
 * Here is a description of how these three methods are implemented:
 * <code>initialStep</code>:
 *    This method discovers the unit you control, the enemy base's unit, and all other enemy units present on the map.
 *    Once this is complete, this method calls the <code>search</code> method to find a plan from your unit's
 *    current location to the location of the enemy's base. The path produced by this method is converted into a Stack
 *    of coordinates for internal use.
 *
 * <code>middleStep</code>:
 *    This method first calls the <code>shouldReplacePlan</code> method to determine if the current plan is still valid.
 *    If <code>shouldReplacePlan</code> returns <code>true</code>, then this method will call <code>search</code>,
 *    convert the result to a Stack of coordinates, and overwrite the current plan with this new plan.
 *
 *    This method will then examine the current position of the unit you control. If the current plan is not empty,
 *    this method will try to move the unit to the next position in the current plan, and will fail if the coordinate
 *    to move to is not adjacent to the current position of the unit you control. When the plan is exhausted (i.e. there
 *    are no more coordinates in the Stack), this unit will try to attack the enemy base. If the unit you control is
 *    not adjacent to the enemy base, this method will crash the program.
 *
 * <code>terminalStep</code>:
 *    This method prints out the results of the game. There are four possible outcomes:
 *        1) both your unit and the enemy base are killed. This is a tie.
 *        2) the enemy base is killed and you survive. You win.
 *        3) the enemy base survives and your unit is killed. You lose.
 *        4) both the enemy base as well as you survive. This is a tie.
 *
 * @author          Andrew Wood
 * @see             Vertex
 * @see             Path
 * @see             MazeAgent#search
 */
public abstract class MazeAgent
    extends Agent
{

    private int myUnitID;
    private int enemyTargetUnitID;
    private Set<Integer> otherEnemyUnitIDs;
    private Stack<Vertex> currentPlan;
    private Vertex nextVertexToMoveTo;


    public MazeAgent(int playerNum)
    {
        super(playerNum);
        this.myUnitID = -1;                                         // invalid state
        this.enemyTargetUnitID = -1;                                // invalid state
        this.otherEnemyUnitIDs = null;
        this.currentPlan = null;
        this.nextVertexToMoveTo = null;
    }

    /**
     * A getter method to get the unit id of the unit under your control.
     *
     * @return      The id of the unit under your control.
     */
    public int getMyUnitID() { return this.myUnitID; }

    /**
     * A getter method to get the unit id of the enemy base.
     *
     * @return      The id of the enemy base.
     */
    public int getEnemyTargetUnitID() { return this.enemyTargetUnitID; }

    /**
     * A getter method to get the ids of all other enemy units on the map (not including the enemy base).
     *
     * @return      The ids of all other enemy units on the map (not including the enemy base).
     */
    public final Set<Integer> getOtherEnemyUnitIDs() { return this.otherEnemyUnitIDs; }

    /**
     * A getter method to get the current plan to get from your unit's current location to a square
     * adjacent to the enemy base
     *
     * @return      The plan (i.e. sequence of coordinates) to get from your unit's current location to a square
     *              adjacent to the enemy base
     */
    public final Stack<Vertex> getCurrentPlan() { return this.currentPlan; }

    /**
     * A getter method to get the next coordinate to move the unit you control to. This is used internally
     * by <code>middleStep</code> just in case movement takes more than a single turn. This is updated
     * every time the unit arrives at this coordinate and is set to the next coordinate in the plan (or null if
     * the plan is empty)
     *
     * @return      The coordinate to move the unit you control to.
     */
    public final Vertex getNextVertexToMoveTo() { return this.nextVertexToMoveTo; }

    /**
     * A setter method to set the unit id of the unit under your control.
     *
     * @param unitID      The id of the unit under your control.
     */
    private void setMyUnitID(int unitID) { this.myUnitID = unitID; }

    /**
     * A setter method to set the unit id of the enemy base.
     *
     * @param unitID      The id of the enemy base
     */
    private void setEnemyTargetUnitID(int unitID) { this.enemyTargetUnitID = unitID; }

    /**
     * A setter method to set the unit ids of all other enemy units on the map (not including the enemy base)
     *
     * @param unitIDs     The ids of all other enemy units on the map (not including the enemy base)
     */
    private void setOtherEnemyUnitIDs(Set<Integer> unitIDs) { this.otherEnemyUnitIDs = unitIDs; }

    /**
     * A setter method to set the current plan (i.e. Stack of coordinates). <code>middleStep</code> will follow this
     * plan by moving the unit you control to each coordinate in the plan one at a time.
     *
     * @param newPlan      The new plan to follow in <code>middleStep</code>
     */
    protected void setCurrentPlan(Stack<Vertex> newPlan) { this.currentPlan = newPlan; }

    /**
     * A setter method to set the coordinate to move the unit you control to. This is called by <code>middleStep</code>
     * whenever the unit you control arrives at the coordinate contained in the field this method sets. Once that
     * happens, <code>middleStep</code> pops the next coordinate from the plan and uses this method to remember that
     * coordinate in case movement takes more than one turn.
     *
     * @param v      The coordinate to move the unit you control to in <code>middleStep</code>
     */
    private void setNextVertexToMoveTo(Vertex v) { this.nextVertexToMoveTo = v; }

    /**
     * A method to discover all units on the map. There should be only one unit you control, one enemy base, and an
     * arbitrary number of additional units the enemy controls. This method, after discovering the ids of these
     * units and setting fields, will call <code>search</code> to find the plan from your unit's current location
     * to a coordinate adjacent to the enemy base.
     *
     * @param state     The initial state of the game.
     * @param history   The initial history of the game.
     * @return          This method returns <code>null</code> and relies on <code>middlestep</code> to do any movement.
     */
    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        // first find out which units are mine and which units aren't
        Set<Integer> myUnitIDs = new HashSet<Integer>();
        for(Integer unitID : state.getUnitIds(this.getPlayerNumber()))
        {
            myUnitIDs.add(unitID);
        }

        // should only be one unit controlled by me
        if(myUnitIDs.size() != 1)
        {
            System.err.println("ERROR: should only be 1 unit controlled by player=" +
                this.getPlayerNumber() + " but found " + myUnitIDs.size() + " units");
            System.exit(-1);
        } else
        {
            this.setMyUnitID(myUnitIDs.iterator().next()); // get the one unit id
        }


        // there can be as many other players as we want, and they can controll as many units as they want,
        // but there should be only ONE enemy townhall unit
        Set<Integer> enemyTownhallUnitIDs = new HashSet<Integer>();
        Set<Integer> otherEnemyUnitIDs = new HashSet<Integer>();
        for(Integer playerNum : state.getPlayerNumbers())
        {
            if(playerNum != this.getPlayerNumber())
            {
                for(Integer unitID : state.getUnitIds(playerNum))
                {
                    if(state.getUnit(unitID).getTemplateView().getName().toLowerCase().equals("townhall"))
                    {
                        enemyTownhallUnitIDs.add(unitID);
                    } else
                    {
                        otherEnemyUnitIDs.add(unitID);
                    }
                }
            }
        }

        // should only be one unit controlled by me
        if(enemyTownhallUnitIDs.size() != 1)
        {
            System.err.println("ERROR: should only be 1 enemy townhall unit present on the map but found "
                + enemyTownhallUnitIDs.size() + " such units");
            System.exit(-1);
        } else
        {
            this.setEnemyTargetUnitID(enemyTownhallUnitIDs.iterator().next()); // get the one unit id
            this.setOtherEnemyUnitIDs(otherEnemyUnitIDs);
        }

        this.setCurrentPlan(this.makePlan(state));

        return null;
    }

    /**
     * A method to decide what action the unit you control should do every turn. This method decides what to do using
     * the following algorithm:
     *    1) if the current plan is invalid (i.e. <code>this.shouldReplacePlan(state)</code> returns <code>true</code>), then
     *       the current plan is replaced by a new plan calculated by <code>search</code> (technically it goes through a
     *       separate method called <code>makePlan</code>. This additional method calls <code>search</code> and then
     *       converts the {@link Path} object to a {@link Stack} of coordinates.
     *
     *    2) If the plan is not empty and the unit you control is not currently at the {@link Vertex} to move to,
     *       then move to that coordinate. This can fail if the two coordinates are not adjacent.
     *
     *    3) If the plan is empty, attack the enemy base. If the unit you control is not adjacent to the enemy base,
     *       this method will print an error and crash the program.
     *
     * @param state         The current state of the game
     * @param history       The history of the game up to this turn
     * @return              A mapping from every unit id under our control to an {@link Action} that we want that unit
     *                      to do.
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // state machine: if we should replace the current plan, do so
        // if(this.shouldReplacePlan(state))
        // {
        //     this.setCurrentPlan(this.makePlan(state));
        // }

        // get my position
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        Vertex myUnitVertex = new Vertex(myUnitView.getXPosition(), myUnitView.getYPosition());
        UnitView enemyTargetUnitView = state.getUnit(this.getEnemyTargetUnitID());

        if(!this.getCurrentPlan().isEmpty() && (this.hasArrivedAtNextVertex(state)))
        {
            // pop off the next element of the plan
            this.setNextVertexToMoveTo(this.getCurrentPlan().pop());
            System.out.println("Moving to " + this.getNextVertexToMoveTo());
        }


        if(this.getNextVertexToMoveTo() != null && !this.getNextVertexToMoveTo().equals(myUnitVertex))
        {
            // try to go there
            actions.put(this.getMyUnitID(),
                        Action.createPrimitiveMove(this.getMyUnitID(),
                                                   this.getDirectionToMoveTo(myUnitVertex,
                                                                             this.getNextVertexToMoveTo())));
        } else
        {
            // enemy target is still alive
            if(enemyTargetUnitView != null)
            {
                if(Math.abs(myUnitView.getXPosition() - enemyTargetUnitView.getXPosition()) > 1 ||
                   Math.abs(myUnitView.getYPosition() - enemyTargetUnitView.getYPosition()) > 1)
                {
                    System.err.println("ERROR: plan is empty, so we should be next to the enemy target"
                        + " it seems we're not. Cannot attack enemy.");
                } else
                {
                    System.out.println("attacking enemy");
                    actions.put(this.getMyUnitID(),
                        Action.createPrimitiveAttack(this.getMyUnitID(),
                                                     this.getEnemyTargetUnitID()));
                }
            }
        }

        return actions;
    }

    /**
     * A method to print out the outcome of the game. There are four possible outcomes:
     *      1) both your unit and the enemy base are killed. This is a tie.
     *      2) the enemy base is killed and you survive. You win.
     *      3) the enemy base survives and your unit is killed. You lose.
     *      4) both the enemy base as well as you survive. This is a tie.
     *
     * @param state         The last state of the game.
     * @param history       The entire history of the game.
     */
    @Override
    public void terminalStep(StateView state,
                             HistoryView history)
    {
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        UnitView enemyTargetUnitView = state.getUnit(this.getEnemyTargetUnitID());

        if(myUnitView == null && enemyTargetUnitView == null)
        {
            System.err.println("The enemy was destroyed, but so were you!");
        } else if(myUnitView != null && enemyTargetUnitView == null)
        {
            System.out.println("The enemy was destroyed, you win!");
        } else if(myUnitView == null && enemyTargetUnitView != null)
        {
            System.err.println("You were destroyed, you lose!");
        } else
        {
            System.out.println("Both you and the enemy lived another day");
        }
    }

    /**
     * A method to save this MazeAgent to a file on disk. This method does nothing.
     *
     * @param os        The {@link OutputStream} to write to
     */
    @Override
    public void savePlayerData(OutputStream os) {}

    /**
     * A method to load this MazeAgent from disk. This method does nothing.
     *
     * @param is        The {@link InputStream} to write to
     */
    @Override
    public void loadPlayerData(InputStream is) {}

    /**
     * A method to make a new plan from the current state of the game. This method gets the coordinate of the unit
     * you control as well as the coordinate of the enemy base, and then calls the <code>search</code> method, which
     * produces a {@link Path} that will lead the unit you control to a coordinate adjacent to the enemy base.
     *
     * This method then creates a {@link Stack} of coordinates from this {@link Path}, which is used internally by
     * <code>middleStep</code> to move the unit you control.
     *
     * @param state     The current state of the game.
     * @return          The {@link Stack} of coordinates to follow, called a plan.
     */
    protected Stack<Vertex> makePlan(StateView state)
    {
        // get current position of friendly unit
        UnitView myUnitView = state.getUnit(this.getMyUnitID());

        // get current position of enemy target unit
        UnitView enemyTargetUnitView = state.getUnit(this.getEnemyTargetUnitID());

        Path path = this.search(new Vertex(myUnitView.getXPosition(),
                                           myUnitView.getYPosition()),
                                new Vertex(enemyTargetUnitView.getXPosition(),
                                           enemyTargetUnitView.getYPosition()),
                                state);

        // chop off the last vertex
        path = path.getParentPath();

        // convert a Path into a Stack<Vertex>
        Stack<Vertex> plan = new Stack<Vertex>();
        while(path.getParentPath() != null)
        {
            plan.push(path.getDestination());
            path = path.getParentPath();
        }
        return plan;
    }

    /**
     * A helper method to calculate the sepia {@link Direction} needed to go from the source coordinate to the
     * destination coordiante. This method will return <code>null</code> (and print out an error) if the two
     * coordinates are not adjacent.
     *
     * @param src   The source coordinate
     * @param dst   The destination coordinate
     * @return      The sepia {@link Direction} that will move a unit located at the source coordinate to the
     *              destination coordinate. Will be <code>null</code> if the two coordinates are not adjacent.
     */
    protected Direction getDirectionToMoveTo(Vertex src, Vertex dst)
    {
        int xDiff = dst.getXCoordinate() - src.getXCoordinate();
        int yDiff = dst.getYCoordinate() - src.getYCoordinate();

        Direction dirToGo = null;

        if(xDiff == 1 && yDiff == 1)
        {
            dirToGo = Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            dirToGo = Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            dirToGo = Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            dirToGo = Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            dirToGo = Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            dirToGo = Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            dirToGo = Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            dirToGo = Direction.NORTHWEST;
        } else
        {
            System.err.println("ERROR: cannot go from src=" + src + " to dst=" + dst + " in one move.");
        }

        return dirToGo;
    }

    /**
     * A helper method to determine if the unit you control has arrived at the coordinate returned by
     * <code>getNextVertexToMoveTo</code>. This method will return <code>true</code> if
     * <code>getNextVertexToMoveTo</code> returns <code>null</code>.
     *
     * @param state     The current state of the game
     * @return          <code>true</code> if the coordinate of the unit you control equals the coordinate produced by
     *                  <code>getNextVertexToMoveTo</code> or if <code>getNextVertexToMoveTo</code> returns
     *                  <code>null</code>. <code>false</code> otherwise.
     */
    protected boolean hasArrivedAtNextVertex(StateView state)
    {
        UnitView myUnitView = state.getUnit(this.getMyUnitID());
        return this.getNextVertexToMoveTo() == null ||
            this.getNextVertexToMoveTo().equals(new Vertex(myUnitView.getXPosition(), myUnitView.getYPosition()));
    }

    /**
     * A abstract method for a graph traversal algorithm that computes the path from the source coordinate to
     * the goal coordinate using the {@link Path} datatype.
     *
     * @param src       The source coordinate
     * @param goal      The goal coordinate
     * @return          A {@link Path} object that implements a path from the source coordinate to the goal coordinate
     */
    public abstract Path search(Vertex src,
                                Vertex goal,
                                StateView state);

}

