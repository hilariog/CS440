package src.pas.tetris.agents;


// SYSTEM IMPORTS
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// JAVA PROJECT IMPORTS
import edu.bu.pas.tetris.agents.QAgent;
import edu.bu.pas.tetris.agents.TrainerAgent.GameCounter;
import edu.bu.pas.tetris.game.Board;
import edu.bu.pas.tetris.game.Game.GameView;
import edu.bu.pas.tetris.game.Block;
import java.util.Arrays;
import edu.bu.pas.tetris.game.minos.Mino;
import edu.bu.pas.tetris.linalg.Matrix;
import edu.bu.pas.tetris.nn.Model;
import edu.bu.pas.tetris.nn.LossFunction;
import edu.bu.pas.tetris.nn.Optimizer;
import edu.bu.pas.tetris.nn.models.Sequential;
import edu.bu.pas.tetris.nn.layers.Dense; // fully connected layer
import edu.bu.pas.tetris.nn.layers.ReLU;  // some activations (below too)
import edu.bu.pas.tetris.nn.layers.Tanh;
import edu.bu.pas.tetris.nn.layers.Sigmoid;
import edu.bu.pas.tetris.training.data.Dataset;
import edu.bu.pas.tetris.utils.Pair;


public class TetrisQAgent
    extends QAgent
{

    public static final double EXPLORATION_PROB = 0.25;
    public static final double EXPLORATION_DECREASE_GAMMA = 0.95;
    public double currentExplorationProb = EXPLORATION_PROB;
    private long lastCycleIdx = -1;      // Remember last cycle idx so we only decay once per cycle:

    private Random random;

    public TetrisQAgent(String name)
    {
        super(name);
        this.random = new Random(12345); // optional to have a seed
    }

    public Random getRandom() { return this.random; }

    @Override
    public Model initQFunction()
    {
        // System.out.println("initQFunction called!");
        // build a single-hidden-layer feedforward network
        // this example will create a 3-layer neural network (1 hidden layer)
        // in this example, the input to the neural network is the
        // image of the board unrolled into a giant vector
        final int hiddenDimOne = 64;
        final int hiddenDimTwo = 128;
        final int hiddenDimThree = 64;
        final int hiddenDimFour = 32;
        final int outDim = 1;

        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(11, hiddenDimOne));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(hiddenDimOne, hiddenDimTwo));
        qFunction.add(new Tanh());
        qFunction.add(new Dense(hiddenDimTwo, hiddenDimThree));
        qFunction.add(new Sigmoid());
        qFunction.add(new Dense(hiddenDimThree, hiddenDimFour));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(hiddenDimFour, outDim));

        // // return qFunction;
        // final int hiddenDimOne = 64;
        // final int hiddenDimTwo = 32;
        // final int outDim = 1;

        // Sequential qFunction = new Sequential();
        // qFunction.add(new Dense(11, hiddenDimOne));
        // qFunction.add(new ReLU());
        // qFunction.add(new Dense(hiddenDimOne, hiddenDimTwo));
        // qFunction.add(new Tanh());
        // qFunction.add(new Dense(hiddenDimTwo, outDim));

        return qFunction;
    }

    /**
        This function is for you to figure out what your features
        are. This should end up being a single row-vector, and the
        dimensions should be what your qfunction is expecting.
        One thing we can do is get the grayscale image
        where squares in the image are 0.0 if unoccupied, 0.5 if
        there is a "background" square (i.e. that square is occupied
        but it is not the current piece being placed), and 1.0 for
        any squares that the current piece is being considered for.
        
        We can then flatten this image to get a row-vector, but we
        can do more than this! Try to be creative: how can you measure the
        "state" of the game without relying on the pixels? If you were given
        a tetris game midway through play, what properties would you look for?
     */
    @Override
    public Matrix getQFunctionInput(final GameView game,
                                    final Mino potentialAction)
    {
        int numQEntries = 11;
        Matrix qInput = Matrix.zeros(numQEntries, 1);

         // 0 score values
        qInput.set(0, 0, game.getTotalScore() / 1000.0);

        // grab and orient the image
        Matrix oriented;
        try {
            oriented = game.getGrayscaleImage(potentialAction);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
            return qInput; // unreachable
        }
        int numRows = oriented.getShape().getNumRows();
        int numCols = oriented.getShape().getNumCols();

        int[] heights = new int[numCols];
        int totalHoles = 0;
        for (int c = 0; c < numCols; c++) {
            boolean seenBlock = false;
            for (int r = 0; r < numRows; r++) {
                if (oriented.get(r, c) >= 0.5) {
                    if (!seenBlock) {
                        // first block in this column
                        heights[c] = numRows - r;
                        seenBlock = true;
                    }
                } else if (seenBlock) {
                    // any empty after first block is a hole
                    totalHoles++;
                }
            }
        }

        int bump = 0;
        for (int c = 0; c < numCols - 1; c++) {
            bump += Math.abs(heights[c] - heights[c + 1]);
        }
        double bumpNorm = bump / ((numCols - 1) * (double) numRows);
        double holeNorm = totalHoles / (double) (numRows * numCols);

        // 1: bumpiness
        qInput.set(1, 0, bumpNorm);

        // 2: holes
        qInput.set(2, 0, holeNorm);

        // 3: maximum height
        int maxHeight = 0;
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                if (oriented.get(i, j) >= 0.5) {
                    maxHeight = i;
                    break;
                }
            }
        }
        qInput.set(3, 0, maxHeight / (double) numRows);

        int filledRowCount = 0, consecutiveFilled = 0, maxConsecutiveFilled = 0;
        for (int i = 0; i < numRows; i++) {
            boolean allFilled = true;
            for (int j = 0; j < numCols; j++) {
                if (oriented.get(i, j) < 0.5) {
                    allFilled = false;
                    break;
                }
            }
            if (allFilled) {
                filledRowCount++;
                consecutiveFilled++;
                maxConsecutiveFilled = Math.max(maxConsecutiveFilled, consecutiveFilled);
            } else {
                consecutiveFilled = 0;
            }
        }
        // 4, 5, 6: filled rows, tetris, full clear
        qInput.set(4, 0, filledRowCount / (double) numRows);            
        qInput.set(5, 0, maxConsecutiveFilled >= 4 ? 1.0 : 0.0);        
        qInput.set(6, 0, filledRowCount == maxHeight ? 1.0 : 0.0);      

        // 7: did the agent lost?
        qInput.set(7, 0, game.didAgentLose() ? 1.0 : 0.0);

        // next three Mino types
        List<Mino.MinoType> nextTypes = game.getNextThreeMinoTypes();
        int numTypes = Mino.MinoType.values().length;
        for (int i = 0; i < 3; i++) {
            double val = (i < nextTypes.size())
                ? nextTypes.get(i).ordinal()
                : 0.0;
            qInput.set(8 + i, 0, (val/(numTypes - 1.0)));
        }

        return qInput.transpose();
    }



    /**
     * This method is used to decide if we should follow our current policy
     * (i.e. our q-function), or if we should ignore it and take a random action
     * (i.e. explore).
     *
     * Remember, as the q-function learns, it will start to predict the same "good" actions
     * over and over again. This can prevent us from discovering new, potentially even
     * better states, which we want to do! So, sometimes we should ignore our policy
     * and explore to gain novel experiences.
     *
     * The current implementation chooses to ignore the current policy around 5% of the time.
     * While this strategy is easy to implement, it often doesn't perform well and is
     * really sensitive to the EXPLORATION_PROB. I would recommend devising your own
     * strategy here.
     */
    @Override
    public boolean shouldExplore(final GameView game,
                                 final GameCounter gameCounter)
    {
        long cycleIdx = gameCounter.getCurrentCycleIdx();

        // only decay epsilon when we enter a new cycle:
        if (cycleIdx != lastCycleIdx) {
            currentExplorationProb *= EXPLORATION_DECREASE_GAMMA;
            lastCycleIdx = cycleIdx;
        }

        // now do the usual epsilon–greedy check
        return this.getRandom().nextDouble() <= currentExplorationProb;

    }

    /**
     * This method is a counterpart to the "shouldExplore" method. Whenever we decide
     * that we should ignore our policy, we now have to actually choose an action.
     *
     * You should come up with a way of choosing an action so that the model gets
     * to experience something new. The current implemention just chooses a random
     * option, which in practice doesn't work as well as a more guided strategy.
     * I would recommend devising your own strategy here.
     */
    @Override
    public Mino getExplorationMove(final GameView game)
    {
        int randIdx = this.getRandom().nextInt(game.getFinalMinoPositions().size());
        return game.getFinalMinoPositions().get(randIdx);
    }

    /**
     * This method is called by the TrainerAgent after we have played enough training games.
     * In between the training section and the evaluation section of a cycle, we need to use
     * the exprience we've collected (from the training games) to improve the q-function.
     *
     * You don't really need to change this method unless you want to. All that happens
     * is that we will use the experiences currently stored in the replay buffer to update
     * our model. Updates (i.e. gradient descent updates) will be applied per minibatch
     * (i.e. a subset of the entire dataset) rather than in a vanilla gradient descent manner
     * (i.e. all at once)...this often works better and is an active area of research.
     *
     * Each pass through the data is called an epoch, and we will perform "numUpdates" amount
     * of epochs in between the training and eval sections of each cycle.
     */
    @Override
    public void trainQFunction(Dataset dataset,
                               LossFunction lossFunction,
                               Optimizer optimizer,
                               long numUpdates)
    {
        for(int epochIdx = 0; epochIdx < numUpdates; ++epochIdx)
        {
            dataset.shuffle();
            Iterator<Pair<Matrix, Matrix> > batchIterator = dataset.iterator();

            while(batchIterator.hasNext())
            {
                Pair<Matrix, Matrix> batch = batchIterator.next();

                try
                {
                    Matrix YHat = this.getQFunction().forward(batch.getFirst());

                    optimizer.reset();
                    this.getQFunction().backwards(batch.getFirst(),
                                                  lossFunction.backwards(YHat, batch.getSecond()));
                    optimizer.step();
                } catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * This method is where you will devise your own reward signal. Remember, the larger
     * the number, the more "pleasurable" it is to the model, and the smaller the number,
     * the more "painful" to the model.
     *
     * This is where you get to tell the model how "good" or "bad" the game is.
     * Since you earn points in this game, the reward should probably be influenced by the
     * points, however this is not all. In fact, just using the points earned this turn
     * is a **terrible** reward function, because earning points is hard!!
     *
     * I would recommend you to consider other ways of measuring "good"ness and "bad"ness
     * of the game. For instance, the higher the stack of minos gets....generally the worse
     * (unless you have a long hole waiting for an I-block). When you design a reward
     * signal that is less sparse, you should see your model optimize this reward over time.
     */

    // keep a running record of last column‐heights so we can detect lines cleared & bumpiness change
    private int[] lastHeights = null;

    @Override
    public double getReward(final GameView game)
    {
        // 1) immediate loss penalty
        if (game.didAgentLose()) {
            return -10.0;
        }

        // 2) base reward = raw points + tiny survival bonus
        double reward = game.getScoreThisTurn() + 0.1;

        // 3) grab board
        Board board = game.getBoard();
        Block[][] grid = board.getBoard();
        int numRows = grid.length, numCols = grid[0].length;

        // 4) ONE pass to compute:
        //    - heights[c]
        //    - currSum of heights
        //    - maxHeight
        //    - hole count
        int[] heights = new int[numCols];
        int currSum    = 0;
        int maxHeight  = 0;
        int holes      = 0;

        for (int c = 0; c < numCols; c++) {
            boolean seenBlock = false;
            for (int r = 0; r < numRows; r++) {
                if (grid[r][c] != null) {
                    if (!seenBlock) {
                        // first block in this column
                        int h = numRows - r;
                        heights[c] = h;
                        currSum   += h;
                        if (h > maxHeight) maxHeight = h;
                        seenBlock = true;
                    }
                    // once seenBlock is true, we skip further filled-cell logic
                } else if (seenBlock) {
                    // every empty cell below the first block is a “hole”
                    holes++;
                }
            }
        }

        // 4.1) heightFactor bonus
        double avgHeight   = (double)currSum / numCols;
        double heightFactor = ((double)numRows - avgHeight) / numRows;
        reward += 0.05 * heightFactor;

        // 5 & 8) single pass over lastHeights to do line‐clear bonus and bumpiness smoothing
        if (lastHeights != null) {
            int prevSum   = 0;
            int prevBump  = 0;
            int currBump  = 0;
            for (int c = 0; c < numCols; c++) {
                prevSum += lastHeights[c];
                if (c < numCols - 1) {
                    prevBump += Math.abs(lastHeights[c] - lastHeights[c+1]);
                    currBump += Math.abs(heights[c]     - heights[c+1]);
                }
            }

            int rowsCleared = prevSum - currSum;
            if (rowsCleared >= 4) {
                reward += 10.0;             // Tetris
            } else if (rowsCleared > 0) {
                reward += 1.0 * rowsCleared;
            }

            if (currBump < prevBump) {
                reward += 0.2 * (prevBump - currBump);
            }
        }

        // 6) penalize max height & buried holes
        reward -= 0.1 * maxHeight;
        reward -= 0.15 * holes;

        // 7) perfect-clear bonus
        if (board.isClear()) {
            reward += 15.0;
        }

        // 10) roll forward
        lastHeights = heights;
        return reward;
    }
    

}
