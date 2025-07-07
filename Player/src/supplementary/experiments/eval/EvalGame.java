package supplementary.experiments.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import main.CommandLineArgParse;
import main.CommandLineArgParse.ArgOption;
import main.CommandLineArgParse.OptionTypes;
import main.StringRoutines;
import main.UnixPrintWriter;
import metrics.Metric;
import metrics.single.duration.DurationMoves;
import metrics.single.duration.DurationTurns;
import metrics.single.duration.DurationTurnsNotTimeouts;
import metrics.single.duration.DurationTurnsStdDev;
import metrics.single.outcome.AdvantageP1;
import metrics.single.outcome.Balance;
import metrics.single.outcome.Completion;
import metrics.single.outcome.Drawishness;
import metrics.single.outcome.OutcomeUniformity;
import metrics.single.outcome.Timeouts;
import other.AI;
import other.GameLoader;
import other.context.Context;
import other.trial.Trial;
import utils.AIFactory;

/**
 * Code to evaluate a single game (in terms of a few basic measures of quality).
 * 
 * @author Dennis Soemers
 */
public class EvalGame 
{
	
	/** Number of seconds of warming up (per game) */
	private int warmingUpSecs;
	
	/** Number of trials to run */
	private int numTrials;
	
	/** Maximum number of moves per game */
	private int moveLimit;
	
	/** Max allowed thinking time per move (in seconds) */
	protected double thinkingTime;
	
	/** Game name to use */
	private String gameName = null;
	
	/** List of options to use for compiling game */
	private List<String> options = null;
	
	/** List of strings describing the AIs to use */
	private List<String> aiStrings = null;

	/** The name of the csv to export with the results. */
	private String exportCSV;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 */
	private EvalGame()
	{
		// Nothing to do here
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Start the experiment
	 */
	@SuppressWarnings("unchecked")
	public void startExperiment()
	{
		final List<String> results = new ArrayList<String>();
		
		final List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(new DurationMoves());
		metrics.add(new DurationTurns());
		metrics.add(new DurationTurnsStdDev());
		metrics.add(new DurationTurnsNotTimeouts());
		metrics.add(new AdvantageP1());
		metrics.add(new Balance());
		metrics.add(new Completion());
		metrics.add(new Drawishness());
		metrics.add(new Timeouts());
		metrics.add(new OutcomeUniformity());

		// TODO add header line to results
		
		final Game game = GameLoader.loadGameFromName(gameName, options);
		game.setMaxMoveLimit(moveLimit);
		
		if (aiStrings.size() != game.players().count())
		{
			System.err.println("Game has " + game.players().count() + " players, but only " + aiStrings.size() + " AI strings were provided.");
			return;
		}
		
		final List<AI> aiPlayers = new ArrayList<AI>();
		aiPlayers.add(null);
		for (int i = 0; i < aiStrings.size(); i++)
		{
			aiPlayers.add(AIFactory.createAI(aiStrings.get(i)));
		}
		
		// Warming up
		final Trial trial = new Trial(game);
		final Context context = new Context(game, trial);

		long stopAt = 0L;
		long start = System.nanoTime();
		double abortAt = start + warmingUpSecs * 1000000000.0;
		while (stopAt < abortAt)
		{
			game.start(context);
			game.playout(context, null, 1.0, null, -1, moveLimit, ThreadLocalRandom.current());
			stopAt = System.nanoTime();
		}

		System.gc();
		
		// TODO should have rotating between AIs for colour switching, but too
		// lazy to implement it right now as I won't need it for the quick
		// experiment I want to run

		final List<Trial> storedTrials = new ArrayList<Trial>(numTrials);
		
		try
		{
			for (int trialIdx = 0; trialIdx < numTrials; ++trialIdx)
			{
				game.start(context);
				for (int p = 1; p <= game.players().count(); ++p)
					aiPlayers.get(p).initAI(game, p);
				
				while (!context.trial().over())
				{
					context.model().startNewStep
					(
						context, 
						aiPlayers, 
						thinkingTime, 
						-1, -1, 0.0,
						true, // block call until it returns
						false, false
					);
					
					while (!context.model().isReady())
						Thread.sleep(100L);
				}
				
				storedTrials.add(new Trial(context.trial()));
			}
		}
		catch (final InterruptedException e)
		{
			e.printStackTrace();
		}
		
		// TODO Save the output data in results list

		try (final PrintWriter writer = new UnixPrintWriter(new File(exportCSV), "UTF-8"))
		{
			for (final String toWrite : results)
				writer.println(StringRoutines.join(",", toWrite));
		}
		catch (final FileNotFoundException | UnsupportedEncodingException e)
		{
			e.printStackTrace();
		}
	}

	//-------------------------------------------------------------------------
	
	/**
	 * Main method
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public static void main(final String[] args)
	{		
		// define options for arg parser
		final CommandLineArgParse argParse = 
				new CommandLineArgParse
				(
					true,
					"Evaluate a game based on simple metrics (balance, duration)."
				);
		
		argParse.addOption(new ArgOption()
				.withNames("--warming-up-secs", "--warming-up")
				.help("Number of seconds of warming up (per game).")
				.withDefault(Integer.valueOf(30))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--num-trials")
				.help("Number of trials to play.")
				.withDefault(Integer.valueOf(150))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--move-limit")
				.help("Maximum number of moves per trial.")
				.withDefault(Integer.valueOf(-1))
				.withNumVals(1)
				.withType(OptionTypes.Int));
		argParse.addOption(new ArgOption()
				.withNames("--game-name")
				.help("Name of the game to be evaluated.")
				.withNumVals("1")
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--game-options")
				.help("Game Options to load.")
				.withDefault(new ArrayList<String>(0))
				.withNumVals("*")
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--ai-strings")
				.help("String descriptions for AIs.")
				.withDefault(new ArrayList<String>(0))
				.withNumVals("+")
				.withType(OptionTypes.String));
		argParse.addOption(new ArgOption()
				.withNames("--thinking-time")
				.help("Max allowed thinking time per move (in seconds).")
				.withDefault(Double.valueOf(1.0))
				.withNumVals(1)
				.withType(OptionTypes.Double));
		argParse.addOption(new ArgOption()
				.withNames("--export-csv")
				.help("Filename (or filepath) to write results to. By default writes to ./results.csv")
				.withDefault("results.csv")
				.withNumVals(1)
				.withType(OptionTypes.String));
		
		// Parse the args
		if (!argParse.parseArguments(args))
			return;
		
		// use the parsed args
		final EvalGame experiment = new EvalGame();
		
		experiment.warmingUpSecs = argParse.getValueInt("--warming-up-secs");
		experiment.numTrials = argParse.getValueInt("--num-trials");
		experiment.moveLimit = argParse.getValueInt("--move-limit");
		experiment.gameName = (String) argParse.getValue("--game-name");
		experiment.options = (List<String>) argParse.getValue("--game-options");
		experiment.aiStrings = (List<String>) argParse.getValue("--ai-strings");
		experiment.thinkingTime = argParse.getValueDouble("--thinking-time");
		experiment.exportCSV = argParse.getValueString("--export-csv");

		experiment.startExperiment();
	}

}
