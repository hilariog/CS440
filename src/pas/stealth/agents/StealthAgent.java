package src.pas.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Comparator;


// JAVA PROJECT IMPORTS
import edu.bu.pas.stealth.agents.AStarAgent;                // the base class of your class
import edu.bu.pas.stealth.agents.AStarAgent.AgentPhase;     // INFILTRATE/EXFILTRATE enums for your state machine
import edu.bu.pas.stealth.agents.AStarAgent.ExtraParams;    // base class for creating your own params objects
import edu.bu.pas.stealth.graph.Vertex;                     // Vertex = coordinate
import edu.bu.pas.stealth.graph.Path;                       // see the documentation...a Path is a linked list



public class StealthAgent
    extends AStarAgent
{

    // Fields of this class
    // TODO: add your fields here! For instance, it might be a good idea to
    // know when you've killed the enemy townhall so you know when to escape!
    // TODO: implement the state machine for following a path once we calculate it
    //       this will for sure adding your own fields.
    
    public enum Target {
        GOLD,
        TOWNHALL,
        SPAWN
    }

    private int enemyChebyshevSightLimit;
    private boolean gotGold;
    private boolean gotHall;
    private Target target;
    private Vertex spawnPoint;
    private Vertex townHall;
    private Vertex gold;
    private Integer goldID;
    private Integer hallID;
    private Integer myUnitId;
    private HistoryView history;
    private Set<Integer> enemyUnitIds = new HashSet<>();

    

    public StealthAgent(int playerNum)
    {
        super(playerNum);

        this.enemyChebyshevSightLimit = -1; // invalid value....we won't know this until initialStep()
        this.gotGold = false;
        this.gotHall = false;
        this.target = Target.GOLD;
        this.myUnitId = null;
        this.hallID = null;
        this.enemyUnitIds.clear();
        this.spawnPoint = null;
        this.townHall = null;
        this.gold = null;
        this.history = null;

    }

    // TODO: add some getter methods for your fields! Thats the java way to do things!
    public final int getEnemyChebyshevSightLimit() { return this.enemyChebyshevSightLimit; }
    public void setEnemyChebyshevSightLimit(int i) { this.enemyChebyshevSightLimit = i; }

    public boolean getGotGold(){ return this.gotGold; }
    public void setGotGold(boolean i){ this.gotGold = i; }

    public boolean getGotHall(){ return this.gotHall; }
    public void setGotHall(boolean i){ this.gotHall = i; }

    public Target getTarget(){ return this.target; }
    public void setTarget(Target i){ this.target = i; }

    public Vertex getSpawnPoint(){ return this.spawnPoint; }
    public Vertex getTownHall(){ return this.townHall; }
    public Vertex getGold(){ return this.gold; } 

    public final Integer getHallID(){ return this.hallID; }
    public final Integer getMyUnitId() { return this.myUnitId; }
    private void setMyUnitId(Integer i) { this.myUnitId = i; }
    private final Integer getGoldID() { return this.goldID; }
    private void setGoldID(Integer i) { this.goldID = i; }

    public HistoryView getHistory() {return this.history; }
    public void setHistory(HistoryView i) { this.history = i ;}

    public Set<Integer> getEnemyUnitIDs() { return this.enemyUnitIds; }

    ///////////////////////////////////////// Sepia methods to override ///////////////////////////////////

    /**
        TODO: if you add any fields to this class it might be a good idea to initialize them here
              if they need sepia information!
     */
    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        super.initialStep(state, history); // call AStarAgent's initialStep() to set helpful fields and stuff

        // now some fields are set for us b/c we called AStarAgent's initialStep()
        // let's calculate how far away enemy units can see us...this will be the same for all units (except the base)
        // which doesn't have a sight limit (nor does it care about seeing you)
        // iterate over the "other" (i.e. not the base) enemy units until we get a UnitView that is not null
        UnitView otherEnemyUnitView = null;
        Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
        while(otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null)
        {
            otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
        }

        if(otherEnemyUnitView == null)
        {
            System.err.println("[ERROR] StealthAgent.initialStep: could not find a non-null 'other' enemy UnitView??");
            System.exit(-1);
        }

        // lookup an attribute from the unit's "template" (which you can find in the map .xml files)
        // When I specify the unit's (i.e. "footman"'s) xml template, I will use the "range" attribute
        // as the enemy sight limit
        this.setEnemyChebyshevSightLimit(otherEnemyUnitView.getTemplateView().getRange());

        Integer[] playerNumbers = state.getPlayerNumbers();
		if(playerNumbers.length != 2)
		{
			System.err.println("ERROR: Should only be two players in the game");
			System.exit(1);
		}
		Integer enemyPlayerNumber = null;
		if(playerNumbers[0] != this.getPlayerNumber())
		{
			enemyPlayerNumber = playerNumbers[0];
		} else
		{
			enemyPlayerNumber = playerNumbers[1];
		}

        // Locate enemy town hall
        for (Integer unitID : state.getUnitIds(enemyPlayerNumber)) {
            this.enemyUnitIds.add(unitID);
        }
        for (Integer enemyID : this.getEnemyUnitIDs()) {
            UnitView unit = state.getUnit(enemyID);
            if (unit != null && unit.getTemplateView().getName().equals("TownHall")) {
                this.hallID = enemyID;
                this.townHall = new Vertex(unit.getXPosition(), unit.getYPosition());
                break;
            }
        }
        //deal with my own unit:
        // discover friendly units
        Set<Integer> myUnitIds = new HashSet<Integer>();
		for(Integer unitID : state.getUnitIds(this.getPlayerNumber())) // for each unit on my team
        {
            myUnitIds.add(unitID);
        }

        // check that we only have a single unit
        if(myUnitIds.size() != 1)
        {
            System.err.println("[ERROR] ScriptedAgent.initialStep: DummyAgent should control only 1 unit");
			System.exit(-1);
        }

        this.setMyUnitId(myUnitIds.iterator().next());
        this.spawnPoint = new Vertex(state.getUnit(this.getMyUnitId()).getXPosition(), state.getUnit(this.getMyUnitId()).getYPosition());
        
        this.setGoldID(state.getAllResourceIds().get(0));
        this.gold = new Vertex(state.getResourceNode(this.getGoldID()).getXPosition(), state.getResourceNode(this.getGoldID()).getYPosition());

        //return this.middleStep(state, history);
        return null;
    }

    /**
        TODO: implement me! This is the method that will be called every turn of the game.
              This method is responsible for assigning actions to all units that you control
              (which should only be a single footman in this game)
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();
        UnitView me = state.getUnit(this.getMyUnitId());
        
        Vertex targetVertex = this.getTargetVertex(this.getTarget());//get vertex of target
        Vertex currentVertex = new Vertex(me.getXPosition(), me.getYPosition());//ghet current vertex
           
        setHistory(history);

        // if(this.shouldReplacePlan(state)){ Path p = aStarSearch(currentVertex, targetVertex, state); }
        Path p = aStarSearch(currentVertex, targetVertex, state, null);
        // Get the next vertex in the path
        if(p.getDestination() != null && p.getDestination() != currentVertex){
            Vertex nextVertex = getNextVertex(p, currentVertex);//(p.getDestination() != null && p.getParentPath() != null) ? p.getParentPath().getDestination() : currentVertex;
            int dx = nextVertex.getXCoordinate() - currentVertex.getXCoordinate();
            int dy = nextVertex.getYCoordinate() - currentVertex.getYCoordinate();
            Direction moveDirection = getDirection(dx, dy);
            if(nextVertex.equals(targetVertex)){
                if (this.getTarget() == Target.GOLD) {
                    actions.put(this.getMyUnitId(), Action.createPrimitiveGather(this.getMyUnitId(), moveDirection));                    if(state.getResourceNode(this.getGoldID()) == null) this.setGotGold(true);
                    if(state.getResourceNode(this.getGoldID()) == null) this.setGotGold(true);
                } else if (this.getTarget() == Target.TOWNHALL) {
                    actions.put(this.getMyUnitId(), Action.createPrimitiveAttack(this.getMyUnitId(), getHallID()));
                    if(state.getUnit(this.getHallID()) == null) this.setGotHall(true);
                } else if (this.getTarget() == Target.SPAWN) {
                    actions.put(this.getMyUnitId(), Action.createPrimitiveMove(this.getMyUnitId(), moveDirection));
                }
            }else{ // Continue moving towards the target vertex
                actions.put(this.getMyUnitId(), Action.createPrimitiveMove(this.getMyUnitId(), moveDirection));
            }
            this.setTarget(this.getNextTarget());//figure out what we are targeting
        }
        
        /**
            I would suggest implementing a state machine here to calculate a path when neccessary.
            For instance beginning with something like:

            if(this.shouldReplacePlan(state))
            {
                // recalculate the plan
            }

            then after this, worry about how you will follow this path by submitting sepia actions
            the trouble is that we don't want to move on from a point on the path until we reach it
            so be sure to take that into account in your design

            once you have this working I would worry about trying to detect when you kill the townhall
            so that you implement escaping
         */

        return actions;
    }

    ////////////////////////////////// Helpers for middlestep ////////////////////////////////////////////
    public Target getNextTarget(){
        if(this.getGotGold() && this.getGotHall()) return Target.SPAWN;
        else if(this.getGotGold()) return Target.TOWNHALL;
        else return Target.GOLD;
    }

    public Vertex getTargetVertex(Target t){
        switch(t){
            case TOWNHALL:
                return this.getTownHall();
            case SPAWN:
                return this.getSpawnPoint();
            case GOLD:
                return this.getGold();//if theres never an instance of more than one gold this can be simplified similarly to getTH() and getSpwanPoint()
            default:
                throw new IllegalArgumentException("Unknown target type: " + t);
        }
    }

    public Vertex getNextVertex(Path p, Vertex cur){
        while (p.getParentPath() != null && p.getParentPath().getDestination() != cur) {
            p = p.getParentPath();
        }
        return p.getDestination();
    }

    // Compute Chebyshev distance
    private double getChebyshevDistance(Vertex v1, Vertex v2) {
        return Math.max(Math.abs(v1.getXCoordinate() - v2.getXCoordinate()), Math.abs(v1.getYCoordinate() - v2.getYCoordinate()));
    }

    Direction getDirection(int dx, int dy) {
        if (dx == 0 && dy == -1) return Direction.NORTH;
        if (dx == 0 && dy == 1) return Direction.SOUTH;
        if (dx == -1 && dy == 0) return Direction.WEST;
        if (dx == 1 && dy == 0) return Direction.EAST;
        if (dx == -1 && dy == -1) return Direction.NORTHWEST;
        if (dx == -1 && dy == 1) return Direction.SOUTHWEST;
        if (dx == 1 && dy == -1) return Direction.NORTHEAST;
        if (dx == 1 && dy == 1) return Direction.SOUTHEAST;
        return null; // Default case (shouldn't happen)5
    }

    ////////////////////////////////// End of helpers for middle step ////////////////////////////////////
    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////

    /////////////////////////////////// AStarAgent methods to override ///////////////////////////////////

    public Path aStarSearch(Vertex src,
                        Vertex dst,
                        StateView state,
                        ExtraParams extraParams) {
        PriorityQueue<Path> toVisit = new PriorityQueue<>(Comparator.comparingDouble(Path::getTrueCost));
        Map<Vertex, Float> costMap = new HashMap<>();
        
        costMap.put(src, 0f);
        toVisit.add(new Path(src, 0f, null));
        
        while (!toVisit.isEmpty()) {
            Path currentPath = toVisit.poll();
            Vertex currentVertex = currentPath.getDestination();
            
            // If we reach the goal, return the path
            if (currentVertex.equals(dst)) {
                return currentPath;
            }
            
            for (Vertex neighbor : getNeighbors(currentVertex, state, extraParams)) {
                Direction direction = getDirectionToMoveTo(currentVertex, neighbor);
                float cost = getEdgeWeight(currentVertex, neighbor, state, extraParams);
                float newDist = costMap.getOrDefault(currentVertex, Float.MAX_VALUE) + cost;
                
                if (newDist < costMap.getOrDefault(neighbor, Float.MAX_VALUE)) {
                    costMap.put(neighbor, newDist);
                    Path newPath = new Path(neighbor, cost, currentPath);
                    toVisit.add(newPath);
                }
            }
        }
        Path p = new Path(src);
        return p;
    }

    @Override
    public Collection<Vertex> getNeighbors(Vertex vertex, StateView state, ExtraParams params) {
        int x = vertex.getXCoordinate();
        int y = vertex.getYCoordinate();

        List<Vertex> neighbors = new LinkedList<>();

        int[][] directions = { 
            {0, -1},
            {0, 1},
            {-1, 0},
            {1, 0},
            {-1, -1},
            {-1, 1},
            {1, -1},
            {1, 1}
        };

        for(int[] d : directions){
            int candidateX = x + d[0];
            int candidateY = y + d[1];

            if((candidateX == getTargetVertex(this.getTarget()).getXCoordinate()) && (candidateY == getTargetVertex(this.getTarget()).getYCoordinate())){
                Vertex neighbor = new Vertex(candidateX, candidateY);
                neighbors.add(neighbor);
                continue;
            }

            //inbound check
            if (!state.inBounds(candidateX, candidateY)) {
                continue;
            }

            //visible check
            if (!state.canSee(candidateX, candidateY)) {
                continue;
            }

            //empty space check
            if (state.isUnitAt(candidateX, candidateY)) {
                continue;
            }

            //empty space check for resource
            if (state.isResourceAt(candidateX, candidateY)) {
                continue;
            }

            // if all checks pass, add neighbor as a possibility
            Vertex neighbor = new Vertex(candidateX, candidateY);
            neighbors.add(neighbor);
        }
        return neighbors;
    }

    public float base(Vertex src, Vertex dest, StateView state, ExtraParams extraParams) {
        int dx = Math.abs(src.getXCoordinate() - dest.getXCoordinate());
        int dy = Math.abs(src.getYCoordinate() - dest.getYCoordinate());
        
        float D = 1.0f;      // Cost for horizontal/vertical movement
        float D_diag = 1.0f; // Cost for diagonal movement (same as horizontal/vertical for Chebyshev)

        return D * Math.max(dx, dy);
    }

    public float getEdgeWeight(Vertex src,
                           Vertex dst,
                           StateView state,
                           ExtraParams extraParams) {
        float baseCost = getHeuristicValue(src, dst, state);
        //base(src, dst, state, extraParams);
        float penalty = 0f;

        // Check enemy sight and apply increasing penalty based on proximity
        for (Integer enemyID : this.getEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyID);

            // Ignore TownHall as it's not a moving enemy
            if (enemy == null || enemy.getTemplateView().getName().equals("TownHall")) continue;

            int enemyX = enemy.getXPosition();
            int enemyY = enemy.getYPosition();
            int myX = dst.getXCoordinate();
            int myY = dst.getYCoordinate();

            // Calculate Chebyshev distance
            int enemyDistance = Math.max(Math.abs(myX - enemyX), Math.abs(myY - enemyY));
            penalty += 1/enemyDistance;
            if(enemyDistance <= (this.getEnemyChebyshevSightLimit())+1){
                penalty += 10000000;
            }
        }
        return baseCost + penalty;
    }

    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        return true;
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}