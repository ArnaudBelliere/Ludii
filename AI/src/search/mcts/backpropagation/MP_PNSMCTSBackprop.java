package search.mcts.backpropagation;

import main.math.statistics.IncrementalStats;
import other.context.Context;
import search.mcts.MCTS;
import search.mcts.nodes.BaseNode;
import utils.AIUtils;

public class MP_PNSMCTSBackprop extends BackpropagationStrategy
{
	 	
 	@Override
 	public void computeUtilities
 	(
 		final MCTS mcts,
 		final BaseNode startNode, 
 		final Context context, 
 		final double[] utilities, 
 		final int numPlayoutMoves
 	)
 	{
 		// Do nothing
 	}
	
	@Override
	public int backpropagationFlags()
	{
		return BackpropagationStrategy.PROOF_DISPROOF_NUMBERS | BackpropagationStrategy.MULTIPLAYER_PNSMCTS;
	}
	
	//-------------------------------------------------------------------------
}
