package search.mcts.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import main.collections.ScoredInt;
import other.state.State;
import search.mcts.MCTS;
import search.mcts.backpropagation.BackpropagationStrategy;
import search.mcts.nodes.BaseNode;
import search.mcts.nodes.MP_PNMCTSNode;

/**
 * A UCB1-based selection strategy that also includes a 
 * proof-number-search-based term, used for multiplayer games.
 * 
 * @author Szymon Kosakowski
 */
public final class MP_PNS_UCB implements SelectionStrategy
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Variants of calculating PN-based terms for selection strategies.
	 */
	public enum PNUCT_VARIANT 
	{
	    RANK,
	    SUM,
	    MAX,
	    // SOFTMAX?
	}
	
	//-------------------------------------------------------------------------
	
	/** Exploration constant */
	protected double explorationConstant;
	
	/** Constant by which to multiply the PN-based term */
	protected double pnConstant;
	
	/** Minimum visits we must still allow sending into a child, even if it's a proven loss. */
	protected int minVisitsSolvedChild = Integer.MAX_VALUE;
	
	/** Which variant of the PNS-based term do we want to use? */
	protected PNUCT_VARIANT pnsVariant = PNUCT_VARIANT.RANK;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor with default value sqrt(2.0) for exploration constant
	 */
	public MP_PNS_UCB()
	{
		this(Math.sqrt(2.0), 1.0, PNUCT_VARIANT.RANK);
	}
	
	/**
	 * TODO add other params
	 * 
	 * Constructor with parameters for constants and PN UCT variant
	 * @param explorationConstant
	 * @param pnConstant
	 * @param variant
	 */
	public MP_PNS_UCB(final double explorationConstant, final double pnConstant, final PNUCT_VARIANT variant)
	{
		this.explorationConstant = explorationConstant;
		this.pnConstant = pnConstant;
		this.pnsVariant = variant;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public int select(final MCTS mcts, final BaseNode current)
	{
		int bestIdx = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        int numBestFound = 0;

        final double parentLog = Math.log(Math.max(1, current.sumLegalChildVisits()));
        final int numChildren = current.numLegalMoves();
        final State state = current.contextRef().state();
        final int moverAgent = state.playerToAgent(state.mover());
        final double unvisitedValueEstimate = current.valueEstimateUnvisitedChildren(moverAgent);

        final MP_PNMCTSNode currentMP_PNMCTSNode = (MP_PNMCTSNode) current;
        if (currentMP_PNMCTSNode.childSelectionScoresDirty())
        {
        	updateChildrenSelectionScores(currentMP_PNMCTSNode);
        }
        
        for (int i = 0; i < numChildren; ++i) 
        {
        	final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
        	
        	// TODO the isValueProven() check shouldn't be necessary if we 
        	// backpropagate early for solved nodes
        	
        	// old PN-MCTS would prune proven-loss children here, but that doesn't make sense, we can't prove losses anymore
//        	if (child != null && !current.isValueProven(moverAgent)) 
//        	{
//                if (child.proofNumber(moverAgent) == 0 && child.numVisits() > minVisitsSolvedChild) 
//                	continue;
//            }
        	
        	final double exploit;
        	final double explore;

        	if (child == null)
        	{
        		exploit = unvisitedValueEstimate;
        		explore = Math.sqrt(parentLog);
        	}
        	else
        	{
        		exploit = child.exploitationScore(moverAgent);
        		final int numVisits = Math.max(child.numVisits() + child.numVirtualVisits(), 1);
        		explore = Math.sqrt(parentLog / numVisits);
        	}
        	
        	final double pnsTerm = currentMP_PNMCTSNode.childrenPNSSelectionTerms()[i];

        	final double ucb1Value = exploit + explorationConstant * explore + pnConstant * pnsTerm;
        	//System.out.println("ucb1Value = " + ucb1Value);
        	//System.out.println("exploit = " + exploit);
        	//System.out.println("explore = " + explore);

        	if (ucb1Value > bestValue)
        	{
        		bestValue = ucb1Value;
        		bestIdx = i;
        		numBestFound = 1;
        	}
        	else if 
        	(
        		ucb1Value == bestValue 
        		&& 
        		ThreadLocalRandom.current().nextInt() % ++numBestFound == 0
        	)
        	{
        		bestIdx = i;
        	}
        }
        
        return bestIdx;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Updates the PN-based terms of the selection strategy for all children of current.
	 * @param current
	 */
	public void updateChildrenSelectionScores(final MP_PNMCTSNode current)
	{
		//  This is the array that we'll re-compute
		final double[] childrenPNSSelectionTerms = current.childrenPNSSelectionTerms();
		final int numLegalMoves = current.numLegalMoves();
		final int currentPlayer = current.getCurrentPlayer();
		
		switch(pnsVariant)
        {
            case RANK:
            	final List<ScoredInt> sortedChildIndices = new ArrayList<ScoredInt>();
            	
        		// OR node
        		for (int i = 0; i < numLegalMoves; ++i)
				{
					final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
					if (child == null)
					{
						// This means: proof number = 1.0 for unexpanded child. TODO this correct?
						sortedChildIndices.add(new ScoredInt(i, 1.0));
					}
					else
					{
						sortedChildIndices.add(new ScoredInt(i, child.proofNumber(currentPlayer)));
					}
				}

            	Collections.sort(sortedChildIndices, ScoredInt.ASCENDING);
            	double lastNum = Double.NaN;
            	int lastRank = -1;
            	
            	for (int i = 0; i < numLegalMoves; ++i)
            	{
            		final double num = sortedChildIndices.get(i).score();
            		final int childIndex = sortedChildIndices.get(i).object();
            		final int rank;
            		if (lastNum == num)
            		{
            			// There's a tie
            			rank = lastRank;
            		}
            		else
            		{
            			// No tie
            			lastRank = i + 1;
            			lastNum = num;
            			rank = lastRank;
            		}
            		
            		childrenPNSSelectionTerms[childIndex] = (1.0 - (((double) rank) / numLegalMoves));
            	}
            	
            	//System.out.println(Arrays.toString(childrenPNSSelectionTerms));

                break;

            case SUM:

                double sum = 0.0;
                
                
            	// OR node
            	for (int i = 0; i < numLegalMoves; ++i)
				{
					final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
					if (child == null)
					{
						// This means: proof number = 1.0 for unexpanded child. TODO this correct?
						sum += 1.0;
					}
					else
					{
						if (Double.isFinite(child.proofNumber(currentPlayer)))
							sum += child.proofNumber(currentPlayer);
					}
				}
                
                if (sum > 0.0)
                {
                	for (int i = 0; i < numLegalMoves; ++i)
					{
						final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
						final double number;
						if (child == null)
						{
							// This means: proof number = 1.0 for unexpanded child. TODO this correct?
							number = 1.0;
						}
						else
						{
							number = child.proofNumber(currentPlayer);
						}
						
						if (Double.isFinite(number))
							childrenPNSSelectionTerms[i] = (1.0 - (number / sum));
						else
							childrenPNSSelectionTerms[i] = 0.0;
					}
                }
                else
                {
                	// Weird special case, not sure what to do here. Probably it doesn't matter.
                	Arrays.fill(childrenPNSSelectionTerms, 0.0);
                }

                break;

            case MAX:
            	
            	double max = 0.0;
            	boolean haveInf = false;
                
            	// OR node
            	for (int i = 0; i < numLegalMoves; ++i)
				{
					final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
					if (child == null)
					{
						// This means: proof number = 1.0 for unexpanded child. TODO this correct?
						max = Math.max(max, 1.0);
					}
					else
					{
						if (Double.isFinite(child.proofNumber(currentPlayer)))
							max = Math.max(max, child.proofNumber(currentPlayer));
						else
							haveInf = true;
					}
				}
                
                if (haveInf)
                	max += 1.0;
                
                if (max > 0.0)
                {
                	for (int i = 0; i < numLegalMoves; ++i)
					{
						final MP_PNMCTSNode child = (MP_PNMCTSNode) current.childForNthLegalMove(i);
						final double number;
						if (child == null)
						{
							// This means: (dis)proof number = 1.0 for unexpanded child. TODO this correct?
							number = 1.0;
						}
						else
						{
							number = child.proofNumber(currentPlayer);
						}
						
						if (Double.isFinite(number))
							childrenPNSSelectionTerms[i] = (1.0 - (number / max));
						else
							childrenPNSSelectionTerms[i] = 0.0;
					}
                }
                else
                {
                	// Weird special case, not sure what to do here. Probably it doesn't matter.
                	Arrays.fill(childrenPNSSelectionTerms, 0.0);
                }

                break;

            default:
                throw new AssertionError("UNKNOWN PNS METHOD!");
        }
		
		current.setSelectionScoresDirtyFlag(false);
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public int backpropFlags()
	{
		return BackpropagationStrategy.PROOF_DISPROOF_NUMBERS | BackpropagationStrategy.MULTIPLAYER_PNSMCTS;
	}
	
	@Override
	public int expansionFlags()
	{
		return 0;
	}
	
	@Override
	public void customise(final String[] inputs)
	{
		if (inputs.length > 1)
		{
			// We have more inputs than just the name of the strategy
			for (int i = 1; i < inputs.length; ++i)
			{
				final String input = inputs[i];
				
				// TODO add other hyperparams
				
				if (input.startsWith("explorationconstant="))
				{
					explorationConstant = Double.parseDouble(
							input.substring("explorationconstant=".length()));
				}
				else
				{
					System.err.println("PNS-UCB1 ignores unknown customisation: " + input);
				}
			}
		}
	}
	
	//-------------------------------------------------------------------------

}
