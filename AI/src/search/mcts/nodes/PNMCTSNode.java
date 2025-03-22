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
    	// TODO probably need to re-think how this should work due to not fully
    	// expanding nodes at once?
    	
        // If this node has child nodes
        if (this.numUnvisitedChildren > 0) 
        {
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
				}

                // If nothing changed return false
                if (this.proofNumber == proof && this.disproofNumber == disproof) 
                {
                    return false;
                } 
                else 
                {
                    this.proofNumber = proof;
                    this.disproofNumber = disproof;
                    return true;
                }
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
				}

                // If nothing changed return false
                if (this.proofNumber == proof && this.disproofNumber == disproof) 
                {
                    return false;
                } 
                else 
                {
                    this.proofNumber = proof;
                    this.disproofNumber = disproof;
                    return true;
                }
			default:
				System.err.println("Unknown node type in PNMCTSNode.setProofAndDisproofNumbers()");
				break;
        	}
        } 
        else 
        {
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
				this.proofNumber = 1.0;
				this.disproofNumber = 1.0;
				break;
			default:
				System.err.println("Unknown proof value in PNMCTSNode.setProofAndDisproofNumbers()");
				break;
        	}
        }
        
        // If we haven't expanded yet it will definitely be changed so return true
        return true;
    }
    
    //-------------------------------------------------------------------------
    
    /**
     * One of our children has an updated pessimistic bound for the given agent; 
     * check if we should also update now
     * 
     * @param agent
     * @param pessBound
     * @param fromChild Child from which we receive update
     */
    public void updatePessBounds(final int agent, final double pessBound, final PNMCTSNode fromChild)
    {
    	final double oldPess = pessimisticScores[agent];
    	
    	if (pessBound > oldPess)	// May be able to increase pessimistic bounds
    	{
    		final int moverAgent = contextRef().state().playerToAgent(contextRef().state().mover());
    		
    		if (moverAgent == agent)
    		{
    			// The agent for which one of our children has a new pessimistic bound
    			// is the agent to move in this node. Hence, we can update directly
    			pessimisticScores[agent] = pessBound;
    			
    			// Mark any children with an optimistic bound less than or equal to our
    			// new pessimistic bound as pruned
    			for (int i = 0; i < children.length; ++i)
    			{
    				final PNMCTSNode child = (PNMCTSNode) children[i];
    				
    				if (child != null)
    				{
    					if (child.optBound(agent) <= pessBound)
    						child.markPruned();
    				}
    			}
    			
    			if (parent != null)
    				((PNMCTSNode) parent).updatePessBounds(agent, pessBound, this);
    		}
    		else
    		{
    			// The agent for which one of our children has a new pessimistic bound
    			// is NOT the agent to move in this node. Hence, we only update to
    			// the minimum pessimistic bound over all children.
    			//
    			// Technically, if the real value (opt = pess) were proven for the
    			// agent to move, we could restrict the set of children over
    			// which we take the minimum to just those that have the optimal
    			// value for the agent to move.
    			//
    			// This is more expensive to implement though, and only relevant in
    			// games with more than 2 players, and there likely also only very
    			// rarely, so we don't bother doing this.
    			double minPess = pessBound;
    			
    			for (int i = 0; i < children.length; ++i)
    			{
    				final PNMCTSNode child = (PNMCTSNode) children[i];
    				
    				if (child == null)
    				{
    					return;		// Can't update anything if we have an unvisited child left
    				}
    				else
    				{
    					final double pess = child.pessBound(agent);
    					if (pess < minPess)
    					{
    						if (pess == oldPess)
    							return;		// Won't be able to update
    						
    						minPess = pess;
    					}
    				}
    			}
    			
    			if (minPess < oldPess)
    			{
    				System.err.println("ERROR in updatePessBounds()!");
    				System.err.println("oldPess = " + oldPess);
    				System.err.println("minPess = " + minPess);
    				System.err.println("pessBound = " + pessBound);
    			}
    			
    			// We can update
    			pessimisticScores[agent] = minPess;
    			if (parent != null)
    				((PNMCTSNode) parent).updatePessBounds(agent, minPess, this);
    		}
    	}
    }
    
    /**
     * One of our children has an updated optimistic bound for the given agent; 
     * check if we should also update now
     * 
     * @param agent
     * @param optBound
     * @param fromChild Child from which we receive update
     */
    public void updateOptBounds(final int agent, final double optBound, final PNMCTSNode fromChild)
    {
    	final int moverAgent = contextRef().state().playerToAgent(contextRef().state().mover());
    	if (moverAgent == agent)
    	{
    		if (optBound <= pessimisticScores[agent])
    		{
    			// The optimistic bound propagated up from the child is at best as good
    			// as our pessimistic score for the agent to move in this node, so
    			// we can prune the child
    			fromChild.markPruned();
    		}
    	}
    	
    	final double oldOpt = optimisticScores[agent];
    	
    	if (optBound < oldOpt)	// May be able to decrease optimistic bounds
    	{
    		// Regardless of who the mover in this node is, any given agent's optimistic
    		// bound should always just be the maximum over all their children
    		double maxOpt = optBound;
			
			for (int i = 0; i < children.length; ++i)
			{
				final PNMCTSNode child = (PNMCTSNode) children[i];
				
				if (child == null)
				{
					return;		// Can't update anything if we have an unvisited child left
				}
				else
				{
					final double opt = child.optBound(agent);
					if (opt > maxOpt)
					{
						if (opt == oldOpt)
							return;		// Won't be able to update
						
						maxOpt = opt;
					}
				}
			}
			
			if (maxOpt > oldOpt)
				System.err.println("ERROR in updateOptBounds()!");
			
			// We can update
			optimisticScores[agent] = maxOpt;
			if (parent != null)
				((PNMCTSNode) parent).updateOptBounds(agent, maxOpt, this);
    	}
    }
    
    //-------------------------------------------------------------------------
    
    /**
     * @param agent
     * @return Current pessimistic bound for given agent
     */
    public double pessBound(final int agent)
    {
    	return pessimisticScores[agent];
    }
    
    /**
     * @param agent
     * @return Current optimistic bound for given agent
     */
    public double optBound(final int agent)
    {
    	return optimisticScores[agent];
    }
    
    /**
     * Mark this node as being "pruned"
     */
    public void markPruned()
    {
    	pruned = true;
//    	final ScoreBoundsNode sbParent = (ScoreBoundsNode) parent;
//    	final int parentMover = sbParent.deterministicContextRef().state().playerToAgent(sbParent.deterministicContextRef().state().mover());
//    	System.out.println();
//    	System.out.println("Marked as pruned");
//    	System.out.println("Parent agent to move = " + parentMover);
//    	System.out.println("My pessimistic bound for agent " + parentMover + " = " + pessBound(parentMover));
//    	System.out.println("My optimistic bound for agent " + parentMover + " = " + optBound(parentMover));
//    	System.out.println("Parent pessimistic bound for agent " + parentMover + " = " + sbParent.pessBound(parentMover));
//    	System.out.println("Parent optimistic bound for agent " + parentMover + " = " + sbParent.optBound(parentMover));
//    	System.out.println("My status = " + deterministicContextRef().trial().status());
    }
    
    /**
     * @return Did this node get marked as "pruned"?
     */
    public boolean isPruned()
    {
    	return pruned;
    }
    
	//-------------------------------------------------------------------------

}
