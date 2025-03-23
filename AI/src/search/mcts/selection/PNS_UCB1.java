package search.mcts.selection;

import java.util.concurrent.ThreadLocalRandom;

import other.state.State;
import search.mcts.MCTS;
import search.mcts.backpropagation.BackpropagationStrategy;
import search.mcts.nodes.BaseNode;
import search.mcts.nodes.PNMCTSNode;
import search.pns.PNSNode.PNSNodeTypes;

/**
 * A UCB1-based selection strategy that also includes a 
 * proof-number-search-based term.
 * 
 * @author Dennis Soemers
 */
public final class PNS_UCB1 implements SelectionStrategy
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
	protected int minVisitsSolvedChild = 5;
	
	/** Which variant of the PNS-based term do we want to use? */
	protected PNUCT_VARIANT pnsVariant = PNUCT_VARIANT.MAX;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor with default value sqrt(2.0) for exploration constant
	 */
	public PNS_UCB1()
	{
		this(Math.sqrt(2.0), 1.0);
	}
	
	/**
	 * TODO add other params
	 * 
	 * Constructor with parameters for constants
	 * @param explorationConstant
	 * @param pnConstant
	 */
	public PNS_UCB1(final double explorationConstant, final double pnConstant)
	{
		this.explorationConstant = explorationConstant;
		this.pnConstant = pnConstant;
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

        final PNMCTSNode currentPNMCTSNode = (PNMCTSNode) current;
        if (currentPNMCTSNode.childSelectionScoresDirty())
        {
        	updateChildSelectionScores(currentPNMCTSNode);
        }
        
        for (int i = 0; i < numChildren; ++i) 
        {
        	final PNMCTSNode child = (PNMCTSNode) current.childForNthLegalMove(i);
        	
        	// TODO the isValueProven() check shouldn't be necessary if we 
        	// backpropagate early for solved nodes
        	if (child != null && !current.isValueProven(moverAgent)) 
        	{
                if (child.disproofNumber() == 0 && child.numVisits() > minVisitsSolvedChild) 
                	continue;
            }
        	
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

        	final double ucb1Value = exploit + explorationConstant * explore;
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
        
        // TODO implement the PNS part
        //return bestIdx;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Updates the PN-based terms of the selection strategy for all children of current.
	 * @param current
	 */
	public void updateChildSelectionScores(final PNMCTSNode current)
	{
		// TODO compute all the scores
		switch(pnsVariant)
        {
            case PNUCT_VARIANT.RANK:
                setChildRanks();
                double total = this.children.size();
                for(Node child : this.children)
                    child.setPnsSelectionValue(1.0 - (child.getRank() / total));

                break;

            case PNUCT_VARIANT.SUM:

                double sum = 0.0;

                if (type == PNSNodeTypes.OR_NODE) {
                    for (Node child : this.children)
                        if (Double.isFinite(child.getProofNum()))
                            sum += child.getProofNum();
                    if (sum > 0)
                        for (Node child : this.children)
                            if (Double.isFinite(child.getProofNum()))
                                child.setPnsSelectionValue(1 - (child.getProofNum() / sum));
                            else
                                child.setPnsSelectionValue(0);
                } else {
                    for (Node child : this.children)
                        if (Double.isFinite(child.getDisproofNum()))
                            sum += child.getDisproofNum();
                    if (sum > 0)
                        for (Node child : this.children)
                            if (Double.isFinite(child.getDisproofNum()))
                                child.setPnsSelectionValue(1 - (child.getDisproofNum() / sum));
                            else
                                child.setPnsSelectionValue(0);
                }
                break;

            case PNUCT_VARIANT.MAX:

                double max = 0.0;
                boolean wasInfinity = false;

                if (type == PNSNodeTypes.OR_NODE) {
                    for (Node child : this.children)
                        if (Double.isFinite(child.getProofNum()))
                            max = Math.max(child.getProofNum(), max);
                        else
                            wasInfinity = true;

                    if (wasInfinity) max += 1;

                    if (max > 0)
                        for (Node child : this.children)
                            if (Double.isFinite(child.getProofNum()))
                                child.setPnsSelectionValue(1 - (child.getProofNum() / max));
                            else
                                child.setPnsSelectionValue(0);
                } else {
                    for (Node child : this.children)
                        if (Double.isFinite(child.getDisproofNum()))
                            max = Math.max(child.getDisproofNum(), max);
                        else
                            wasInfinity = true;

                    if (wasInfinity) max += 1;

                    if (max > 0)
                        for (Node child : this.children)
                            if (Double.isFinite(child.getDisproofNum()))
                                child.setPnsSelectionValue(1 - (child.getDisproofNum() / max));
                            else
                                child.setPnsSelectionValue(0);
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
		return BackpropagationStrategy.PROOF_DISPROOF_NUMBERS;
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
