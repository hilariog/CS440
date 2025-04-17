package src.labs.cp;


// SYSTEM IMPORTS
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Queue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;


// JAVA PROJECT IMPORTS
import edu.bu.cp.game.Game;
import edu.bu.cp.linalg.Matrix;
import edu.bu.cp.nn.LossFunction;
import edu.bu.cp.nn.Model;
import edu.bu.cp.nn.Optimizer;
import edu.bu.cp.nn.Parameter;
import edu.bu.cp.nn.layers.*;
import edu.bu.cp.nn.losses.MeanSquaredError;
import edu.bu.cp.nn.models.Sequential;
import edu.bu.cp.nn.optimizers.*;
import edu.bu.cp.utils.Triple;
import edu.bu.cp.utils.Pair;


import src.labs.cp.ReplayBuffer;
import src.labs.cp.Dataset;


public class Main
    extends Object
{
    public static final long SEED = 12345;
    private static double EPSILON        = 1.0;   // start fully exploratory
    private static final double EPS_MIN  = 0.05;  // floor
    private static final double EPS_DECAY = 0.990; // multiplicative decay per episode

    // a tiny 1-hidden-layer network. This will work
    public static Model initQFunction()
    {
        Sequential m = new Sequential();

        // input is 4d
        m.add(new Dense(4, 36));
        m.add(new Sigmoid());

        // since the number of actions in this world is fixed, we can ask the network to predict
        // one q-value per (fixed ahead of time) actions. In this world there are two actions.
        m.add(new Dense(36, 2));

        return m;
    }


    public static int argmax(Matrix qValues) throws IndexOutOfBoundsException
    {
        // find the best action according to the q-function
        // small subtlety is that in the unexpected case where our neural network starts spitting
        // out NaNs or Infs or something we won't get an answer if we start off maxVal at -inf
        // so instead I like to start it off at null, just to guarantee that the first element gets picked
        // regardless of q-value.

        // This is not the only way to do this

        Double maxVal = null;
        int action = -1;

        for(int colIdx = 0; colIdx < qValues.getShape().getNumCols(); ++colIdx)
        {
            double qVal = qValues.get(0, colIdx);
            if(maxVal == null || qVal > maxVal)
            {
                maxVal = qVal;
                action = colIdx;
            }
        }
        return action;
    }


    public static void train(Game game,         // world
                             Model qFunction,   // neural network
                             ReplayBuffer rb,   // replay buffer (to populate)
                             Namespace ns)      // namespace of command line arguments
    {
            // TODO: complete me!
            // play a bunch of training games where you are not allowed to update the neural network
            // make sure to add transitions that you observe to the replay buffer, including the terminal transition!
        

        long   numTrainingGames = ns.get("numTrainingGames");
        Random rng              = game.getRandom();      // keep RNG seed‑controlled

        for (long ep = 0; ep < numTrainingGames; ++ep)
        {
            /* ---- 1.  Start episode ---- */
            Matrix state = game.reset();
            boolean done = false;

            while (!done)
            {
                /* ---- 2.  ε‑greedy action ---- */
                int action;
                if (rng.nextDouble() < EPSILON) {
                    action = rng.nextInt(2);             // explore
                } else {
                    try {
                        action = argmax(qFunction.forward(state)); // exploit
                    } catch (Exception e) {
                        System.err.println("Main.train: q‑function forward error");
                        e.printStackTrace();
                        action = rng.nextInt(2);          // fall back to random
                    }
                }

                /* ---- 3.  Environment step ---- */
                Triple<Matrix, Double, Boolean> obs = game.step(action);
                Matrix nextState = obs.getFirst();       // may be null on terminal
                double reward    = obs.getSecond();
                done             = obs.getThird();

                /* ---- 4.  Store transition ---- */
                if (done)
                    rb.addSample(state, reward, null);   // terminal transition
                else
                    rb.addSample(state, reward, nextState);

                state = nextState;                       // advance state
            }

            /* ---- 5.  Decay ε after each episode ---- */
            if (EPSILON > EPS_MIN)
                EPSILON = Math.max(EPSILON * EPS_DECAY, EPS_MIN);
        }

    }

    public static void update(Model qFunction,      // neural network
                              Optimizer opt,        // SGD or Adam in this implementation
                              LossFunction lf,      // loss function (mean squared error)
                              ReplayBuffer rb,      // replay buffer
                              Random rng,           // random number generator
                              Namespace ns)         // namespace of command line args
    {
        double gamma = ns.get("gamma");
        int batchSize = ns.get("miniBatchSize");
        int numUpdates = ns.get("numUpdates");

        // make supervised learning dataset from our replay buffer
        Pair<Matrix, Matrix> trainingData = rb.getTrainingData(qFunction, gamma);

        Matrix X = trainingData.getFirst();
        Matrix YGt = trainingData.getSecond();

        // make Dataset object to handle minibatching
        Dataset ds = new Dataset(X, YGt, batchSize, rng);

        for(int epochIdx = 0; epochIdx < numUpdates; ++epochIdx)
        {

            // shuffle the training data to prevent the network from thinking some examples belong by others
            ds.shuffle();

            // iterate over the batches
            Dataset.BatchIterator it = ds.iterator();
            while(it.hasNext())
            {

                // gimme a batch
                Pair<Matrix, Matrix> batch = it.next();

                try
                {
                    // get predictions
                    Matrix YHat = qFunction.forward(batch.getFirst());

                    // a pytorch-esque gradient descent update api
                    opt.reset();
                    qFunction.backwards(batch.getFirst(),
                                        lf.backwards(YHat, batch.getSecond()));
                    opt.step();
                } catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }            
            }
        }

    }

    public static Pair<Double, Double> test(Game game, Model qFunction, Namespace ns)
    {
        long numEvalGames = ns.get("numEvalGames");
        double gamma = ns.get("gamma");

        double numGames = 0;
        double trajectoryUtilitySum = 0;

        double gameLengthSum = 0;

        for(int gameIdx = 0; gameIdx < numEvalGames; ++gameIdx)
        {
            // populate with the sum of discounted rewards
            double trajectoryUtility = 0;

            // reset the game and the variables we want to aggregate information with
            Matrix state = game.reset();
            double reward = 0;
            int action = 0;

            boolean isDone = false;
            int t = 0;
            while(!isDone)
            {
                // ask the q function to rank each action and then listen to it (i.e. argmax for policy)
                try
                {
                    Matrix qValues = qFunction.forward(state);
                    action = argmax(qValues);
                } catch(Exception e)
                {
                    System.err.println("Main.main: error caught using qFunction");
                    e.printStackTrace();
                }

                // step the game
                Triple<Matrix, Double, Boolean> obs = game.step(action);

                // update variables
                state = obs.getFirst();
                reward = obs.getSecond();
                isDone = obs.getThird();

                // update this trajectory's utility
                trajectoryUtility += Math.pow(gamma, t) * reward;
                t += 1;

            }

            // update numerator for avg trajectory utility
            trajectoryUtilitySum += trajectoryUtility;
            gameLengthSum += t;
            numGames += 1;
        }

        return new Pair<Double, Double>(trajectoryUtilitySum / numGames, gameLengthSum / numGames);
    }


    public static void main(String[] args)
    {

        ArgumentParser parser = ArgumentParsers.newFor("Main").build()
            .defaultHelp(true)
            .description("Play openai-gym Deterministic Mountain Car in Java");

        // training/eval phase config
        parser.addArgument("-p", "--numCycles")
            .type(Long.class)
            .setDefault(1l)
            .help("the number of times the training/testing cycle is repeated");
        parser.addArgument("-t", "--numTrainingGames")
            .type(Long.class)
            .setDefault(10l)
            .help("the number of training games to collect training data from before an evaluation phase");
        parser.addArgument("-v", "--numEvalGames")
            .type(Long.class)
            .setDefault(5l)
            .help("the number of evaluation games to play while fixing the agent " +
                  "(the agent can't learn from these games)");

        // replay buffer config
        parser.addArgument("-b", "--maxBufferSize")
            .type(Integer.class)
            .setDefault(1280)
            .help("The max number of samples to store in the replay buffer if using the TrainerAgent.");

        parser.addArgument("-r", "--replacementType")
            .type(ReplayBuffer.ReplacementType.class)
            .setDefault(ReplayBuffer.ReplacementType.RANDOM)
            .help("replay buffer replacement type for when a new sample is added to a full buffer");

        // neural network training hyperparams
        parser.addArgument("-u", "--numUpdates")
            .type(Integer.class)
            .setDefault(1)
            .help("the number of epochs to train for after each training phase if using the TrainerAgent.");
        parser.addArgument("-m", "--miniBatchSize")
            .type(Integer.class)
            .setDefault(128)
            .help("batch size to use when performing an epoch of training if using the TrainerAgent.");
        parser.addArgument("-n", "--lr")
            .type(Double.class)
            .setDefault(1e-6)
            .help("the learning rate to use if using the TrainerAgent.");
        parser.addArgument("-c", "--clip")
            .type(Double.class)
            .setDefault(100d)
            .help("gradient clip value to use (symmetric) if using the TrainerAgent.");
        parser.addArgument("-d", "--optimizerType")
            .type(String.class)
            .setDefault("sgd")
            .help("type of optimizer to use if using the TrainerAgent");
        parser.addArgument("-b1", "--beta1")
            .type(Double.class)
            .setDefault(0.9)
            .help("beta1 value for adam optimizer");
        parser.addArgument("-b2", "--beta2")
            .type(Double.class)
            .setDefault(0.999)
            .help("beta2 value for adam optimizer");

        // RL hyperparams
        parser.addArgument("-g", "--gamma")
            .type(Double.class)
            .setDefault(1e-4)
            .help("discount factor for the Bellman equation if using the TrainerAgent.");

        // model saving/loading config
        parser.addArgument("-i", "--inFile")
            .type(String.class)
            .setDefault("")
            .help("params file to load");
        parser.addArgument("-o", "--outFile")
            .type(String.class)
            .setDefault("./params/qFunction")
            .help("where to save the model to (will append XX.model where XX is the number of training/eval " +
                  "cycles performed if using the TrainerAgent.");
        parser.addArgument("--outOffset")
            .type(Long.class)
            .setDefault(0l)
            .help("offset to XX value appended to end of --outFile arg. Useful if you want to resume training from " +
                  "a previous training point and don't want to overwrite any subsequent files. (XX + offset) will " +
                  "be used instead of (XX) when appending to the --outFile arg. Only used if using the TrainerAgent.");

        // miscellaneous config
        parser.addArgument("--seed")
                .type(Long.class)
                .setDefault(SEED)
                .help("random seed to make successive runs repeatable. If -1l, no seed is used");
        

        Namespace ns = parser.parseArgsOrFail(args);

        long numCycles = ns.get("numCycles");
        long numTrainingGames = ns.get("numTrainingGames");
        long numEvalGames = ns.get("numEvalGames");

        long seed = ns.get("seed");

        String checkpointFileBase = ns.get("outFile");
        long offset = ns.get("outOffset");

        Random rng = new Random(seed);
        Game game = new Game(rng);
        Model qFunction = initQFunction();

        // feel free to change the optimizer if you want
        // but if you change it to Adam be sure to include the command line arguments for Adam!
        Optimizer opt = new SGDOptimizer(qFunction.getParameters(), ns.get("lr"));
        LossFunction lf = new MeanSquaredError();

        ReplayBuffer rb = new ReplayBuffer(ns.get("replacementType"), ns.get("maxBufferSize"), 4, rng);

        for(int cycleIdx = 0; cycleIdx < numCycles; ++cycleIdx)
        {
            // play a bunch of training games to populate the replay buffer
            train(game, qFunction, rb, ns);

            // update the model by converting the replay buffer into a supervised learning dataset and doing gd
            update(qFunction, opt, lf, rb, rng, ns);

            // save the model
            qFunction.save(checkpointFileBase + (cycleIdx + offset) + ".model");

            // evaluate the model
            Pair<Double, Double> expectedUtilityAndAvgGameLength = test(game, qFunction, ns);
            double avgUtil = expectedUtilityAndAvgGameLength.getFirst();
            double avgGameLength = expectedUtilityAndAvgGameLength.getSecond();

            System.out.println("after cycle=" + cycleIdx + " avg(utility)=" + avgUtil + " avg(game_length)=" + avgGameLength);
        }
    }
}
