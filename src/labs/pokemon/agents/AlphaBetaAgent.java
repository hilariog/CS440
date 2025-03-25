package src.labs.pokemon.agents;


// SYSTEM IMPORTS
import edu.bu.labs.pokemon.core.Agent;
import edu.bu.labs.pokemon.core.Battle;
import edu.bu.labs.pokemon.core.Battle.BattleView;
import edu.bu.labs.pokemon.core.Team;
import edu.bu.labs.pokemon.core.Team.TeamView;
import edu.bu.labs.pokemon.core.Move;
import edu.bu.labs.pokemon.core.Move.MoveView;
import edu.bu.labs.pokemon.traversal.Node;
import edu.bu.labs.pokemon.utils.Pair;


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


public class AlphaBetaAgent
    extends Agent
{

    // private static class MoveOrderer
    //     extends Object
    // {

    //     /**
    //      * TODO: implement me!
    //      * This method should order the children in a way that encourages alpha-beta pruning to prune!
    //      * @param children The children to order
    //      * @return the ordered list of the children
    //      */
    //     public static List<Node> order(List<Node> children)
    //     {
    //         return children;
    //     }

    // }
    private static class MoveOrderer extends Object {
        public static List<Node> order(List<Node> children) {
            // Sort children based on their utility value to encourage pruning
            children.sort((n1, n2) -> {
                double v1 = n1.getUtilityValue();
                double v2 = n2.getUtilityValue();
                return (n1.getCurrentPlayerTeamIdx() == 0) ? Double.compare(v2, v1) // Max node: Descending
                                                        : Double.compare(v1, v2); // Min node: Ascending
            });
            return children;
        }
    }


	private class AlphaBetaSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

		private final Node rootNode;
        private final int maxDepth;
        private final int myTeamIdx;

		public AlphaBetaSearcher(Node rootNode, int maxDepth, int myTeamIdx)
        {
            this.rootNode = rootNode;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

		public Node getRootNode() { return this.rootNode; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

        public String getTabs(Node node)
        {
            StringBuilder b = new StringBuilder();
            for(int idx = 0; idx < node.getDepth(); ++idx)
            {
                b.append("\t");
            }
            return b.toString();
        }

		/**
		//  * TODO: implement me!
		//  * This method should perform alpha-beta search from the current node
		//  * @param node the node to perform the search on (i.e. the root of the subtree)
		//  * @param alpha
		//  * @param beta
		//  * @return The best childNode
		//  */
		// public Node normalAlphaBeta(Node node,
        //                             double alpha,
        //                             double beta)
		// {
		// 	Node bestChild = null;
		// 	return bestChild;
		// }

        public Node normalAlphaBeta(Node node, double alpha, double beta) {
            if (node.isTerminal()) { // If it's a terminal node, return it (game over)
                return node;
            }

            Node bestChild = null;
            double bestValue = (node.getCurrentPlayerTeamIdx() == 0) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

            // Order children before searching to improve pruning efficiency
            List<Node> orderedChildren = MoveOrderer.order(node.getChildren());

            for (Node child : orderedChildren) {
                Node result = normalAlphaBeta(child, alpha, beta);
                double childValue = result.getUtilityValue();

                if (node.getCurrentPlayerTeamIdx() == 0) { // Max node
                    if (childValue > bestValue) {
                        bestValue = childValue;
                        bestChild = child;
                    }
                    alpha = Math.max(alpha, bestValue);
                } else { // Min node
                    if (childValue < bestValue) {
                        bestValue = childValue;
                        bestChild = child;
                    }
                    beta = Math.min(beta, bestValue);
                }

                // Prune if the current branch is no longer useful
                if (alpha >= beta) {
                    break;
                }
            }

            // Set the utility value before returning (needed for grandchild handling)
            if (bestChild != null) {
                bestChild.setUtilityValue(bestValue);
            }

            return bestChild;
        }


		public Node minimaxFirstLayerWhenImNotRoot(Node node,
                                                   double alpha,
                                                   double beta)//, int depth)
        {
            // this is special...since p1 ALWAYS goes first, if we find ourself as p2, then we are not the root
            // of the game tree. Instead of choosing the best child of the root, we need to choose a *grandchild*
            // however, our opponent will still get to choose their best option, so we need to return the
            // best child of *whatever child our opponent picks*
            Node bestGrandChild = null;

            double bestUtilityValue = Double.POSITIVE_INFINITY;
            for(Node child : node.getChildren())
            {
                if(!child.isTerminal())
                {
                    Node grandChild = this.normalAlphaBeta(child, alpha, beta);

                    if(grandChild.getUtilityValue() < bestUtilityValue) // remember the opponent is root who is MIN
                    {
                        bestUtilityValue = grandChild.getUtilityValue();
                        bestGrandChild = grandChild;
                    }
                }
            }

            return bestGrandChild;
        }

        public Node alphaBetaSearch(Node node,
                                    double alpha,
                                    double beta) //, int depth)
        {
            return this.getMyTeamIdx() == 0 ? this.normalAlphaBeta(node, alpha, beta)
                : this.minimaxFirstLayerWhenImNotRoot(node, alpha, beta);
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            MoveView move = null;

            double startTime = System.nanoTime();

            Node node = this.alphaBetaSearch(this.getRootNode(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            if(node != null)
            {
                move = node.getLastMoveView();
            }
            double endTime = System.nanoTime();

            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public AlphaBetaAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000; // 3 min
        this.maxDepth = 1000;
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // find the next pokemon that is active
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
        Node rootNode = new Node(battleView, this.getMyTeamIdx(), 0, 0);

        // this obj will run in the background
        AlphaBetaSearcher searcherObject = new AlphaBetaSearcher(
            rootNode,
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
