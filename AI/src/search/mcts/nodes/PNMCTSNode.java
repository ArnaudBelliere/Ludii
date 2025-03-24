package search.mcts.nodes;

import other.RankUtils;
import other.context.Context;
import other.move.Move;
import search.mcts.MCTS;

/**
 * Node for PN-MCTS tree.
 * 
 * @author Dennis Soemers
 */
public final class PNMCTSNode extends DeterministicNode
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Nodes types in search trees in PN-MCTS
	 * 
	 * @author Dennis Soemers
	 */
	public enum PNMCTSNodeTypes 
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
	public enum PNMCTSNodeValues
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
	protected double proofNumber;
	/** Disproof number for this node */
	protected double disproofNumber;
	
	/** The player to move in the root node of the tree this node is in. */
	protected int rootPlayer;
	
	/** what type of node are we? */
	protected PNMCTSNodeTypes type;
	
	/** The value (in terms of proven/disproven/dont know) for this node */
	protected PNMCTSNodeValues proofValue;
	
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
    public PNMCTSNode
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
    		rootPlayer = ((PNMCTSNode) parent).rootPlayer;
    	}
    	
    	if (context.state().mover() == rootPlayer) 
    	{
            this.type = PNMCTSNodeTypes.OR_NODE;
        }
    	else 
    	{
            this.type = PNMCTSNodeTypes.AND_NODE;
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
                this.proofValue = PNMCTSNodeValues.TRUE;
            } 
            else 
            {
                this.proofValue = PNMCTSNodeValues.FALSE;
            }
        } 
        else 
        {
            this.proofValue = PNMCTSNodeValues.UNKNOWN;
        }
    }
    
    /**
     * Sets the proof and disproof values of the current node as it is done for 
     * PNS in L. V. Allis' "Searching for Solutions in Games and Artificial 
     * Intelligence". Set differently depending on if the node has children yet.
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
        	double disproof;
        	switch (type)
        	{
			case AND_NODE:
				proof = 0.0;
				disproof = Double.POSITIVE_INFINITY;
				for (final BaseNode child : children)
				{
					final PNMCTSNode childNode = (PNMCTSNode) child;
					if (childNode != null)
					{
						proof += childNode.proofNumber;
						
						if (childNode.disproofNumber < disproof)
						{
							disproof = childNode.disproofNumber;
						}
					}
					else
					{
						// An unexpanded child
						// TODO verify that this is indeed what we want to do?
						// NOTE: addition for proof, setting for disproof
						proof += 1.0;
						disproof = 1.0;
					}
				}

                // If nothing changed return false
                if (this.proofNumber == proof && this.disproofNumber == disproof) 
                {
                    return false;
                }
                
				this.proofNumber = proof;
				this.disproofNumber = disproof;
				return true;
			case OR_NODE:
				disproof = 0.0;
				proof = Double.POSITIVE_INFINITY;
				
				for (final BaseNode child : children)
				{
					final PNMCTSNode childNode = (PNMCTSNode) child;
					if (childNode != null)
					{
						disproof += childNode.disproofNumber;
						
						if (childNode.proofNumber < proof)
						{
							proof = childNode.proofNumber;
						}
					}
					else
					{
						// An unexpanded child
						// TODO verify that this is indeed what we want to do?
						// NOTE: addition for disproof, setting for proof
						disproof += 1.0;
						proof = 1.0;
					}
				}

                // If nothing changed return false
                if (this.proofNumber == proof && this.disproofNumber == disproof) 
                {
                    return false;
                }
                
				this.proofNumber = proof;
				this.disproofNumber = disproof;
				
				if (proof == 0.0)
					proofValue = PNMCTSNodeValues.TRUE;
				else if (disproof == 0.0)
					proofValue = PNMCTSNodeValues.FALSE;
				
				return true;
			default:
				System.err.println("Unknown node type in PNMCTSNode.setProofAndDisproofNumbers()");
				break;
        	}
        } 
        else 
        {
        	// Terminal node!
        	switch (proofValue)
        	{
			case FALSE:
				this.proofNumber = Double.POSITIVE_INFINITY;
				this.disproofNumber = 0.0;
				break;
			case TRUE:
				this.proofNumber = 0.0;
				this.disproofNumber = Double.POSITIVE_INFINITY;
				break;
			case UNKNOWN:
				System.err.println("Terminal node has UNKNOWN proof value in PNMCTSNode!");
				break;
			default:
				System.err.println("Unknown proof value in PNMCTSNode.setProofAndDisproofNumbers()");
				break;
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
    public PNMCTSNodeTypes nodeType()
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
    public double proofNumber()
    {
    	return proofNumber;
    }
    
    /**
     * @return Current disproof number for this node.
     */
    public double disproofNumber()
    {
    	return disproofNumber;
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
    	return (proofValue != PNMCTSNodeValues.UNKNOWN);
    }
    
	//-------------------------------------------------------------------------

}
