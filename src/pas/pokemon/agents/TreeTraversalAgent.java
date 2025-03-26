package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.enums.Stat;



import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


// JAVA PROJECT IMPORTS


public class TreeTraversalAgent
    extends Agent
{

    private double evaluate(BattleView state) {
        // TODO: Implement a proper heuristic evaluation.
        TeamView t1 = state.getTeam1View();
        TeamView t2 = state.getTeam2View();
        
        if(state.isOver()){
            //in terminal state, return utility value (%%assume getactivepokemonview will return the last active pokemon if a team lost%%)
            if(t2.getActivePokemonView().hasFainted()) return 1;
            else return -1;
            //in a terminal state we return either -1 or 1 for win/loss
        }else{
            int i = 0;
            int t1Hp = 0;
            int t2Hp = 0;
            while(t2.getPokemonView(i) != null){
                t2Hp += t2.getPokemonView(i).getBaseStat(Stat.HP);
                i++;
            }
            i = 0;
            while(t1.getPokemonView(i) != null){
                t1Hp += t1.getPokemonView(i).getBaseStat(Stat.HP);
                i++;
            }
            return (((t1Hp / (t1Hp + t2Hp)) * 2) - 1);
        }
    }

    //!!!! Function to return the expected utility of a node? to be called by stochasticTreeSearcher to decide move

    /*
    private double expectiminimax(Node node, int depth) {
        // Terminal condition: if state is over or maximum depth reached, evaluate.
        if (node.state.isOver() || depth >= maxDepth) {
            return evaluate(node.state);
        }
        
        // Expand the node if children not generated yet.
        if (node.children.isEmpty()) {
            expandNode(node);
        }
        
        // Depending on the node type, choose the appropriate aggregation.
        if (node.type == NodeType.MAX) {
            double maxVal = Double.NEGATIVE_INFINITY;
            for (Node child : node.children) {
                double childVal = expectiminimax(child, depth + 1);
                maxVal = Math.max(maxVal, childVal);
            }
            return maxVal;
        } else if (node.type == NodeType.MIN) {
            double minVal = Double.POSITIVE_INFINITY;
            for (Node child : node.children) {
                double childVal = expectiminimax(child, depth + 1);
                minVal = Math.min(minVal, childVal);
            }
            return minVal;
        } else if (node.type == NodeType.CHANCE) {
            double expVal = 0.0;
            for (Node child : node.children) {
                double childVal = expectiminimax(child, depth + 1);
                expVal += child.probability * childVal;
            }
            return expVal;
        }
        // Should not reach here.
        return 0.0;
    }
    */

    //Expand Node function that uses API calls to get a nodes child(used in build tree func):
    /*
    private void expandNode(Node node) {
        // Example expansion for a MAX node (our turn)
        if (node.type == NodeType.MAX) {
            // TODO: Replace with actual code to get available moves for our Pokemon.
            List<MoveView> availableMoves = node.state.getTeamView(getMyTeamIdx()).getPokemonView(0).getAvailableMoves();
            // For each move, create a child node.
            for (MoveView move : availableMoves) {
                // Simulate applying the move: get potential outcomes.
                // The getPotentialEffects method returns a List of pairs (probability, new BattleView)
                List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(node.state, getMyTeamIdx(), 1); // assuming opponent index 1
                // For each outcome, create a chance node.
                Node moveNode = new Node(null, move, NodeType.MAX); // intermediate node representing taking move 'move'
                for (Pair<Double, BattleView> outcome : outcomes) {
                    double prob = outcome.getFirst();
                    BattleView newState = outcome.getSecond();
                    Node chanceNode = new Node(newState, prob);
                    // For simplicity, we assume that after a chance node, it is the MIN node (opponent's turn).
                    chanceNode.type = NodeType.MIN;
                    moveNode.children.add(chanceNode);
                }
                // Add the move node as a child of the current node.
                node.children.add(moveNode);
            }
        } else if (node.type == NodeType.MIN) {
            // TODO: Expand MIN nodes.
            // You might simulate the opponent's move.
            // For example, use an oracle to select the opponent's move and then generate a single child.
            // Here we simply create a dummy child that returns the same state.
            Node child = new Node(node.state, null, NodeType.MAX);
            child.probability = 1.0;
            node.children.add(child);
        } else if (node.type == NodeType.CHANCE) {
            // Typically, chance nodes are generated in the MAX node expansion.
            // If additional expansion is needed (e.g., post-turn effects), implement it here.
            // For now, we assume that chance nodes are leaves if no further randomness applies.
        }
    }
    */

	private class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

        // TODO: feel free to add any fields here! If you do, you should probably modify the constructor
        // of this class and add some getters for them. If the fields you add aren't final you should add setters too!
		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;

        // If you change the parameters of the constructor, you will also have to change
        // the getMove(...) method of TreeTraversalAgent!
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        // Getter methods. Since the default fields are declared final, we don't need setters
        // but if you make any fields that aren't final you should give them setters!
		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

		/**
		 * TODO: implement me!
		 * This method should perform your tree-search from the root of the entire tree.
         * You are welcome to add any extra parameters that you want! If you do, you will also have to change
         * The call method in this class!
		 * @param node the node to perform the search on (i.e. the root of the entire tree)
		 * @return The MoveView that your agent should execute
		 */
        public MoveView stochasticTreeSearch(BattleView rootView) //, int depth)
        {
            // Create the root node. We assume that at the root, it is our turn (MAX node).
            Node root = new Node(rootView, null, NodeType.MAX);
            // Expand the root node.
            expandNode(root);
            // We assume that each child of the root represents a move.
            MoveView bestMove = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (Node child : root.children) {
                double value = expectiminimax(child, 1);
                if (value > bestValue) {
                    bestValue = value;
                    bestMove = child.move;
                }
            }
            return bestMove;
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();

            MoveView move = this.stochasticTreeSearch(this.getRootView());
            double endTime = System.nanoTime();

            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 1000; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // TODO: replace me! This code calculates the first-available pokemon.
        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.

        for(int idx = 0; idx < this.getMyTeamView(view).size(); ++idx)
        {
            if(!this.getMyTeamView(view).getPokemonView(idx).hasFainted())
            {
                return idx;
            }
        }
        return null;
    }

    /**
     * This method is responsible for getting a move selected via the minimax algorithm.
     * There is some setup for this to work, namely making sure the agent doesn't run out of time.
     * Please do not modify.
     */
    @Override
    public MoveView getMove(BattleView battleView)
    {

        // will run the minimax algorithm in a background thread with a timeout
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        // preallocate so we don't spend precious time doing it when we are recording duration
        MoveView move = null;
        long durationInMs = 0;

        // this obj will run in the background
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            this.getMaxDepth(),
            this.getMyTeamIdx()
        );

        // submit the job
        Future<Pair<MoveView, Long> > future = backgroundThreadManager.submit(searcherObject);

        try
        {
            // set the timeout
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            // if we get here the move was chosen quick enough! :)
            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();

            // convert the move into a text form (algebraic notation) and stream it somewhere
            // Streamer.getStreamer(this.getFilePath()).streamMove(move, Planner.getPlanner().getGame());
        } catch(TimeoutException e)
        {
            // timeout = out of time...you lose!
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + " loses!");
            System.exit(-1);
        } catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        } catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return move;
    }
}
