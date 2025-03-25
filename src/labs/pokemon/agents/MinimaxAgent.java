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
import java.util.LinkedList;
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


public class MinimaxAgent
    extends Agent
{

    private class MinimaxSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
    {

        private final Node rootNode;
        private final int maxDepth;
        private final int myTeamIdx;

        public MinimaxSearcher(Node rootNode, int maxDepth, int myTeamIdx)
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

        public Node normalMinimax(Node node)//, int depth)
        {
            // uncomment if you want to see the tree being made
            // System.out.println(this.getTabs(node) + "Node(currentPlayer=" + (node.getCurrentPlayerTeamIdx()+1) +
            //      " isTerminal=" + node.isTerminal() + " lastMove=" +
            //     (node.getLastMoveView() == null ? null : node.getLastMoveView().getName()) + ")");

            Node bestChild = null;
            if(node.isTerminal()) // terminal state!
            {
                bestChild = node;
            // } else if(depth <= 0) // reached the end of the depth!
            // {
            //     // assign heuristic value to the child as its utility
            } else // we can get the children of this node and find its best value
            {
                List<Node> children = node.getChildren();

                double bestUtilityValue;
                if(node.getCurrentPlayerTeamIdx() == this.getMyTeamIdx()) // I'm going so this is a MAX node
                {
                    bestUtilityValue = Double.NEGATIVE_INFINITY;
                    for(Node child : children)
                    {
                        child.setUtilityValue(this.normalMinimax(child).getUtilityValue());//, depth-1).getUtilityValue());
                        if(child.getUtilityValue() > bestUtilityValue)
                        {
                            bestUtilityValue = child.getUtilityValue();
                            bestChild = child;
                        }
                    }
                } else // MIN node
                {
                    bestUtilityValue = Double.POSITIVE_INFINITY;
                    for(Node child : children)
                    {
                        child.setUtilityValue(this.normalMinimax(child).getUtilityValue());
                        if(child.getUtilityValue() < bestUtilityValue)
                        {
                            bestUtilityValue = child.getUtilityValue();
                            bestChild = child;
                        }
                    }
                }
            }

            // uncomment if you want to see the tree being made
            // System.out.println(this.getTabs(node) + "Node(currentPlayer=" + (bestChild.getCurrentPlayerTeamIdx()+1) +
            //      " isTerminal=" + bestChild.isTerminal() + " lastMove=" +
            //     (bestChild.getLastMoveView() == null ? null : bestChild.getLastMoveView().getName()) +
            //     " utility=" + bestChild.getUtilityValue() + ")");
            return bestChild;
        }

        public Node minimaxFirstLayerWhenImNotRoot(Node node)//, int depth)
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
                    Node grandChild = this.normalMinimax(child);

                    if(grandChild.getUtilityValue() < bestUtilityValue) // remember the opponent is root who is MIN
                    {
                        bestUtilityValue = grandChild.getUtilityValue();
                        bestGrandChild = grandChild;
                    }
                }
            }

            return bestGrandChild;
        }

        public Node minimaxSearch(Node node) //, int depth)
        {
            return this.getMyTeamIdx() == 0 ? this.normalMinimax(node) : this.minimaxFirstLayerWhenImNotRoot(node);
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            MoveView move = null;

            double startTime = System.nanoTime();

            Node node = this.minimaxSearch(this.getRootNode());
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

    public MinimaxAgent()
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
        MinimaxSearcher searcherObject = new MinimaxSearcher(
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
