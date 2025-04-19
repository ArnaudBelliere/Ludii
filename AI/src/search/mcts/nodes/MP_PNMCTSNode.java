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
public final class MP_PNMCTSNode extends DeterministicNode
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
	
	/** Proof number for this node */
	protected double[] proofNumbers;
	
	/** The player to move in the root node of the tree this node is in. */
	protected int rootPlayer;
	
	/** The player to move in this node. */
	protected int currentPlayer;
	
	/** Number of players in the game. */
	protected int numPlayers;
	
	/** what type of node are we? */
	protected MP_PNMCTSNodeTypes type;
	
	/** The value (in terms of proven/disproven/dont know) for this node */
	protected MP_PNMCTSNodeValues[] proofValue;
	
	/** Are the cached PNS-based terms of childrens' selection scores outdated? */
	protected boolean childSelectionScoresDirty = false;
	
	/** Cached PNS-based terms for selection scores for all our children (including unexpanded ones) */
	protected final double[] childrenPNSSelectionTerms;
	
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
    	
    	if (parent == null)
    	{
    		// We are the root node
    		rootPlayer = context.state().mover();
    	}
    	else
    	{
    		rootPlayer = ((MP_PNMCTSNode) parent).rootPlayer;
    	}
    	
    	currentPlayer = context.state().mover();
    	
    	// Player 0 is not considered, players start from index 1
    	numPlayers = context.trial().ranking().length - 1;
    	
    	proofNumbers = new double[numPlayers + 1];
    	proofValue = new MP_PNMCTSNodeValues[numPlayers + 1];
   
    	for(int p = 1; p <= numPlayers; p++) {
    		proofNumbers[p] = 1.0;
    		proofValue[p] = MP_PNMCTSNodeValues.UNKNOWN;
    	}
    	
    	
    	
    	if (context.state().mover() == rootPlayer) 
    	{
            this.type = MP_PNMCTSNodeTypes.OR_NODE;
        }
    	else 
    	{
            this.type = MP_PNMCTSNodeTypes.AND_NODE;
        }
    	
    	evaluate();
        setProofAndDisproofNumbers();
        
        childrenPNSSelectionTerms = new double[numLegalMoves()];
    }
    
    //-------------------------------------------------------------------------
    
    /**
     * Evaluates a node as in PNS according to L. V. Allis' 
     * "Searching for Solutions in Games and Artificial Intelligence"
     */
    public void evaluate() 
    {
        if (this.context.trial().over()) 
        {
            if (RankUtils.utilities(this.context)[rootPlayer] == 1.0) 
            {
                this.proofValue[currentPlayer] = MP_PNMCTSNodeValues.TRUE;
                for(int p = 1; p <= numPlayers; p++) {
            		if(p != currentPlayer)
            			proofValue[p] = MP_PNMCTSNodeValues.FALSE;
            	}
            } 
            else 
            {
                this.proofValue[currentPlayer] = MP_PNMCTSNodeValues.FALSE;
            }
        } 
        else 
        {
            this.proofValue[currentPlayer] = MP_PNMCTSNodeValues.UNKNOWN;
        }
    }
    
    /**
     * Sets the proof and disproof values of the current node as it is done for 
     * PNS in L. V. Allis' "Searching for Solutions in Games and Artificial 
     * Intelligence". Set differently depending on if the node has children yet.
     * 
     * In multiplayer PNSMCTS version only proofNumbers are used.
     *
     * @return Returns true if something was changed and false if not. 
     * Used to improve PN-MCTS speed.
     */
    public boolean setProofAndDisproofNumbers() 
    {
        if (legalMoves.length > 0) 
        {
        	// Not a terminal node
        	double proof;
        	boolean changed = false;
        	
        	for(int playerNum = 1; playerNum <= numPlayers; playerNum++)
        	{
        		if(proofValue[playerNum] == MP_PNMCTSNodeValues.FALSE || proofValue[playerNum] == MP_PNMCTSNodeValues.TRUE)
        			continue;

        		MP_PNMCTSNodeTypes playerType = (playerNum == currentPlayer ? MP_PNMCTSNodeTypes.OR_NODE : MP_PNMCTSNodeTypes.AND_NODE);
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
							// TODO verify that this is indeed what we want to do?
							proof += 1.0;
						}
					}
	
	                // If nothing changed return false
	                if (this.proofNumbers[playerNum] != proof) 
	                {
						this.proofNumbers[playerNum] = proof;
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
							// TODO verify that this is indeed what we want to do?
							proof = 1.0;
						}
					}

	                if (this.proofNumbers[playerNum] != proof) 
	                {
						this.proofNumbers[playerNum] = proof;
						
						if (proof == 0.0) {
							proofValue[playerNum] = MP_PNMCTSNodeValues.TRUE;
							for(int p = 1; p <= numPlayers; p++)
								if(p != playerNum)
									proofValue[p] = MP_PNMCTSNodeValues.FALSE;
						}
						else if (proof == Double.POSITIVE_INFINITY)
							proofValue[playerNum] = MP_PNMCTSNodeValues.FALSE;
						
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
        	
        	for(int playerNum = 1; playerNum <= numPlayers; playerNum++)
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
					// In multiplayer game it is possible to be terminal (lost) for one player, but still unknown for others
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
     * @return Do our childrens' PNS-based selection terms need updating?
     */
    public boolean childSelectionScoresDirty()
    {
    	return childSelectionScoresDirty;
    }
    
    /**
     * @return What type of node are we? (OR / AND)
     */
    public MP_PNMCTSNodeTypes nodeType()
    {
    	return type;
    }
    
    /**
     * Store a flag saying whether the cached PNS-based terms of our childrens'
     * selection scores are (potentially) outdated.
     * 
     * @param newFlag
     */
    public void setSelectionScoresDirtyFlag(final boolean newFlag)
    {
    	childSelectionScoresDirty = newFlag;	// TODO selection strategy will have to account for this
    }
    
    /**
     * @return Current proof number for this node.
     */
    public double proofNumber(final int player)
    {
    	return proofNumbers[player];
    }
    
    /**
     * @return Array of PNS-based terms for selection strategy for all of our children.
     */
    public double[] childrenPNSSelectionTerms()
    {
    	return childrenPNSSelectionTerms;
    }
    
    //-------------------------------------------------------------------------
    
    @Override
    public boolean isValueProven(final int agent)
    {
    	return (proofValue[agent] != MP_PNMCTSNodeValues.UNKNOWN);
    }
    
    @Override
    public double expectedScore(final int agent)
    {
    	if (rootPlayer == agent)
    	{
    		if (proofValue[agent] == MP_PNMCTSNodeValues.TRUE)
    			return 1.0;
    	}
    	
    	return super.expectedScore(agent);
    }
    
    public int getCurrentPlayer() { return currentPlayer; }

	//-------------------------------------------------------------------------

}
