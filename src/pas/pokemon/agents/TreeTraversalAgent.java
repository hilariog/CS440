package src.pas.pokemon.agents;

// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.callbacks.*;
import edu.bu.pas.pokemon.core.SwitchMove;
import edu.bu.pas.pokemon.core.enums.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

// JAVA PROJECT IMPORTS^^


public class TreeTraversalAgent
    extends Agent
{
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
            return null;
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

abstract class MinimaxTreeBuilder
    extends Agent
{

    private final BattleView rootView;
    private final int maxDepth;
    private final int myTeamIdx;

    public MinimaxTreeBuilder(BattleView rootView, int maxDepth, int myTeamIdx) {
        this.rootView = rootView;
        this.maxDepth = maxDepth;
        this.myTeamIdx = myTeamIdx;
    }

    // === NODE DEFINITIONS ===

    public abstract class Node {
        protected BattleView state;
        protected int casterIdx;
        protected int oppIdx;

        public Node(BattleView state, int casterIdx, int oppIdx) {
            this.state = state;
            this.casterIdx = casterIdx;
            this.oppIdx = oppIdx;
        }

        public abstract boolean isTerminal();
        public abstract double getValue();
        public abstract List<Node> getChildren();
    }

    public class MoveOrderChanceNode extends Node {
        public MoveOrderChanceNode(BattleView state, int casterIdx, int oppIdx) {
            super(state, casterIdx, oppIdx);
        }

        @Override
        public boolean isTerminal() {
            return state.isOver();
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            return getChildren().stream()
                    .mapToDouble(Node::getValue)
                    .average()
                    .orElse(0);
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();

            List<MoveView> casterMoves = getLegalMoves(state, casterIdx);
            List<MoveView> oppMoves = getLegalMoves(state, oppIdx);

            // For now: pick one representative move per side for priority/speed logic
            // Later: enumerate combinations if needed
            MoveView casterMove = casterMoves.isEmpty() ? null : casterMoves.get(0);
            MoveView oppMove = oppMoves.isEmpty() ? null : oppMoves.get(0);

            int casterPriority = (casterMove != null) ? casterMove.getPriority() : 0;
            int oppPriority = (oppMove != null) ? oppMove.getPriority() : 0;

            if (casterPriority > oppPriority) {
                children.add(new DeterministicNode(state, casterIdx, oppIdx, true));
            } else if (oppPriority > casterPriority) {
                children.add(new DeterministicNode(state, oppIdx, casterIdx, false));
            } else {
                double casterSpeed = state.getTeamView(casterIdx).getActivePokemonView().getBaseStat(Stat.SPD);
                NonVolatileStatus casterStatus = state.getTeamView(casterIdx).getActivePokemonView().getNonVolatileStatus();
                if (casterStatus == NonVolatileStatus.PARALYSIS) {
                    casterSpeed *= 0.75;
                }
                double oppSpeed = state.getTeamView(oppIdx).getActivePokemonView().getBaseStat(Stat.SPD);
                NonVolatileStatus oppStatus = state.getTeamView(oppIdx).getActivePokemonView().getNonVolatileStatus();
                if (oppStatus == NonVolatileStatus.PARALYSIS) {
                    oppSpeed *= 0.75;
                }
                //THis should be same as above:
                // double casterSpeed = state.getTeamView(casterIdx).getActivePokemonView().getCurrentStat(Stat.SPD);
                // double oppSpeed = state.getTeamView(oppIdx).getActivePokemonView().getCurrentStat(Stat.SPD);

                if (casterSpeed > oppSpeed) {
                    children.add(new DeterministicNode(state, casterIdx, oppIdx, true));
                } else if (oppSpeed > casterSpeed) {
                    children.add(new DeterministicNode(state, oppIdx, casterIdx, false));
                } else {
                    children.add(new DeterministicNode(state, casterIdx, oppIdx, true));
                    children.add(new DeterministicNode(state, oppIdx, casterIdx, false));
                }
            }

            return children;
        }
    }

    public class DeterministicNode extends Node {
        private final boolean isMaximizing;

        public DeterministicNode(BattleView state, int casterIdx, int oppIdx, boolean isMaximizing) {
            super(state, casterIdx, oppIdx);
            this.isMaximizing = isMaximizing;
        }

        @Override
        public boolean isTerminal() {
            return state.isOver();
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            return isMaximizing
                ? getChildren().stream().mapToDouble(Node::getValue).max().orElse(Double.NEGATIVE_INFINITY)
                : getChildren().stream().mapToDouble(Node::getValue).min().orElse(Double.POSITIVE_INFINITY);
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();
            for (MoveView move : getLegalMoves(state, casterIdx)) {
                children.add(new MoveResolutionChanceNode(state, casterIdx, oppIdx, move));
            }
            return children;
        }
    }

    public class MoveResolutionChanceNode extends Node {
        private final MoveView move;

        public MoveResolutionChanceNode(BattleView state, int casterIdx, int oppIdx, MoveView move) {
            super(state, casterIdx, oppIdx);
            this.move = move;
        }

        @Override
        public boolean isTerminal() {
            return state.isOver();
        }

        @Override
        public double getValue() {
            double total = 0.0;
            for (Pair<Double, Node> branch : getProbabilisticChildren()) {
                total += branch.getFirst() * branch.getSecond().getValue();
            }
            return total;
        }

        @Override
        public List<Node> getChildren() {
            return getProbabilisticChildren().stream()
                    .map(Pair::getSecond)
                    .collect(Collectors.toList());
        }

        private List<Pair<Double, Node>> getProbabilisticChildren() {
            List<Pair<Double, Node>> children = new ArrayList<>();

            PokemonView pokemon = state.getTeamView(casterIdx).getActivePokemonView();
            NonVolatileStatus status = pokemon.getNonVolatileStatus();

            double successChance;
            double confuseChance = 0.0;

            if (status == NonVolatileStatus.SLEEP) {
                successChance = 0.0;
            } else if (status == NonVolatileStatus.FREEZE) {
                successChance = 0.0;
            } else if (status == NonVolatileStatus.PARALYSIS) {
                successChance = 0.75;
            } else if (pokemon.getFlag(Flag.FLINCHED)) {
                successChance = 0.0;
            } else {
                successChance = 1.0;
            }

            if (pokemon.getFlag(Flag.CONFUSED)) {
                confuseChance = 0.5;
            }

            double correctMoveChance = successChance * (1.0 - confuseChance);
            double selfHitChance = successChance * confuseChance;

            // Real move goes through
            if (correctMoveChance > 0) {
                for (Pair<Double, BattleView> outcome : move.getPotentialEffects(state, casterIdx, oppIdx)) {
                    Node child = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx);
                    children.add(new Pair<>(correctMoveChance * outcome.getFirst(), child));
                }
            }

            // Confused and hits self
            if (selfHitChance > 0) {
                Move hurtYourselfMove = new Move(
                        "SelfDamage",
                        Type.NORMAL,
                        Move.Category.PHYSICAL,
                        40,
                        null,
                        Integer.MAX_VALUE,
                        1,
                        0
                ).addCallback(new MultiCallbackCallback(
                        new ResetLastDamageDealtCallback(),
                        new DoDamageCallback(Target.CASTER, false, false, true)
                ));

                MoveView selfHitView = hurtYourselfMove.getView();
                for (Pair<Double, BattleView> outcome : selfHitView.getPotentialEffects(state, casterIdx, oppIdx)) {
                    Node child = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx);
                    children.add(new Pair<>(selfHitChance * outcome.getFirst(), child));
                }
            }

            // Fails entirely
            if (failChance > 0) {
                Node child = new PostTurnChanceNode(state, casterIdx, oppIdx);
                children.add(new Pair<>(failChance, child));
            }

            return children;
        }
    }


    public class PostTurnChanceNode extends Node {
        public PostTurnChanceNode(BattleView state, int casterIdx, int oppIdx) {
            super(state, casterIdx, oppIdx);
        }

        @Override
        public boolean isTerminal() {
            return state.isOver();
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }else{//if not terminal, this gets the average value of all possible resolutions
                List<Node> children = getChildren();
                double total = 0.0;
                for (Node child : children) {
                    total += child.getValue();
                }
                return total / children.size();
            }
        }

        @Override
        public List<Node> getChildren() {
            /*
            ApplyPostTurnDocumentation from API:
            "A method to get all of the ways post-turn logic can be applied to this Battle.BattleView. 
            Note that this call may invoke an Agent's chooseNextPokemon method in the presence of fainted pokemon. 
            If no pokemon has fainted, this method will return a list with only one element in it. 
            If a pokemon faints, all choices of replacements are swapped in to generate multiple Battle.BattleView instances. 
            If two pokemon faint, then the cross-product of potential swaps across both teams will be used to generate Battle.BattleView instances.
            Returns:
            All Battle.BattleView instances that could occur after post-turn logic is applied. 
            This includes all ways of replacing fainted pokemon across both teams."
            */

            List<Node> children = new ArrayList<>();
            List<BattleView> postTurnOutcomes = state.applyPostTurnConditions();//get outcomes

            //create node for each child outcome
            for (BattleView outcome : postTurnOutcomes) {
                children.add(new MoveOrderChanceNode(outcome, casterIdx, oppIdx));//add to return list
            }
            if (children.isEmpty()) {
                children.add(new MoveOrderChanceNode(state, casterIdx, oppIdx));
            }
            return children;    
        }
    }

    public static List<Move.MoveView> getLegalMoves(BattleView state, int playerIdx) {
        List<Move.MoveView> legalMoves = new ArrayList<>();

        TeamView currentTeam = state.getTeamView(playerIdx);
        PokemonView active = currentTeam.getActivePokemonView();

        // Add attack/status moves
        for (MoveView move : active.getAvailableMoves()) {
            if (move.getPP() != null && move.getPP() > 0) {
                legalMoves.add(move);
            }
        }

        // Add switch moves only if the active Pokémon is NOT trapped
        if (!active.getFlag(Flag.TRAPPED)) {
            int activeIdx = currentTeam.getActivePokemonIdx();

            for (int i = 0; i < currentTeam.size(); i++) {
                if (i == activeIdx) continue;

                PokemonView poke = currentTeam.getPokemonView(i);
                if (!poke.hasFainted()) {
                    SwitchMove switchMove = new SwitchMove(i);
                    legalMoves.add(switchMove.getView());
                }
            }
        }

        return legalMoves;
    }


    private double evaluate(BattleView state) {
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
                t2Hp += t2.getPokemonView(i).getCurrentStat(Stat.HP);
                i++;
            }
            i = 0;
            while(t1.getPokemonView(i) != null){
                t1Hp += t1.getPokemonView(i).getCurrentStat(Stat.HP);
                i++;
            }
            return (((t1Hp / (t1Hp + t2Hp)) * 2) - 1);
        }
    }
}