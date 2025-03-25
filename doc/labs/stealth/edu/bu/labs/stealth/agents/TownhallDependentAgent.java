package edu.bu.labs.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;


import java.io.InputStream;
import java.io.OutputStream;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS



public class TownhallDependentAgent
    extends Agent
{

    private static final int NUM_TURNS_WITHOUT_TOWNHALL_BEFORE_DEATH_DEFAULT = 30;

    private int numTurnsWithoutTownhallBeforeDeath;
    private int friendlyTownhallUnitID;
    private Set<Integer> otherFriendlyUnitIDs;

    public TownhallDependentAgent(int playerNum, String[] args)
    {
        super(playerNum);

        System.out.println("TownhallDependentAgent(int, String[])");

        /****/
        if(args.length < 2)
        {
            this.numTurnsWithoutTownhallBeforeDeath = TownhallDependentAgent.NUM_TURNS_WITHOUT_TOWNHALL_BEFORE_DEATH_DEFAULT;
        } else
        {
            try
            {
                this.numTurnsWithoutTownhallBeforeDeath = Integer.parseInt(args[1]);

                if(this.numTurnsWithoutTownhallBeforeDeath < 0)
                {
                    throw new Exception("ERROR: 2nd arg must be positive!");
                }
            } catch(Exception e)
            {
                System.err.println("ERROR: 2nd arg must be a positive int");
                System.exit(-1);
            }
        }
        /****/
        // this.numTurnsWithoutTownhallBeforeDeath = TownhallDependentAgent.NUM_TURNS_WITHOUT_TOWNHALL_BEFORE_DEATH_DEFAULT;

        this.friendlyTownhallUnitID = -1;
        this.otherFriendlyUnitIDs = new HashSet<Integer>();
    }

    public int getNumTurnsWithoutTownhallBeforeDeath() { return this.numTurnsWithoutTownhallBeforeDeath; }
    public int getFriendlyTownhallUnitID() { return this.friendlyTownhallUnitID; }
    public final Set<Integer> getOtherFriendlyUnitIDs() { return this.otherFriendlyUnitIDs; }

    private void setFriendlyTownhallUnitID(int unitID) { this.friendlyTownhallUnitID = unitID; }
    private void setNumTurnsWithoutTownhallBeforeDeath(int n) { this.numTurnsWithoutTownhallBeforeDeath = n; }

    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // detect friendly townhall id
        Set<Integer> friendlyTownhallUnitIDs = new HashSet<Integer>();
        for(Integer unitID : state.getUnitIds(this.getPlayerNumber()))
        {
            if(state.getUnit(unitID).getTemplateView().getName().toLowerCase().equals("townhall"))
            {
                friendlyTownhallUnitIDs.add(unitID);
            } else
            {
                this.getOtherFriendlyUnitIDs().add(unitID);
            }
        }

        if(friendlyTownhallUnitIDs.size() != 1)
        {
            System.err.println("ERROR: should only have 1 friendly townhall on my team but found "
                + friendlyTownhallUnitIDs.size());
            System.exit(-1);
        }

        this.setFriendlyTownhallUnitID(friendlyTownhallUnitIDs.iterator().next());
        return actions;
    }

    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        UnitView friendlyTownhallUnitView = state.getUnit(this.getFriendlyTownhallUnitID());
        if(friendlyTownhallUnitView == null)
        {
            if(this.getFriendlyTownhallUnitID() > 0)
            {
                this.setNumTurnsWithoutTownhallBeforeDeath(this.getNumTurnsWithoutTownhallBeforeDeath() - 1);
            }

            if(this.getNumTurnsWithoutTownhallBeforeDeath() <= 0)
            {
                // tell everyone to kill themselves
                for(Integer unitID : this.getOtherFriendlyUnitIDs())
                {
                    if(state.getUnit(unitID) != null)
                    {
                        actions.put(unitID, Action.createPrimitiveAttack(unitID, unitID));
                    }
                }
            }
        }

        return actions;
    }

    @Override
    public void terminalStep(StateView state,
                             HistoryView history) {}

    @Override
    public void savePlayerData(OutputStream os) {}

    @Override
    public void loadPlayerData(InputStream is) {}

}

