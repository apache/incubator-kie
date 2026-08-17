/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.tms.beliefsystem.jtms;

import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.core.common.InternalFactHandle;
import org.drools.tms.LogicalDependency;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.common.PropagationContext;
import org.drools.base.util.LinkedList;

public class JTMSBeliefSetImpl<M extends JTMSMode<M>> extends LinkedList<M> implements JTMSBeliefSet<M> {

    private BeliefSystem<M> beliefSystem;

    private WorkingMemoryAction wmAction;

    private InternalFactHandle rootHandle;

    private int posCounter = 0;
    private int negCounter = 0;

    public JTMSBeliefSetImpl(BeliefSystem<M> beliefSystem, InternalFactHandle rootHandle) {
        this.beliefSystem = beliefSystem;
        this.rootHandle = rootHandle;
    }

    public JTMSBeliefSetImpl() {
    }

    @Override
    public void add(M node ) {
        JTMSMode mode = node;
        String value = mode.getValue();
        boolean neg = MODE.NEGATIVE.getId().equals( value );
        if ( neg ) {
            super.addLast( node ); //we add negatives to end
            negCounter++;
        } else {
            super.addFirst( node ); // we add positied to start
            posCounter++;
        }
    }
    
    @Override
    public void remove(M node ) {
        super.remove(node);

        JTMSMode mode = node;
        String value = mode.getValue();

        boolean neg = MODE.NEGATIVE.getId().equals( value );
        if ( neg ) {
            negCounter--;
        } else {
            posCounter--;
        }

    }    
    
    public BeliefSystem getBeliefSystem() {
        return beliefSystem;
    }

    public InternalFactHandle getFactHandle() {
        return this.rootHandle;
    }
    
    @Override
    public boolean isNegated() {
        return posCounter == 0 && negCounter > 0;
    }

    @Override
    public boolean isDecided() {
        return !isConflicting();
    }

    @Override
    public boolean isConflicting() {
        return posCounter > 0 && negCounter > 0;
    }

    @Override
    public boolean isPositive() {
        return negCounter == 0 && posCounter > 0;
    }    
    
    public enum MODE {
        POSITIVE( "pos" ),
        NEGATIVE( "neg" );

        private String string;
        MODE( String string ) {
            this.string = string;
        }

        public String toExternalForm() {
            return this.string;
        }

        @Override
        public String toString() {
            return this.string;
        }

        public String getId() {
            return this.string;
        }

        public static MODE resolve( Object id ) {
            if ( id == null ) {
                return null;
            } else if( NEGATIVE == id || NEGATIVE.getId().equalsIgnoreCase( id.toString() ) ) {
                return NEGATIVE;
            } else if( POSITIVE == id || POSITIVE.getId().equalsIgnoreCase( id .toString()) ) {
                return POSITIVE;
            } else {
                return null;
            }
        }
    }

    public void cancel(PropagationContext context) {        
        // get all but last, as that we'll do via the BeliefSystem, for cleanup
        // note we don't update negative, conflict counters. It's needed for the last cleanup operation
        JTMSMode<M> entry = getFirst();
        while (entry != getLast()) {
            JTMSMode<M> temp = entry.getNext(); // get next, as we are about to remove it
            final LogicalDependency<M> node =  entry.getLogicalDependency();
            node.getJustifier().getLogicalDependencies().remove( node );
            remove( (M) entry );
            entry = temp;
        }

        JTMSMode<M> last = getFirst();
        final LogicalDependency node = last.getLogicalDependency();
        node.getJustifier().getLogicalDependencies().remove( node );
        beliefSystem.delete( node, this, context );
    }
    
    public void clear(PropagationContext context) { 
        // remove all, but don't allow the BeliefSystem to clean up, the FH is most likely going to be used else where
        JTMSMode<M> entry = getFirst();
        while (entry != null) {
            JTMSMode<M> temp =  entry.getNext(); // get next, as we are about to remove it
            final LogicalDependency<M> node = entry.getLogicalDependency();
            node.getJustifier().getLogicalDependencies().remove( node );
            remove( (M) entry );
            entry = temp;
        }
    }    

    public void setWorkingMemoryAction(WorkingMemoryAction wmAction) {
        this.wmAction = wmAction;
    }    
    
    public WorkingMemoryAction getWorkingMemoryAction() {
        return this.wmAction;
    }
}
