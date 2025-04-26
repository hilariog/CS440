package src.labs.rl.maze.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS
import edu.bu.labs.rl.maze.agents.StochasticAgent;
import edu.bu.labs.rl.maze.agents.StochasticAgent.RewardFunction;
import edu.bu.labs.rl.maze.agents.StochasticAgent.TransitionModel;
import edu.bu.labs.rl.maze.utilities.Coordinate;
import edu.bu.labs.rl.maze.utilities.Pair;



public class ValueIterationAgent
    extends StochasticAgent
{

    public static final double GAMMA = 0.1; // feel free to change this around!
    public static final double EPSILON = 1e-6; // don't change this though

    private Map<Coordinate, Double> utilities;

	public ValueIterationAgent(int playerNum)
	{
		super(playerNum);
        this.utilities = null;
	}

    public Map<Coordinate, Double> getUtilities() { return this.utilities; }
    private void setUtilities(Map<Coordinate, Double> u) { this.utilities = u; }

    public boolean isTerminalState(Coordinate c)
    {
        return c.equals(StochasticAgent.POSITIVE_TERMINAL_STATE)
            || c.equals(StochasticAgent.NEGATIVE_TERMINAL_STATE);
    }

    public Map<Coordinate, Double> getZeroMap(StateView state)
    {
        Map<Coordinate, Double> m = new HashMap<Coordinate, Double>();
        for(int x = 0; x < state.getXExtent(); ++x)
        {
            for(int y = 0; y < state.getYExtent(); ++y)
            {
                if(!state.isResourceAt(x, y))
                {
                    // we can go here
                    m.put(new Coordinate(x, y), 0.0);
                }
            }
        }
        return m;
    }

    public void valueIteration(StateView state) {
        // Initialize U and U' to zero for all reachable coordinates
        Map<Coordinate, Double> U      = getZeroMap(state);
        Map<Coordinate, Double> Uprime = new HashMap<>(U);

        // Convergence threshold δ ≤ ε (1−γ)/γ
        double threshold = EPSILON * (1 - GAMMA) / GAMMA;
        double delta;

        do {
            // Copy U' into U for this iteration
            U = new HashMap<>(Uprime);
            delta = 0.0;

            // For each state s
            for (Coordinate s : U.keySet()) {
                // Terminal states: utility = their reward
                if (isTerminalState(s)) {
                    Uprime.put(s, RewardFunction.getReward(s));
                } else {
                    // Non-terminal: bellman update
                    double r = RewardFunction.getReward(s);
                    double maxActionValue = Double.NEGATIVE_INFINITY;

                    // For each action a (cardinal direction)
                    for (Direction a : TransitionModel.CARDINAL_DIRECTIONS) {
                        double sum = 0.0;
                        // Sum over next states s'
                        for (Pair<Coordinate, Double> trans 
                                : TransitionModel.getTransitionProbs(state, s, a)) {
                            sum += trans.getSecond() * U.get(trans.getFirst());
                        }
                        if (sum > maxActionValue) {
                            maxActionValue = sum;
                        }
                    }

                    // New utility for s
                    double newU = r + GAMMA * maxActionValue;
                    Uprime.put(s, newU);

                    // Track the largest change
                    delta = Math.max(delta, Math.abs(newU - U.get(s)));
                }
            }
        } while (delta > threshold);

        // Save the computed utilities
        setUtilities(Uprime);
    }

    @Override
    public void computePolicy(StateView state,
                              HistoryView history)
    {
        this.valueIteration(state);

        // compute the policy from the utilities
        Map<Coordinate, Direction> policy = new HashMap<Coordinate, Direction>();

        for(Coordinate c : this.getUtilities().keySet())
        {
            // figure out what to do when in this state
            double maxActionUtility = Double.NEGATIVE_INFINITY;
            Direction bestDirection = null;

            for(Direction d : TransitionModel.CARDINAL_DIRECTIONS)
            {
                double thisActionUtility = 0.0;
                for(Pair<Coordinate, Double> transition : TransitionModel.getTransitionProbs(state, c, d))
                {
                    thisActionUtility += transition.getSecond() * this.getUtilities().get(transition.getFirst());
                }

                if(thisActionUtility > maxActionUtility)
                {
                    maxActionUtility = thisActionUtility;
                    bestDirection = d;
                }
            }

            policy.put(c, bestDirection);

        }

        this.setPolicy(policy);
    }

}
