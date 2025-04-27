package search.mcts.nodes;

import other.RankUtils;
import other.context.Context;
import other.move.Move;
import search.mcts.MCTS;

/**
 * Node for Multiplayer PN-MCTS tree.
 * 
 * @author Szymon Kosakowski
 */
public final class MP_PNMCTSNode extends IPNMCTSNode
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Nodes types in search trees in PN-MCTS
	 * 
	 * @author Dennis Soemers
	 */
	public enum MP_PNMCTSNodeTypes 
	{
        /** An OR node */
        OR_NODE,

        /** An AND node */
        AND_NODE
    }
	
	/**
	 * Values of nodes in search trees in PN-MCTS
	 * 
	 * @author Dennis Soemers
	 */
	public enum MP_PNMCTSNodeValues
	{
		/** A proven node */
		TRUE,
		
		/** A disproven node */
		FALSE,
		
		/** Unknown node (yet to prove or disprove) */
		UNKNOWN
	}
	
	//-------------------------------------------------------------------------
	
	/** Proof numbers for this node (one per player) */
	protected double[] proofNumbers;
	
	/** The value (in terms of proven/disproven/dont know) for this node (one per player) */
	protected MP_PNMCTSNodeValues[] proofValue;
	
	/** The player to move in this node. */
	protected int currentPlayer;
	
	/** Number of players in the game. */
	protected int numPlayers;
	
	/** The best rank that is still unclaimed. This will be the target rank (what we consider "winning" for proving nodes) in our children */
	protected final double bestAvailableRank;
	
	//-------------------------------------------------------------------------
    
    /**
     * Constructor 
     * 
     * @param mcts
     * @param parent
     * @param parentMove
     * @param parentMoveWithoutConseq
     * @param context
     */
    public MP_PNMCTSNode
    (
    	final MCTS mcts, 
    	final BaseNode parent, 
    	final Move parentMove, 
    	final Move parentMoveWithoutConseq,
    	final Context context
    )
    {
    	super(mcts, parent, parentMove, parentMoveWithoutConseq, context);
    	
    	bestAvailableRank = context.computeNextWinRank();
    	
    	currentPlayer = context.state().mover();
    	
    	// Player 0 is not considered, players start from index 1
    	numPlayers = context.trial().ranking().length - 1;
    	
    	proofNumbers = new double[numPlayers + 1];
    	proofValue = new MP_PNMCTSNodeValues[numPlayers + 1];
   
    	for (int p = 1; p <= numPlayers; p++) 
    	{
    		proofNumbers[p] = 1.0;
    		proofValue[p] = MP_PNMCTSNodeValues.UNKNOWN;
    	}
    	
    	if (parent != null)
    		evaluate();
    	
        setProofAndDisproofNumbers();
    }
    
    //-------------------------------------------------------------------------
    
    /**
     * Evaluates a node as in PNS according to L. V. Allis' 
     * "Searching for Solutions in Games and Artificial Intelligence"
     */
    public void evaluate() 
    {
    	for (int p = 1; p <= numPlayers; ++p)
    	{
    		if (!context.active(p))
    		{
    			// TODO check if this handles swap rule correctly
    			final double rank = context.trial().ranking()[p];
    			
    			if (rank == ((MP_PNMCTSNode) parent).bestAvailableRank)
    			{
    				// proven node
    				proofNumbers[p] = 0.0;
    				proofValue[p] = MP_PNMCTSNodeValues.TRUE;
    			}
    			else
    			{
    				// disproven node
    				proofNumbers[p] = Double.POSITIVE_INFINITY;
    				proofValue[p] = MP_PNMCTSNodeValues.FALSE;
    			}
    		}
    	}
    }
    
    @Override
    public boolean setProofAndDisproofNumbers() 
    {
        if (legalMoves.length > 0) 
        {
        	// Not a terminal node
        	double proof;
        	boolean changed = false;
        	
        	for (int playerNum = 1; playerNum <= numPlayers; playerNum++)
        	{
        		if (proofValue[playerNum] != MP_PNMCTSNodeValues.UNKNOWN)
        			continue;

        		final MP_PNMCTSNodeTypes playerType = (playerNum == currentPlayer ? MP_PNMCTSNodeTypes.OR_NODE : MP_PNMCTSNodeTypes.AND_NODE);
	        	switch (playerType)
	        	{
				case AND_NODE:
					proof = 0.0;
					for (final BaseNode child : children)
					{
						final MP_PNMCTSNode childNode = (MP_PNMCTSNode) child;
						if (childNode != null)
						{
							proof += childNode.proofNumber(playerNum);
						}
						else
						{
							// An unexpanded child
							proof += 1.0;
						}
					}
	
	                if (this.proofNumbers[playerNum] != proof) 
	                {
						this.proofNumbers[playerNum] = proof;
						
						if (proof == 0.0) 
						{
							proofValue[playerNum] = MP_PNMCTSNodeValues.TRUE;
							for (int p = 1; p <= numPlayers; p++)
							{
								if (p != playerNum)
								{
									proofValue[p] = MP_PNMCTSNodeValues.FALSE;
									proofNumbers[p] = Double.POSITIVE_INFINITY;
								}
							}
						}
						else if (proof == Double.POSITIVE_INFINITY)
						{
							proofValue[playerNum] = MP_PNMCTSNodeValues.FALSE;
						}
						
						changed = true;
	                }
	                break;
	                
				case OR_NODE:
					proof = Double.POSITIVE_INFINITY;
					
					for (final BaseNode child : children)
					{
						final MP_PNMCTSNode childNode = (MP_PNMCTSNode) child;
						if (childNode != null)
						{
							if (childNode.proofNumber(playerNum) < proof)
							{
								proof = childNode.proofNumber(playerNum);
							}
						}
						else
						{
							// An unexpanded child
							proof = Math.min(1.0, proof);
						}
					}

	                if (this.proofNumbers[playerNum] != proof) 
	                {
						this.proofNumbers[playerNum] = proof;
						
						if (proof == 0.0) 
						{
							proofValue[playerNum] = MP_PNMCTSNodeValues.TRUE;
							for (int p = 1; p <= numPlayers; p++)
							{
								if (p != playerNum)
								{
									proofValue[p] = MP_PNMCTSNodeValues.FALSE;
									proofNumbers[p] = Double.POSITIVE_INFINITY;
								}
							}
						}
						else if (proof == Double.POSITIVE_INFINITY)
						{
							proofValue[playerNum] = MP_PNMCTSNodeValues.FALSE;
						}
						
						changed = true;
	                }
	                
	                break;
				default:
					System.err.println("Unknown node type in MP_PNMCTSNode.setProofAndDisproofNumbers()");
					break;
	        	}
        	}
        	
        	return changed;
        } 
        else 
        {
        	// Terminal node!
        	
        	for (int playerNum = 1; playerNum <= numPlayers; playerNum++)
        	{
	        	switch (proofValue[playerNum])
	        	{
				case FALSE:
					this.proofNumbers[playerNum] = Double.POSITIVE_INFINITY;
					break;
				case TRUE:
					this.proofNumbers[playerNum] = 0.0;
					break;
				case UNKNOWN:
					System.err.println("Terminal node has UNKNOWN proof value in MP_PNMCTSNode!");
					break;
				default:
					System.err.println("Unknown proof value in MP_PNMCTSNode.setProofAndDisproofNumbers()");
					break;
	        	}
        	}
        }
        
        // If we haven't expanded yet it will definitely be changed so return true
        return true;
    }
    
    /**
     * @return Current proof number for this node.
     */
    public double proofNumber(final int player)
    {
    	return proofNumbers[player];
    }
    
    //-------------------------------------------------------------------------
    
    @Override
    public boolean isValueProven(final int agent)
    {
    	return (proofValue[agent] == MP_PNMCTSNodeValues.TRUE);
    }
    
    @Override
    public double expectedScore(final int agent)
    {
    	if (proofValue[agent] == MP_PNMCTSNodeValues.TRUE && parent != null)
    	{
    		//System.out.println("returning " + RankUtils.rankToUtil(((MP_PNMCTSNode) parent).bestAvailableRank, numPlayers) + " instead of " + super.expectedScore(agent));
			return RankUtils.rankToUtil(((MP_PNMCTSNode) parent).bestAvailableRank, numPlayers);
    	}
    	
    	return super.expectedScore(agent);
    }
    
    public int getCurrentPlayer() { return currentPlayer; }

	//-------------------------------------------------------------------------

}
