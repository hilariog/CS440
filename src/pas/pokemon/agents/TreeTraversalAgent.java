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
import edu.bu.pas.pokemon.core.SwitchMove.SwitchMoveView;
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
    private int startAliveCount = -1;

	private class StochasticTreeSearcher
        extends MinimaxTreeBuilder
        implements Callable<Pair<MoveView, Long>>
	{
		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;
        
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            super(rootView, maxDepth, myTeamIdx);
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

		/**
		 * This method performs your tree-search from the root of the entire tree.
		 * @return The MoveView that your agent should execute
		 */
        public MoveView stochasticTreeSearch(BattleView rootView) {
            List<MoveView> myMoves = getLegalMoves(rootView, this.getMyTeamIdx());
            if (myMoves.isEmpty()) {
                return null;
            }

            double bestValue = Double.NEGATIVE_INFINITY;
            MoveView bestMove = null;
            int oppIdx = (this.getMyTeamIdx() == 0) ? 1 : 0;

            for (MoveView candidateMove : myMoves) {
                Node nodeForMove = new MoveResolutionChanceNode(
                    rootView, 
                    this.getMyTeamIdx(),
                    oppIdx,
                    0,
                    candidateMove
                );
                
                double value = nodeForMove.getValue();

                if (value > bestValue) {
                    bestValue = value;
                    bestMove = candidateMove;
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

            return new Pair<MoveView, Long>(move, (long)((endTime - startTime)/1_000_000));
        }
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 4; // or whatever depth you want
    }

    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView state)
    {
        // Get our team
        TeamView myTeam = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();
        int oppTeamIdx = oppTeam.getBattleIdx();
        int myTeamIdx = myTeam.getBattleIdx();
        int exploreDepth = computeDynamicDepth(state);

        // Potential switch moves
        List<SwitchMoveView> switchMoves = MinimaxTreeBuilder.getLegalSwitches(state, myTeamIdx);
        if (switchMoves.isEmpty()) {
            // Possibly means no available switch => might have lost
            return -1; 
        }

        int bestCandidate = -1;
        double bestUtility = Double.NEGATIVE_INFINITY;
        
        // Evaluate each potential switch with a deeper tree
        for (SwitchMoveView switchMove : switchMoves) {
            List<Pair<Double, BattleView>> outcome = switchMove.getPotentialEffects(state, myTeamIdx, oppTeamIdx);
            if (outcome.isEmpty()) {
                continue;
            }

            // Just take the first outcome from switching (most switches yield exactly one outcome)
            BattleView outcomeState = outcome.get(0).getSecond();

            // Build a quick tree
            MinimaxTreeBuilder treeBuilder = new MinimaxTreeBuilder(outcomeState, exploreDepth, myTeamIdx) {};
            MinimaxTreeBuilder.Node rootNode = treeBuilder.new MoveOrderChanceNode(outcomeState, myTeamIdx, oppTeamIdx, 0);
            double candidateUtility = rootNode.getValue();
            
            // Grab the new active index from the switch, if we can
            int candidateIdx;
            try {
                candidateIdx = switchMove.getNewActiveIdx();
            } catch (NumberFormatException e) {
                candidateIdx = -1;
            }
            
            if (candidateUtility > bestUtility) {
                bestUtility = candidateUtility;
                bestCandidate = candidateIdx;
            }
        }
        return bestCandidate;
    }

    @Override
    public MoveView getMove(BattleView battleView)
    {
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();
        MoveView move = null;
        long durationInMs = 0;
        int dynamicDepth = computeDynamicDepth(battleView) - 1;

        // Create the searcher
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            dynamicDepth,
            this.getMyTeamIdx()
        );

        // Submit the job in the background
        Future<Pair<MoveView, Long>> future = backgroundThreadManager.submit(searcherObject);

        try
        {
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();
        }
        catch(TimeoutException e)
        {
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + "] loses!");
            System.exit(-1);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return move;
    }

    // helper for scaling max depth dynamically
    private int countAllAlive(BattleView state) {
        int alive = 0;

        for (int i = 0; i < state.getTeam1View().size(); i++) {
            PokemonView p = state.getTeam1View().getPokemonView(i);
            if (p == null) break; 
            if (!p.hasFainted()) {
                alive++;
            }
        }

        for (int i = 0; i < state.getTeam2View().size(); i++) {
            PokemonView p = state.getTeam2View().getPokemonView(i);
            if (p == null) break; 
            if (!p.hasFainted()) {
                alive++;
            }
        }

        return alive;
    }

    // compute dynamic depth
    private int computeDynamicDepth(BattleView state) {
        // only reset alive count if still -1 from initializing
        if (this.startAliveCount < 0) {
            this.startAliveCount = countAllAlive(state);
        }

        int currentAlive = countAllAlive(state);

        int fainted = this.startAliveCount - currentAlive;
        int halfFainted = fainted / 4;
        int adaptiveDepth = 3 * (1 + halfFainted);
        adaptiveDepth = Math.max(3, Math.min(adaptiveDepth, 5));

        return adaptiveDepth;
    }
}

abstract class MinimaxTreeBuilder
{
    private final BattleView rootView;
    private final int maxDepth;
    protected final int myTeamIdx;

    public MinimaxTreeBuilder(BattleView rootView, int maxDepth, int myTeamIdx) {
        this.rootView = rootView;
        this.maxDepth = maxDepth;
        this.myTeamIdx = myTeamIdx;
    }

    // Nodes, got structure from piazza

    public abstract class Node {
        protected BattleView state;
        protected int casterIdx;
        protected int oppIdx;
        protected int depth;

        public Node(BattleView state, int casterIdx, int oppIdx, int depth) {
            this.state = state;
            this.casterIdx = casterIdx;
            this.oppIdx = oppIdx;
            this.depth = depth;
        }

        public double getValue() {
            return getValue(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        public double getValue(double alpha, double beta) { 
            return getValue();
        }

        public abstract boolean isTerminal();
        public abstract List<Node> getChildren();
    }

    // creates chance nodes based on possible move ordering with priority and speed
    public class MoveOrderChanceNode extends Node {
        public MoveOrderChanceNode(BattleView state, int casterIdx, int oppIdx, int depth) {
            super(state, casterIdx, oppIdx, depth);
        }

        @Override
        public boolean isTerminal() {
            return (state.isOver() || depth >= maxDepth);
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            List<Node> children = getChildren();
            if (children.isEmpty()) {
                return evaluate(state);
            }
            // assume uniform probabilty so use average of values
            return children.stream().mapToDouble(Node::getValue).average().orElse(0.0);
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();

            List<MoveView> casterMoves = getLegalMoves(state, casterIdx);
            List<MoveView> oppMoves    = getLegalMoves(state, oppIdx);

            // If no moves, nothing to expand
            if (casterMoves.isEmpty() && oppMoves.isEmpty()) {
                return children;
            }

            for (MoveView cMove : casterMoves) {
                for (MoveView oMove : oppMoves) {

                    // Compare priority
                    int cPriority = (cMove != null) ? cMove.getPriority() : 0;
                    int oPriority = (oMove != null) ? oMove.getPriority() : 0;

                    if (cPriority > oPriority) {
                        // caster moves first
                        boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                        children.add(new DeterministicNode(state, casterIdx, oppIdx, casterIsMyTeam, depth + 1, cMove, oMove));
                    }
                    else if (oPriority > cPriority) {
                        // opponent moves first
                        boolean oppIsMyTeam = (oppIdx == myTeamIdx);
                        children.add(
                            new DeterministicNode(
                                state, 
                                oppIdx, 
                                casterIdx, 
                                oppIsMyTeam, 
                                depth + 1,
                                oMove, 
                                cMove
                            )
                        );
                    }
                    else {
                        // tie on priority => compare Speed
                        double casterSpeed = adjustedSpeed(state.getTeamView(casterIdx).getActivePokemonView());
                        double oppSpeed    = adjustedSpeed(state.getTeamView(oppIdx).getActivePokemonView());

                        if (casterSpeed > oppSpeed) {
                            boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                            children.add(new DeterministicNode(state, casterIdx, oppIdx, casterIsMyTeam, depth + 1, cMove, oMove));
                        }
                        else if (oppSpeed > casterSpeed) {
                            boolean oppIsMyTeam = (oppIdx == myTeamIdx);
                            children.add(new DeterministicNode(state, oppIdx, casterIdx, oppIsMyTeam, depth + 1, oMove, cMove));
                        }
                        else {
                            // Perfect tie => we consider both orders
                            boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                            boolean oppIsMyTeam    = (oppIdx == myTeamIdx);

                            // Child 1: caster first
                            children.add(new DeterministicNode(state, casterIdx, oppIdx, casterIsMyTeam, depth + 1, cMove, oMove));
                            // Child 2: opp first
                            children.add(new DeterministicNode(state, oppIdx, casterIdx, oppIsMyTeam, depth + 1, oMove, cMove));
                        }
                    }
                }
            }

            return children;
        }
    }

    // we know now who goes first so now we implement max/mini levels that are not chance, which means we can also do some alpha beta pruning
    public class DeterministicNode extends Node {
        private final boolean isMaximizing;
        private final MoveView firstMove;
        private final MoveView secondMove;
        private final int firstCasterIdx;
        private final int secondCasterIdx;

        public DeterministicNode(
            BattleView state,
            int firstCasterIdx,
            int secondCasterIdx,
            boolean isMaximizing,
            int depth,
            MoveView firstMove,
            MoveView secondMove
        ) {
            super(state, firstCasterIdx, secondCasterIdx, depth);
            this.isMaximizing     = isMaximizing;
            this.firstMove        = firstMove;
            this.secondMove       = secondMove;
            this.firstCasterIdx   = firstCasterIdx;
            this.secondCasterIdx  = secondCasterIdx;
        }

        @Override
        public boolean isTerminal() {
            return state.isOver() || depth >= maxDepth;
        }

        @Override
        public double getValue() {
            return getValue(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        // alpha beta override when called for deterministic override
        @Override
        public double getValue(double alpha, double beta) {
            if (isTerminal()) {
                return evaluate(state);
            }

            List<Node> children = getChildren();
            if (children.isEmpty()) {
                return evaluate(state);
            }

            if (isMaximizing) {
                double value = Double.NEGATIVE_INFINITY;
                for (Node child : children) {
                    value = Math.max(value, child.getValue(alpha, beta));
                    alpha = Math.max(alpha, value);
                    if (alpha >= beta) {
                        break;
                    }
                }
                return value;
            } else {
                double value = Double.POSITIVE_INFINITY;
                for (Node child : children) {
                    value = Math.min(value, child.getValue(alpha, beta));
                    beta  = Math.min(beta, value);
                    if (alpha >= beta) {
                        break;
                    }
                }
                return value;
            }
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();

            List<Pair<Double, BattleView>> firstOutcomes =
                firstMove.getPotentialEffects(state, firstCasterIdx, secondCasterIdx);

            for (Pair<Double, BattleView> firstOutcome : firstOutcomes) {
                BattleView afterFirst = firstOutcome.getSecond();

                List<Pair<Double, BattleView>> secondOutcomes =
                    secondMove.getPotentialEffects(afterFirst, secondCasterIdx, firstCasterIdx);

                for (Pair<Double, BattleView> secondOutcome : secondOutcomes) {
                    BattleView finalState = secondOutcome.getSecond();
                    Node next = new PostTurnChanceNode(finalState, firstCasterIdx, secondCasterIdx, depth + 1);
                    children.add(next);
                }
            }

            return children;
        }
    }

    // once we have decided which move we need to make a chance node of it resolving before we max/min
    public class MoveResolutionChanceNode extends Node {
        private final MoveView move;

        public MoveResolutionChanceNode(BattleView state, int casterIdx, int oppIdx, int depth, MoveView move) {
            super(state, casterIdx, oppIdx, depth);
            this.move = move;
        }

        @Override
        public boolean isTerminal() {
            return (state.isOver() || depth >= maxDepth);
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            double total = 0.0;
            List<Pair<Double, Node>> branches = getProbabilisticChildren();
            for (Pair<Double, Node> branch : branches) {
                double prob = branch.getFirst();
                double val  = branch.getSecond().getValue();
                total += prob * val;
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

            // got numbers from documentation, checks success of attack
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

            // sums to one
            double correctMoveChance = successChance * (1.0 - confuseChance);
            double selfHitChance     = successChance * confuseChance;
            double failChance        = 1.0 - successChance;

            // success
            if (correctMoveChance > 0) {
                for (Pair<Double, BattleView> outcome : move.getPotentialEffects(state, casterIdx, oppIdx)) {
                    Node child = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx, depth + 1);
                    children.add(new Pair<>(correctMoveChance * outcome.getFirst(), child));
                }
            }

            // hit self
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
                    Node child = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx, depth + 1);
                    children.add(new Pair<>(selfHitChance * outcome.getFirst(), child));
                }
            }

            // fails
            if (failChance > 0) {
                Node child = new PostTurnChanceNode(state, casterIdx, oppIdx, depth + 1);
                children.add(new Pair<>(failChance, child));
            }

            return children;
        }
    }

    // applys all effects from moves
    public class PostTurnChanceNode extends Node {
        public PostTurnChanceNode(BattleView state, int casterIdx, int oppIdx, int depth) {
            super(state, casterIdx, oppIdx, depth);
        }

        @Override
        public boolean isTerminal() {
            return (state.isOver() || depth >= maxDepth);
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            List<Node> children = getChildren();
            if (children.isEmpty()) {
                return evaluate(state);
            }

            double total = 0.0;
            for (Node child : children) {
                total += child.getValue();
            }
            return total / children.size();
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();
            List<BattleView> postTurnOutcomes = state.applyPostTurnConditions();

            for (BattleView outcome : postTurnOutcomes) {
                children.add(new MoveOrderChanceNode(outcome, casterIdx, oppIdx, depth + 1));
            }
            if (children.isEmpty()) {
                children.add(new MoveOrderChanceNode(state, casterIdx, oppIdx, depth + 1));
            }
            return children;
        }
    }

    // helper to multiply speed by 0.75
    protected double adjustedSpeed(PokemonView pokemon) {
        double spd = pokemon.getBaseStat(Stat.SPD);
        if (pokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) {
            spd *= 0.75;
        }
        return spd;
    }

    // all legal moves from available moves appended to legal switches
    public static List<Move.MoveView> getLegalMoves(BattleView state, int playerIdx) {
        List<Move.MoveView> legalMoves = new ArrayList<>();
        TeamView currentTeam = state.getTeamView(playerIdx);
        PokemonView active = currentTeam.getActivePokemonView();

        for (MoveView move : active.getAvailableMoves()) {
            if (move.getPP() != null && move.getPP() > 0) {
                legalMoves.add(move);
            }
        }

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

    // get legal switches
    public static List<SwitchMove.SwitchMoveView> getLegalSwitches(BattleView state, int playerIdx){
        List<SwitchMove.SwitchMoveView> legalSwitches = new ArrayList<>();
        TeamView team = state.getTeamView(playerIdx);
        int replaceIdx = team.getActivePokemonIdx();

        for(int i = 0; i < team.size(); i++){
            if(i == replaceIdx) continue; 
            PokemonView poke = team.getPokemonView(i);
            if(!poke.hasFainted()){
                SwitchMove switchMove = new SwitchMove(i);
                legalSwitches.add((SwitchMove.SwitchMoveView) switchMove.getView());
            }
        }
        return legalSwitches;
    }

    // our heuristic for early terminated states
    protected double evaluate(BattleView state) {
        TeamView t1 = state.getTeam1View();
        TeamView t2 = state.getTeam2View();
        
        if (state.isOver()){
            // If team2's active has fainted => we (team1) presumably win => +1
            if (t2.getActivePokemonView().hasFainted()) return 1.0;
            else return -1.0;
        } else {
            // Heuristic: sum HP of each side
            int t1Hp = 0;
            int t2Hp = 0;

            // Count HP of all t2 Pokemon
            for (int i = 0; i < t2.size(); i++) {
                PokemonView poke = t2.getPokemonView(i);
                if (poke == null) break;
                t2Hp += poke.getCurrentStat(Stat.HP);
            }

            // Count HP of all t1 Pokemon
            for (int i = 0; i < t1.size(); i++) {
                PokemonView poke = t1.getPokemonView(i);
                if (poke == null) break;
                t1Hp += poke.getCurrentStat(Stat.HP);
            }

            // Score in [-1, 1],  +1 means t1 is at full advantage, -1 means t2 is
            double ratio = (double)t1Hp / ((double)t1Hp + (double)t2Hp);
            return (ratio * 2) - 1; 
        }
    }
}
