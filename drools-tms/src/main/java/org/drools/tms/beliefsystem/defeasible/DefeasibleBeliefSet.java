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
package org.drools.tms.beliefsystem.defeasible;

import java.util.Arrays;
import java.util.List;

import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.QueryElementFactHandle;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.common.PropagationContext;
import org.drools.base.util.FastIterator;
import org.drools.base.util.DoubleLinkedEntry;
import org.drools.tms.LogicalDependency;
import org.drools.tms.TruthMaintenanceSystemEqualityKey;
import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.tms.beliefsystem.jtms.JTMSBeliefSet;
import org.drools.tms.beliefsystem.jtms.JTMSBeliefSetImpl.MODE;
import org.kie.api.runtime.rule.FactHandle;

public class DefeasibleBeliefSet<M extends DefeasibleMode<M>> implements JTMSBeliefSet<M> {
    private BeliefSystem beliefSystem;

    public static final String DEFEATS = Defeats.class.getSimpleName();

    private static final FastIterator iterator = new IteratorImpl();

    private InternalFactHandle rootHandle;

    private M                  rootUndefeated;
    private M                  tailUndefeated;

    private int definitelyPosCount;
    private int definitelyNegCount;
    private int defeasiblyPosCount;
    private int defeasiblyNegCount;
    private int defeatedlyPosCount;
    private int defeatedlyNegCount;

    private static final int DEFINITELY_POS_BIT = 1;
    private static final int DEFINITELY_NEG_BIT = 2;
    private static final int DEFEASIBLY_POS_BIT = 4;
    private static final int DEFEASIBLY_NEG_BIT = 8;
    private static final int DEFEATEDLY_POS_BIT = 16;
    private static final int DEFEATEDLY_NEG_BIT = 32;

    private static final int POS_MASK = DEFINITELY_POS_BIT | DEFEASIBLY_POS_BIT | DEFEATEDLY_POS_BIT;
    private static final int NEG_MASK = DEFINITELY_NEG_BIT | DEFEASIBLY_NEG_BIT | DEFEATEDLY_NEG_BIT;

    private static final int WEAK_POS_MASK = DEFEASIBLY_POS_BIT | DEFEATEDLY_POS_BIT;
    private static final int WEAK_NEG_MASK = DEFEASIBLY_NEG_BIT | DEFEATEDLY_NEG_BIT;

    private int statusMask = 0;

    private DefeasibilityStatus status;

    public DefeasibleBeliefSet(BeliefSystem<M> beliefSystem, InternalFactHandle rootHandle) {
        this.beliefSystem = beliefSystem;
        this.rootHandle = rootHandle;
    }

    public BeliefSystem<M> getBeliefSystem() {
        return beliefSystem;
    }

    public InternalFactHandle getFactHandle() {
        return rootHandle;
    }

    public M getFirst() {
        return rootUndefeated;
    }

    public M getLast() {
        return tailUndefeated;
    }

    public void add( M newDep ) {
        newDep.setStatus( resolveStatus( newDep ) );

        RuleImpl rule = newDep.getLogicalDependency().getJustifier().getRule();

        // first iterate to see if this new dep is defeated. If it's defeated, it can no longer impacts any deps
        // if we checked what it defeats, and later this was defeated, we would have undo action. So we do the cheaper work first.
        boolean wasDefeated = false;
        for (DefeasibleMode<M> existingDep = rootUndefeated; existingDep != null; existingDep = existingDep.getNext()) {
            wasDefeated = checkIsDefeated( newDep, rule, existingDep );
            if (wasDefeated) {
                existingDep.addDefeated( newDep );
                break;
            }
        }

        if (!wasDefeated) {
            M stagedDeps = null;
            for ( M existingDep = rootUndefeated; existingDep != null;) {
                M next = existingDep.getNext();

                if ( checkIsDefeated( existingDep, existingDep.getLogicalDependency().getJustifier().getRule(), newDep ) ) {
                    // fist remove it from the undefeated list
                    removeUndefeated( existingDep );
                    newDep.addDefeated( existingDep );
                    if (existingDep.getRootDefeated() != null) {
                        // build up the list of staged deps, that will need to be reprocessed
                        if (stagedDeps == null) {
                            stagedDeps = existingDep.getRootDefeated();
                        } else {
                            stagedDeps.setPrevious( existingDep.getTailDefeated() );
                            stagedDeps = existingDep.getRootDefeated();
                        }
                    }
                    existingDep.clearDefeated();
                }
                existingDep = next;
            }
            addUndefeated(newDep);
            // now process the staged
            reprocessDefeated(stagedDeps);
        }
        updateStatus();
    }

    private void reprocessDefeated( M deps ) {
        for ( M dep = deps; dep != null; ) {
            M next = dep.getNext();
            dep.clear(); // it needs to be removed, before it can be processed
            add( dep ); // adding back in, effectively reprocesses the dep
            dep = next;
        }
    }

    public void remove( M dep) {
        if (dep.getDefeatedBy() != null) {
            // Defeated deps do not have defeated defeated lists of their own, so just remove.
            DefeasibleMode<M> defeater = dep.getDefeatedBy();
            defeater.removeDefeated(dep);
        } else {
            // ins undefeated, process it's defeated list if they exist
            removeUndefeated(dep);
            if (dep.getRootDefeated() != null) {
                reprocessDefeated( dep.getRootDefeated() );
            }
        }
        updateStatus();
    }

    private boolean checkIsDefeated( DefeasibleMode potentialInferior, RuleImpl rule, DefeasibleMode potentialSuperior ) {
        if ( potentialSuperior.getDefeats() == null ) {
            return false;
        }
        if ( potentialSuperior.getStatus() == DefeasibilityStatus.DEFINITELY && potentialInferior.getStatus() != DefeasibilityStatus.DEFINITELY ) {
            return true;
        }
        // adds the references that defeat the current node
        if (Arrays.binarySearch(potentialSuperior.getDefeats(), rule.getName()) >= 0 ||
            Arrays.binarySearch(potentialSuperior.getDefeats(), rule.getPackage() + "." + rule.getName()) >= 0) {
            if ( "neg".equals( potentialSuperior.getValue() ) ^ "neg".equals( potentialInferior.getValue() ) ) {
                return true;
            }
        }
        return false;
    }

    public void addUndefeated( M dep ) {
        boolean pos = ! ( dep.getValue() != null && MODE.NEGATIVE.getId().equals( dep.getValue() ) );
        switch( dep.getStatus() ) {
            case DEFINITELY:
                if ( pos ) {
                    definitelyPosCount++;
                    statusMask = statusMask | DEFINITELY_POS_BIT;
                } else {
                    definitelyNegCount++;
                    statusMask = statusMask | DEFINITELY_NEG_BIT;
                }
                break;
            case DEFEASIBLY:
                if ( pos ) {
                    defeasiblyPosCount++;
                    statusMask = statusMask | DEFEASIBLY_POS_BIT;
                } else {
                    defeasiblyNegCount++;
                    statusMask = statusMask | DEFEASIBLY_NEG_BIT;
                }
                break;
            case DEFEATEDLY:
                if ( pos ) {
                    defeatedlyPosCount++;
                    statusMask = statusMask | DEFEATEDLY_POS_BIT;
                } else {
                    defeatedlyNegCount++;
                    statusMask = statusMask | DEFEATEDLY_NEG_BIT;
                }
                break;
            case UNDECIDABLY:
                throw new IllegalStateException("Individual logical dependencies cannot be undecidably");
        }

        if (rootUndefeated == null) {
            rootUndefeated = dep;
            tailUndefeated = dep;
        } else {
            if ( dep.getStatus() == DefeasibilityStatus.DEFINITELY ) {
                // Strict dependencies at to the front
                rootUndefeated.setPrevious(dep);
                dep.setNext(rootUndefeated);
                rootUndefeated = dep;
            } else {
                // add to end
                tailUndefeated.setNext(dep);
                dep.setPrevious(tailUndefeated);
                tailUndefeated = dep;
            }
        }
    }

    public void removeUndefeated(DefeasibleMode dep) {
        boolean pos = ! ( dep.getValue() != null && MODE.NEGATIVE.getId().equals(dep.getValue()) );
        switch( dep.getStatus() ) {
            case DEFINITELY:
                if ( pos ) {
                    definitelyPosCount--;
                    if ( definitelyPosCount == 0 ) {
                        statusMask = statusMask ^ DEFINITELY_POS_BIT;
                    }
                } else {
                    definitelyNegCount--;
                    if ( definitelyNegCount == 0 ) {
                        statusMask = statusMask ^ DEFINITELY_NEG_BIT;
                    }
                }
                break;
            case DEFEASIBLY:
                if ( pos ) {
                    defeasiblyPosCount--;
                    if ( defeasiblyPosCount == 0 ) {
                        statusMask = statusMask ^ DEFEASIBLY_POS_BIT;
                    }
                } else {
                    defeasiblyNegCount--;
                    if ( defeasiblyNegCount == 0 ) {
                        statusMask = statusMask ^ DEFEASIBLY_NEG_BIT;
                    }
                }
                break;
            case DEFEATEDLY:
                if ( pos ) {
                    defeatedlyPosCount--;
                    if ( defeatedlyPosCount == 0 ) {
                        statusMask = statusMask ^ DEFEATEDLY_POS_BIT;
                    }
                } else {
                    defeatedlyNegCount--;
                    if ( defeatedlyNegCount == 0 ) {
                        statusMask = statusMask ^ DEFEATEDLY_NEG_BIT;
                    }
                }
                break;
            case UNDECIDABLY:
                throw new IllegalStateException("Individual logical dependencies cannot be undecidably");
        }

        if (this.rootUndefeated == dep) {
            removeFirst();
        } else if (this.tailUndefeated == dep) {
            removeLast();
        } else {
            dep.getPrevious().setNext(dep.getNext());
            dep.getNext().setPrevious(dep.getPrevious());
            dep.clear();
        }
    }

    public M removeFirst() {
        if (this.rootUndefeated == null) {
            return null;
        }
        final M dep = this.rootUndefeated;
        this.rootUndefeated = dep.getNext();
        dep.setNext(null);
        if (this.rootUndefeated != null) {
            this.rootUndefeated.setPrevious(null);
        } else {
            this.tailUndefeated = null;
        }
        return dep;
    }

    public M removeLast() {
        if (this.tailUndefeated == null) {
            return null;
        }
        final M dep = this.tailUndefeated;
        this.tailUndefeated = dep.getPrevious();
        dep.setPrevious(null);
        if (this.tailUndefeated != null) {
            this.tailUndefeated.setNext(null);
        } else {
            this.rootUndefeated = this.tailUndefeated;
        }
        return dep;
    }

    public DoubleLinkedEntry getRootUndefeated() {
        return this.rootUndefeated;
    }

    public DoubleLinkedEntry getTailUnDefeated() {
        return this.tailUndefeated;
    }

    public boolean isEmpty() {
        return rootUndefeated == null;
    }

    public int size() {
        return definitelyPosCount + definitelyNegCount + defeasiblyPosCount + defeasiblyNegCount + defeatedlyPosCount + defeatedlyNegCount;
    }

    public int undefeatdSize() {
        int i = 0;
        for (DefeasibleMode existingNode = rootUndefeated; existingNode != null; existingNode = (DefeasibleMode) existingNode.getNext()) {
            i++;
        }
        return i;
    }

    public void cancel(PropagationContext propagationContext) {
        // get all but last, as that we'll do via the BeliefSystem, for cleanup
        // note we don't update negative, conflict counters. It's needed for the last cleanup operation
        FastIterator it = fastIterator();
        for ( M node =  getFirst(); node != tailUndefeated;  ) {
            M temp = (M) it.next(node); // get next, as we are about to remove it

            LogicalDependency<M> dep = node.getLogicalDependency();

            dep.getJustifier().getLogicalDependencies().remove( dep );
            remove( node );
            node = temp;
        }

        DefeasibleMode<M> node = getFirst();
        LogicalDependency<M> dep = node.getLogicalDependency();
        dep.getJustifier().getLogicalDependencies().remove( dep );
    }

    public void clear(PropagationContext propagationContext) {
    }

    public void setWorkingMemoryAction(WorkingMemoryAction wmAction) {
    }

    public boolean isDefinitelyPosProveable() {
        return  (statusMask &  DEFINITELY_POS_BIT) != 0;
    }

    public boolean isDefinitelyNegProveable() {
        return  (statusMask &  DEFINITELY_NEG_BIT) != 0;
    }

    public boolean isDefeasiblyPosProveable() {
        return  (statusMask &  DEFEASIBLY_POS_BIT) != 0;
    }

    public boolean isDefeasiblyNegProveable() {
        return  (statusMask &  DEFEASIBLY_NEG_BIT) != 0;
    }

    public boolean isDefeatedlyPosProveable() {
        return  (statusMask &  DEFEATEDLY_POS_BIT) != 0;
    }

    public boolean isDefeatedlyNegProveable() {
        return  (statusMask &  DEFEATEDLY_NEG_BIT) != 0;
    }
    public DefeasibilityStatus getStatus() {
        return status;
    }

    public void updateStatus() {
        if ( isDefinitelyPosProveable() ^ isDefinitelyNegProveable() ) {
            status = DefeasibilityStatus.DEFINITELY;
            return;
        }

        if ( isConflicting() ) {
            status = DefeasibilityStatus.UNDECIDABLY;
            return;
        }

        if ( isDefeasiblyPosProveable() ^ isDefeasiblyNegProveable() ) {
            status = DefeasibilityStatus.DEFEASIBLY;
            return;
        }

        if ( isDefeatedlyPosProveable() ^ isDefeatedlyNegProveable() ) {
            status = DefeasibilityStatus.DEFEATEDLY;
            return;
        }

        status = DefeasibilityStatus.UNDECIDABLY;
    }

    public boolean isNegated() {
        return ((statusMask & POS_MASK ) == 0 ) &&  ((statusMask & NEG_MASK ) != 0 );
    }

    public boolean isPositive() {
        return ((statusMask & POS_MASK ) != 0 ) &&  ((statusMask & NEG_MASK ) == 0 );
    }

    public boolean isConflicting() {
        return ((statusMask & POS_MASK ) != 0 ) &&  ((statusMask & NEG_MASK ) != 0 );
    }

    public boolean isDecided() {
        return getStatus() != DefeasibilityStatus.UNDECIDABLY && getStatus() != DefeasibilityStatus.DEFEATEDLY;
    }

    public FastIterator<M> fastIterator() {
        return iterator;
    }

    private static class IteratorImpl<M extends DefeasibleMode<M>> implements FastIterator<M> {

        public M next(M dep) {
            if ( dep.getRootDefeated() != null ) {
                // try going down the list of defeated first
                return dep.getRootDefeated();
            }

            if ( dep.getNext() != null ) {
                return dep.getNext();
            }

            if ( dep.getDefeatedBy() != null ) {
                // go back up to the parent undefeated, and try the next undefeated
                return dep.getDefeatedBy().getNext();
            }

            return null; // nothing more to iterate too.

        }

        public boolean isFullIterator() {
            return true;
        }
    }

    private DefeasibilityStatus resolveStatus( DefeasibleMode node ) {
        List<? extends FactHandle> premise = node.getLogicalDependency().getJustifier().getFactHandles();

        DefeasibilityStatus status = DefeasibilityStatus.resolve( node.getValue() );

        if ( status == null ) {
            DefeasibleRuleNature defeasibleType = DefeasibleRuleNature.STRICT;
            if ( node.getLogicalDependency().getJustifier().getRule().getMetaData().containsKey( DefeasibleRuleNature.DEFEASIBLE.getLabel() ) ) {
                defeasibleType = DefeasibleRuleNature.DEFEASIBLE;
            } else if ( node.getLogicalDependency().getJustifier().getRule().getMetaData().containsKey( DefeasibleRuleNature.DEFEATER.getLabel() ) ) {
                defeasibleType = DefeasibleRuleNature.DEFEATER;
            }

            switch ( defeasibleType ) {
                case DEFEASIBLE :
                    status = checkDefeasible(premise);
                    break;
                case DEFEATER   :
                    status = checkDefeater(premise);
                    break;
                case STRICT     :
                default         :
                    status = checkStrict(premise );
                    break;
            }
        }
        return status;
    }

    private DefeasibilityStatus checkDefeasible(  List<? extends FactHandle> premise ) {
        return DefeasibilityStatus.DEFEASIBLY;
    }

    private DefeasibilityStatus checkDefeater(  List<? extends FactHandle> premise ) {
        return DefeasibilityStatus.DEFEATEDLY;
    }

    private DefeasibilityStatus checkStrict(  List<? extends FactHandle> premise ) {
        // The rule is strict. To prove that the derivation is strict we have to check that all the premises are
        // either facts or strictly proved facts
        for ( FactHandle h : premise ) {
            if ( h instanceof QueryElementFactHandle ) {
                return DefeasibilityStatus.DEFINITELY;
            }
            EqualityKey key = ((InternalFactHandle) h).getEqualityKey();
            if ( key != null && key.getStatus() == EqualityKey.JUSTIFIED ) {
                //DefeasibleBeliefSet bs = (DefeasibleBeliefSet) getTruthMaintenanceSystem().getJustifiedMap().get(((DefaultFactHandle) h).getId());

                DefeasibleBeliefSet bs = (DefeasibleBeliefSet) ((TruthMaintenanceSystemEqualityKey)key).getBeliefSet();


                if ( bs.getStatus() != DefeasibilityStatus.DEFINITELY ) {
                    // to make a fact "definitely provable", all the supporting non-factual premises must be definitely provable.
                    return DefeasibilityStatus.DEFEASIBLY;
                }
            }
            // else it's a fact, so it's a good candidate for definite entailment
        }
        return DefeasibilityStatus.DEFINITELY;
    }


}
