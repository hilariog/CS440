package src.pas.pokemon.agents;

// SYSTEM IMPORTS....
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
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


//  This agent uses a minimax-based approach with alpha-beta
//  and move-ordering selection that considers "power * type effectiveness"
//  vs. the opponent's active Pokemon, which may have up to 2 types.
public class TreeTraversalAgent
    extends Agent
{
    private static final int MAX_DEPTH = 3; //I found that four expands too much on certain moves
    private long maxThinkingTimePerMoveInMS;

    public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 60000; //  60s
    }

    public long getMaxThinkingTimePerMoveInMS() {//getter
        return this.maxThinkingTimePerMoveInMS;
    }

	private class StochasticTreeSearcher
        extends MinimaxTreeBuilder
        implements Callable<Pair<MoveView, Long>>
	{
		private final BattleView rootView;
        
		public StochasticTreeSearcher(BattleView rootView, int myTeamIdx)
        {
            // pass in the static MAX_DEPTH from our agent, fixed rn but possible dynamic!
            super(rootView, MAX_DEPTH, myTeamIdx);
            this.rootView = rootView;
        }

		public BattleView getRootView() {
            return this.rootView;
        }
 
        //Perform a tree-search from the root
        public MoveView stochasticTreeSearch(BattleView rootView) {
            List<MoveView> myMoves = getLegalMoves(rootView, this.getMyTeamIdx());//legal moves includess poekmon and trainer moves for me and opp
            if (myMoves.isEmpty()) {
                return null;
            }

            double bestValue = Double.NEGATIVE_INFINITY;
            MoveView bestMove = null;
            int oppIdx = (this.getMyTeamIdx() == 0) ? 1 : 0;

            // Sort + limit moves
            myMoves = orderAndLimitMoves(rootView, this.getMyTeamIdx(), oppIdx, myMoves);//gets strongest starting moves

            for (MoveView candidateMove : myMoves) {//expand node for candidate moves
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

            return new Pair<>(move, (long)((endTime - startTime)/1_000_000));
        }
	}

    @Override
    public Integer chooseNextPokemon(BattleView state)//called when a pokemon faints by postturn chance node
    {
        TeamView myTeam  = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();
        int myTeamIdx    = myTeam.getBattleIdx();
        int oppTeamIdx   = oppTeam.getBattleIdx();

        List<SwitchMoveView> switchMoves = MinimaxTreeBuilder.getLegalSwitches(state, myTeamIdx);
        if (switchMoves.isEmpty()) {
            return -1;
        }

        int bestCandidate = -1;
        double bestUtility = Double.NEGATIVE_INFINITY;

        // smaller depth for nextPokemon
        int depth = 1;
        for (SwitchMoveView sw : switchMoves) {
            List<Pair<Double, BattleView>> outcomeList = sw.getPotentialEffects(state, myTeamIdx, oppTeamIdx);
            if (outcomeList.isEmpty()) {
                continue;
            }
            BattleView outState = outcomeList.get(0).getSecond();

            // Build a small tree
            MinimaxTreeBuilder tb = new MinimaxTreeBuilder(outState, depth, myTeamIdx){};
            MinimaxTreeBuilder.Node root = tb.new MoveOrderChanceNode(outState, myTeamIdx, oppTeamIdx, 0);

            double val = root.getValue();
            int candidateIdx;
            try {
                candidateIdx = sw.getNewActiveIdx();
            } catch (NumberFormatException e) {
                candidateIdx = -1;
            }

            if (val > bestUtility) {
                bestUtility = val;
                bestCandidate = candidateIdx;
            }
        }
        return bestCandidate;
    }

    @Override
    public MoveView getMove(BattleView battleView)//given func dont edit!
    {
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();
        MoveView move = null;

        StochasticTreeSearcher searcher = new StochasticTreeSearcher(
            battleView,
            this.getMyTeamIdx()
        );

        Future<Pair<MoveView, Long>> future = backgroundThreadManager.submit(searcher);
        try {
            Pair<MoveView, Long> result = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );
            move = result.getFirst();
        }
        catch(TimeoutException e) {
            System.err.println("Timeout!");
            System.exit(-1);
        }
        catch(InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return move;
    }
}

// Minimal MinimaxTreeBuilder
// I took out quiescence, but includes "power * type effectiveness" move ordering
abstract class MinimaxTreeBuilder
{
    public static final int MAX_MOVES_TO_EXPAND = 5; // Top number of moves to expand

    private final BattleView rootView;
    private final int maxDepth;
    protected final int myTeamIdx;

    public MinimaxTreeBuilder(BattleView rootView, int maxDepth, int myTeamIdx) {
        this.rootView = rootView;
        this.maxDepth = maxDepth;
        this.myTeamIdx = myTeamIdx;
    }

    public int getMyTeamIdx() { 
        return this.myTeamIdx; 
    }

    
    //Order moves by (base power * total effectiveness),
    //then limit to top N (MAX_MOVES_TO_EXPAND).
    protected List<MoveView> orderAndLimitMoves(
        BattleView state, int casterIdx, int oppIdx, List<MoveView> allMoves
    ){
        // Sort descending
        allMoves.sort((m1, m2) -> {
            double e1 = estimatedPower(m1, state, casterIdx, oppIdx);
            double e2 = estimatedPower(m2, state, casterIdx, oppIdx);
            return Double.compare(e2, e1);
        });

        // Limit to top X
        if (allMoves.size() > MAX_MOVES_TO_EXPAND) {
            return allMoves.subList(0, MAX_MOVES_TO_EXPAND);
        }
        return allMoves;
    }

    //calculates a metric based on power and effectiveness for move order
    private double estimatedPower(MoveView mv, BattleView state, int casterIdx, int oppIdx) {
        // If it's a switch or no base power => 0
        if (mv instanceof SwitchMoveView) {
            return 0.0;
        }
        Integer basePow = mv.getPower();
        if (basePow == null || basePow <= 0) {
            return 0.0;
        }
        Type attackType = mv.getType();
        if (attackType == null) {
            return basePow; // fallback
        }

        // If the opponent has an active
        TeamView foeTeam = state.getTeamView(oppIdx);
        PokemonView foePoke = foeTeam.getActivePokemonView();
        if (foePoke == null) {
            return basePow; // fallback
        }

        // Handle up to 2 types
        Type foeDefType1 = foePoke.getCurrentType1();  // might be null
        Type foeDefType2 = foePoke.getCurrentType2();  // might be null

        double totalEffect = 1.0;
        if (foeDefType1 != null) {
            totalEffect *= Type.getEffectivenessModifier(attackType, foeDefType1);
        }
        if (foeDefType2 != null) {
            totalEffect *= Type.getEffectivenessModifier(attackType, foeDefType2);
        }

        return basePow * totalEffect;
    }

    // Legal Switches called by choose next pokemon
    public static List<SwitchMove.SwitchMoveView> getLegalSwitches(BattleView state, int playerIdx) {
        List<SwitchMove.SwitchMoveView> out = new ArrayList<>();
        TeamView team = state.getTeamView(playerIdx);
        int activeIdx = team.getActivePokemonIdx();
        for(int i=0; i<team.size(); i++){
            if(i == activeIdx) continue;
            PokemonView p = team.getPokemonView(i);
            if(!p.hasFainted()){
                SwitchMove sw = new SwitchMove(i);
                out.add((SwitchMove.SwitchMoveView) sw.getView());
            }
        }
        return out;
    }

    // Legal Moves called by move order
    public static List<Move.MoveView> getLegalMoves(BattleView state, int playerIdx) {
        List<Move.MoveView> moves = new ArrayList<>();
        TeamView tv = state.getTeamView(playerIdx);
        PokemonView active = tv.getActivePokemonView();
        if (active == null) {
            return moves; // none
        }
        for (MoveView m : active.getAvailableMoves()) {
            if (m.getPP() != null && m.getPP() > 0) {
                moves.add(m);
            }
        }
        // Switch if not trapped
        if (!active.getFlag(Flag.TRAPPED)) {
            int activeIdx = tv.getActivePokemonIdx();
            for (int i = 0; i < tv.size(); i++) {
                if (i == activeIdx) continue;
                PokemonView p = tv.getPokemonView(i);
                if (!p.hasFainted()) {
                    SwitchMove sw = new SwitchMove(i);
                    moves.add(sw.getView());
                }
            }
        }
        return moves;
    }

    //Node classes
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

        protected boolean baseTerminalCheck() {
            return (state.isOver() || depth >= maxDepth);
        }

        protected double evaluate(BattleView st) {
            // Basic HP sum heuristic
            TeamView t1 = st.getTeam1View();
            TeamView t2 = st.getTeam2View();
            
            if (st.isOver()) {
                // If t2's active is fainted => t1 presumably wins => +1
                if (t2.getActivePokemonView().hasFainted()) {
                    return 1.0;
                } else {
                    return -1.0;
                }
            } else {
                int t1Hp = 0;
                int t2Hp = 0;
                for (int i = 0; i < t2.size(); i++) {
                    PokemonView poke = t2.getPokemonView(i);
                    if (poke == null) break;
                    t2Hp += poke.getCurrentStat(Stat.HP);
                }
                for (int i = 0; i < t1.size(); i++) {
                    PokemonView poke = t1.getPokemonView(i);
                    if (poke == null) break;
                    t1Hp += poke.getCurrentStat(Stat.HP);
                }
                double ratio = (double)t1Hp / (t1Hp + t2Hp);
                return (ratio * 2) - 1; 
            }
        }
    }
    
    // MoveOrderChanceNode
    public class MoveOrderChanceNode extends Node {
        public MoveOrderChanceNode(BattleView state, int casterIdx, int oppIdx, int depth) {
            super(state, casterIdx, oppIdx, depth);
        }

        @Override
        public boolean isTerminal() {
            return baseTerminalCheck();
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
            // average
            double sum = 0.0;
            for (Node c : children) {
                sum += c.getValue();
            }
            return sum / children.size();
        }

        @Override
        public List<Node> getChildren() {
            List<Node> kids = new ArrayList<>();

            // For each side, get & limit
            List<MoveView> casterMoves = getLegalMoves(state, casterIdx);
            casterMoves = orderAndLimitMoves(state, casterIdx, oppIdx, casterMoves);

            List<MoveView> oppMoves    = getLegalMoves(state, oppIdx);
            oppMoves = orderAndLimitMoves(state, oppIdx, casterIdx, oppMoves);

            if (casterMoves.isEmpty() && oppMoves.isEmpty()) {
                return kids;
            }

            for (MoveView cMove : casterMoves) {
                for (MoveView oMove : oppMoves) {
                    int cPrio = (cMove != null) ? cMove.getPriority() : 0;
                    int oPrio = (oMove != null) ? oMove.getPriority() : 0;

                    if (cPrio > oPrio) {
                        boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                        kids.add(new DeterministicNode(
                            state, casterIdx, oppIdx, casterIsMyTeam, depth+1, cMove, oMove
                        ));
                    }
                    else if (oPrio > cPrio) {
                        boolean oppIsMyTeam = (oppIdx == myTeamIdx);
                        kids.add(new DeterministicNode(
                            state, oppIdx, casterIdx, oppIsMyTeam, depth+1, oMove, cMove
                        ));
                    }
                    else {
                        double cSpd = adjustedSpeed(state.getTeamView(casterIdx).getActivePokemonView());
                        double oSpd = adjustedSpeed(state.getTeamView(oppIdx).getActivePokemonView());
                        if (cSpd > oSpd) {
                            boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                            kids.add(new DeterministicNode(
                                state, casterIdx, oppIdx, casterIsMyTeam, depth+1, cMove, oMove
                            ));
                        }
                        else if (oSpd > cSpd) {
                            boolean oppIsMyTeam = (oppIdx == myTeamIdx);
                            kids.add(new DeterministicNode(
                                state, oppIdx, casterIdx, oppIsMyTeam, depth+1, oMove, cMove
                            ));
                        }
                        else {
                            // perfect tie => consider both orders
                            boolean casterIsMyTeam = (casterIdx == myTeamIdx);
                            boolean oppIsMyTeam    = (oppIdx == myTeamIdx);

                            kids.add(new DeterministicNode(
                                state, casterIdx, oppIdx, casterIsMyTeam, depth+1, cMove, oMove
                            ));
                            kids.add(new DeterministicNode(
                                state, oppIdx, casterIdx, oppIsMyTeam, depth+1, oMove, cMove
                            ));
                        }
                    }
                }
            }
            return kids;
        }
    }

    // DeterministicNode
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
            return baseTerminalCheck();
        }

        @Override
        public double getValue() {
            return getValue(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        @Override
        public double getValue(double alpha, double beta) {
            if (isTerminal()) {
                return evaluate(state);
            }
            List<Node> kids = getChildren();
            if (kids.isEmpty()) {
                return evaluate(state);
            }

            if (isMaximizing) {
                double val = Double.NEGATIVE_INFINITY;
                for (Node c : kids) {
                    double cv = c.getValue(alpha, beta);
                    val = Math.max(val, cv);
                    alpha = Math.max(alpha, val);
                    if (alpha >= beta) {
                        break; // prune
                    }
                }
                return val;
            } else {
                double val = Double.POSITIVE_INFINITY;
                for (Node c : kids) {
                    double cv = c.getValue(alpha, beta);
                    val = Math.min(val, cv);
                    beta = Math.min(beta, val);
                    if (alpha >= beta) {
                        break; // prune
                    }
                }
                return val;
            }
        }

        @Override
        public List<Node> getChildren() {
            List<Node> out = new ArrayList<>();
            List<Pair<Double, BattleView>> firstOutcomes =
                firstMove.getPotentialEffects(state, firstCasterIdx, secondCasterIdx);

            for (Pair<Double, BattleView> fo : firstOutcomes) {
                BattleView afterFirst = fo.getSecond();

                List<Pair<Double, BattleView>> secondOutcomes =
                    secondMove.getPotentialEffects(afterFirst, secondCasterIdx, firstCasterIdx);

                for (Pair<Double, BattleView> so : secondOutcomes) {
                    BattleView finalState = so.getSecond();
                    Node nxt = new PostTurnChanceNode(
                        finalState, firstCasterIdx, secondCasterIdx, depth+1
                    );
                    out.add(nxt);
                }
            }
            return out;
        }
    }

    // ------------------------------------------------------------
    // MoveResolutionChanceNode
    // ------------------------------------------------------------
    public class MoveResolutionChanceNode extends Node {
        private final MoveView move;

        public MoveResolutionChanceNode(BattleView state, int casterIdx, int oppIdx, int depth, MoveView move) {
            super(state, casterIdx, oppIdx, depth);
            this.move = move;
        }

        @Override
        public boolean isTerminal() {
            return baseTerminalCheck();
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }

            List<Pair<Double, Node>> branches = getProbabilisticChildren();
            if (branches.isEmpty()) {
                return evaluate(state);
            }
            double total = 0.0;
            for (Pair<Double, Node> b : branches) {
                double prob = b.getFirst();
                double val  = b.getSecond().getValue();
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
            List<Pair<Double, Node>> out = new ArrayList<>();
            PokemonView pkmn = state.getTeamView(casterIdx).getActivePokemonView();
            if (pkmn == null) {
                return out; 
            }

            NonVolatileStatus st = pkmn.getNonVolatileStatus();
            double successChance;
            double confuseChance = 0.0;

            if (st == NonVolatileStatus.SLEEP) {
                successChance = 0.0;
            }
            else if (st == NonVolatileStatus.FREEZE) {
                successChance = 0.0;
            }
            else if (st == NonVolatileStatus.PARALYSIS) {
                successChance = 0.75;
            }
            else if (pkmn.getFlag(Flag.FLINCHED)) {
                successChance = 0.0;
            }
            else {
                successChance = 1.0;
            }

            if (pkmn.getFlag(Flag.CONFUSED)) {
                confuseChance = 0.5;
            }

            double correct = successChance * (1.0 - confuseChance);
            double selfHit = successChance * confuseChance;
            double fail    = 1.0 - successChance;

            // success
            if (correct > 0) {
                for (Pair<Double, BattleView> outcome : move.getPotentialEffects(state, casterIdx, oppIdx)) {
                    Node nxt = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx, depth+1);
                    out.add(new Pair<>(correct * outcome.getFirst(), nxt));
                }
            }
            // confusion => hits self
            if (selfHit > 0) {
                Move selfDamage = new Move(
                    "SelfDamage",
                    Type.NORMAL,
                    Move.Category.PHYSICAL,
                    40,
                    null,
                    Integer.MAX_VALUE,
                    1,
                    0
                ).addCallback(
                    new MultiCallbackCallback(
                        new ResetLastDamageDealtCallback(),
                        new DoDamageCallback(Target.CASTER, false, false, true)
                    )
                );
                for (Pair<Double, BattleView> outcome : selfDamage.getView().getPotentialEffects(state, casterIdx, oppIdx)) {
                    Node nxt = new PostTurnChanceNode(outcome.getSecond(), casterIdx, oppIdx, depth+1);
                    out.add(new Pair<>(selfHit * outcome.getFirst(), nxt));
                }
            }
            // fails
            if (fail > 0) {
                Node nxt = new PostTurnChanceNode(state, casterIdx, oppIdx, depth+1);
                out.add(new Pair<>(fail, nxt));
            }
            return out;
        }
    }

    // PostTurnChanceNode
    public class PostTurnChanceNode extends Node {
        public PostTurnChanceNode(BattleView state, int casterIdx, int oppIdx, int depth) {
            super(state, casterIdx, oppIdx, depth);
        }

        @Override
        public boolean isTerminal() {
            return baseTerminalCheck();
        }

        @Override
        public double getValue() {
            if (isTerminal()) {
                return evaluate(state);
            }
            List<Node> kids = getChildren();
            if (kids.isEmpty()) {
                return evaluate(state);
            }
            // average
            double sum = 0.0;
            for (Node k : kids) {
                sum += k.getValue();
            }
            return sum / kids.size();
        }

        @Override
        public List<Node> getChildren() {
            List<Node> out = new ArrayList<>();
            List<BattleView> postOuts = state.applyPostTurnConditions();
            if (!postOuts.isEmpty()) {
                for (BattleView bv : postOuts) {
                    out.add(new MoveOrderChanceNode(bv, casterIdx, oppIdx, depth+1));
                }
            } else {
                out.add(new MoveOrderChanceNode(state, casterIdx, oppIdx, depth+1));
            }
            return out;
        }
    }

    // Speed adjuster
    protected double adjustedSpeed(PokemonView pokemon) {
        double spd = pokemon.getBaseStat(Stat.SPD);
        if (pokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) {
            spd *= 0.75;
        }
        return spd;
    }
}