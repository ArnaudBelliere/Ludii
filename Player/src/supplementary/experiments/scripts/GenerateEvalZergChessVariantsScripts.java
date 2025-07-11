package supplementary.experiments.scripts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import main.StringRoutines;
import main.UnixPrintWriter;

/**
 * Generates scripts to evaluate different variants of Zerg Chess.
 * 
 * @author Dennis Soemers
 */
public class GenerateEvalZergChessVariantsScripts 
{
	
	public static void main(final String[] args)
	{
		final int numJavaCallsPerBatch = 24;
		
		final String[] rowsOptions = new String[] {"Rows/8", "Rows/9", "Rows/10"};
		final String[] emptyRowsOptions = new String[] {"Empty Rows/2", "Empty Rows/3"};
		final String[] whiteBishopsKnightsRooksOptions = new String[] {"White Bishops Knights Rooks/1", "White Bishops Knights Rooks/2"};
		final String[] whiteDoubleMovePerTurnOptions = new String[] {"White Double Move per Turn/No", "White Double Move per Turn/Yes"};
		final String[] blackDoubleMovePerTurnOptions = new String[] {"Black Double Move per Turn/No", "Black Double Move per Turn/Yes"};
		
		final List<String> javaCalls = new ArrayList<String>();
		
		for (final String rowsOption : rowsOptions)
		{
			for (final String emptyRowsOption : emptyRowsOptions)
			{
				for (final String whiteBishopsKnightsRooksOption : whiteBishopsKnightsRooksOptions) 
				{
					for (final String whiteDoubleMovePerTurnOption : whiteDoubleMovePerTurnOptions)
					{
						for (final String blackDoubleMovePerTurnOption : blackDoubleMovePerTurnOptions)
						{
							final String javaCall = StringRoutines.join(" ", new String[] {
									"nohup",
									"java",
									"-da",
									"-dsa",
									"-XX:+UseStringDeduplication",
									"-XX:+HeapDumpOnOutOfMemoryError",
									"-XX:HeapDumpPath=/home/dsoemers/TestZergChess/java_pid%p.hprof",
									"-jar",
									"Ludii.jar",
									"--eval-game",
									"--warming-up-secs 30",
									"--num-trials 150",
									"--move-limit 250",
									"--game-name",
									StringRoutines.quote("Zerg Chess.lud"),
									"--game-options",
									StringRoutines.join(" ", new String[] {
										StringRoutines.quote(rowsOption),
										StringRoutines.quote(emptyRowsOption),
										StringRoutines.quote(whiteBishopsKnightsRooksOption),
										StringRoutines.quote(whiteDoubleMovePerTurnOption),
										StringRoutines.quote(blackDoubleMovePerTurnOption),
									}),
									"--ai-strings",
									StringRoutines.quote("Alpha-Beta"),
									StringRoutines.quote("Alpha-Beta"),
									"--thinking-time 3",
									"--export-csv",
									StringRoutines.quote("/home/dsoemers/TestZergChess/Experiment_" + javaCalls.size() + ".csv"),
									"> /home/dsoemers/TestZergChess/" + javaCalls.size() + ".out",
									"2> /home/dsoemers/TestZergChess/" + javaCalls.size() + ".err",
									"&"
							});
							
							javaCalls.add(javaCall);
						}
					}
				}
			}
		}
		
		int numBatchScripts = 0;
		int numJavaCallsWritten = 0;
		while (numJavaCallsWritten < javaCalls.size())
		{
			// Start a new batch of Java calls in a single script
			try (final PrintWriter writer = new UnixPrintWriter(new File("D:/Downloads/EvalZergChess_" + numBatchScripts + ".sh"), "UTF-8"))
			{
				writer.println("trap '' HUP");
				writer.println();
				
				int numJavaCallsThisScript = 0;
				while (numJavaCallsThisScript < numJavaCallsPerBatch)
				{
					writer.println(javaCalls.get(numJavaCallsWritten));
					
					++numJavaCallsWritten;
					++numJavaCallsThisScript;
				}
				
				++numBatchScripts;
			}
			catch (final FileNotFoundException | UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
		}
	}

}
