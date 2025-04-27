package search.mcts.backpropagation;

import other.context.Context;
import search.mcts.MCTS;
import search.mcts.nodes.BaseNode;

public class PNSMCTSBackprop extends BackpropagationStrategy
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
		// TODO can't we remove this entire class? PNs are necessary if our Selection strategy wants them, so our
		// Selection strategy can then already return this flag. This class does not do anything else.
		return BackpropagationStrategy.PROOF_DISPROOF_NUMBERS;
	}
	
	//-------------------------------------------------------------------------
}
