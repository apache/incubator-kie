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
package org.drools.tms;

import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.base.rule.EntryPointId;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.util.LinkedList;
import org.drools.base.util.LinkedListEntry;
import org.drools.kiesession.consequence.DefaultKnowledgeHelper;
import org.drools.kiesession.entrypoints.NamedEntryPoint;
import org.drools.tms.agenda.TruthMaintenanceSystemInternalMatch;
import org.drools.tms.agenda.TruthMaintenanceSystemRuleTerminalNodeLeftTuple;
import org.drools.tms.beliefsystem.BeliefSet;
import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.tms.beliefsystem.BeliefSystemMode;
import org.drools.tms.beliefsystem.ModedAssertion;
import org.drools.tms.beliefsystem.simple.SimpleLogicalDependency;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.Match;

public class TruthMaintenanceSystemKnowledgeHelper<T extends ModedAssertion<T>> extends DefaultKnowledgeHelper {

    private LinkedList<LogicalDependency<T>> previousJustified;

    private LinkedList<LogicalDependency<SimpleMode>> previousBlocked;

    public TruthMaintenanceSystemKnowledgeHelper() { }

    public TruthMaintenanceSystemKnowledgeHelper(ReteEvaluator reteEvaluator) {
        super(reteEvaluator);
    }

    @Override
    public void setActivation(final InternalMatch internalMatch) {
        TruthMaintenanceSystemInternalMatch tmsActivation = (TruthMaintenanceSystemInternalMatch) internalMatch;
        this.previousJustified = tmsActivation.getLogicalDependencies();
        this.previousBlocked = tmsActivation.getBlocked();
        super.setActivation(internalMatch);
        tmsActivation.setLogicalDependencies( null );
        tmsActivation.setBlocked( null );
    }

    @Override
    public void restoreActivationOnConsequenceFailure(InternalMatch internalMatch) {
        TruthMaintenanceSystemInternalMatch tmsActivation = (TruthMaintenanceSystemInternalMatch) internalMatch;
        tmsActivation.setLogicalDependencies( this.previousJustified );
        tmsActivation.setBlocked( tmsActivation.getBlocked() );
    }

    @Override
    public void reset() {
        cancelRemainingPreviousLogicalDependencies();
        super.reset();
        this.previousJustified = null;
        this.previousBlocked = null;
    }

    public InternalFactHandle insertLogical(Object object, Object value) {
        return insertLogical(toStatefulKnowledgeSession().getDefaultEntryPoint(), object, value);
    }

    @Override
    public InternalFactHandle insertLogical(EntryPoint entryPoint, Object object) {
        return insertLogical(entryPoint, object, null);
    }

    public InternalFactHandle insertLogical(EntryPoint entryPoint, Object object, Object value) {
        if ( object == null ) {
            // prevent nulls from being inserted logically
            return null;
        }

        if ( !internalMatch.isMatched() ) {
            // Activation is already unmatched, can't do logical insertions against it
            return null;
        }
        // iterate to find previous equal logical insertion
        LogicalDependency<T> dep = null;
        if ( this.previousJustified != null ) {
            for ( dep = this.previousJustified.getFirst(); dep != null; dep = dep.getNext() ) {
                if ( object.equals( ((BeliefSet)dep.getJustified()).getFactHandle().getObject() ) ) {
                    this.previousJustified.remove( dep );
                    break;
                }
            }
        }

        if ( dep != null ) {
            // Add the previous matching logical dependency back into the list
            ((TruthMaintenanceSystemInternalMatch)this.internalMatch).addLogicalDependency(dep);
            return ( (BeliefSet) dep.getJustified() ).getFactHandle();
        } else {
            // no previous matching logical dependency, so create a new one
            return TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem((InternalWorkingMemoryEntryPoint) entryPoint).insert( object, value, this.internalMatch);
        }
    }

    public void cancelRemainingPreviousLogicalDependencies() {
        if ( this.previousJustified != null ) {
            for ( LogicalDependency<T> dep = this.previousJustified.getFirst(); dep != null; dep = dep.getNext() ) {
                TruthMaintenanceSystemImpl.removeLogicalDependency(dep, internalMatch.getPropagationContext());
            }
        }

        if ( this.previousBlocked != null ) {
            for (LogicalDependency<SimpleMode> dep = this.previousBlocked.getFirst(); dep != null; ) {
                LogicalDependency<SimpleMode> tmp = dep.getNext();
                this.previousBlocked.remove( dep );

                TruthMaintenanceSystemInternalMatch justified = (TruthMaintenanceSystemInternalMatch) dep.getJustified();
                justified.getBlockers().remove( dep.getMode());
                if (justified.getBlockers().isEmpty() ) {
                    RuleAgendaItem ruleAgendaItem = justified.getRuleAgendaItem();
                    toStatefulKnowledgeSession().getAgenda().stageLeftTuple(ruleAgendaItem, justified);
                }
                dep = tmp;
            }
        }
    }

    @Override
    public InternalFactHandle bolster( final Object object, final Object value ) {

        if ( object == null || ! internalMatch.isMatched() ) {
            return null;
        }

        InternalFactHandle handle = getFactHandleFromWM( object );
        NamedEntryPoint ep = (NamedEntryPoint) reteEvaluator.getEntryPoint( EntryPointId.DEFAULT.getEntryPointId() );
        ObjectTypeConf otc = ep.getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( ep.getEntryPoint(), object );

        BeliefSet beliefSet = null;
        if ( handle == null ) {
            handle = RuntimeComponentFactory.get().getFactHandleFactoryService().newFactHandle( object, otc, reteEvaluator, ep );
        }
        if ( handle.getEqualityKey() == null ) {
            handle.setEqualityKey( new TruthMaintenanceSystemEqualityKey( handle, EqualityKey.STATED ) );
        } else {
            beliefSet = ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).getBeliefSet();
        }

        BeliefSystem beliefSystem = value instanceof BeliefSystemMode ?
                ((BeliefSystemMode) value).getBeliefSystem() :
                ((TruthMaintenanceSystemImpl)TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem(toStatefulKnowledgeSession())).getBeliefSystem();
        if ( beliefSet == null ) {
            beliefSet = beliefSystem.newBeliefSet( handle );
            ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).setBeliefSet( beliefSet );
        }

        return beliefSystem.insert(beliefSystem.asMode( value ),
                                   internalMatch.getRule(),
                                   (TruthMaintenanceSystemInternalMatch) internalMatch,
                                   object,
                                   beliefSet,
                                   internalMatch.getPropagationContext(),
                                   otc ).getFactHandle();
    }

    @Override
    public void blockMatch(Match act) {
        TruthMaintenanceSystemRuleTerminalNodeLeftTuple targetMatch = ( TruthMaintenanceSystemRuleTerminalNodeLeftTuple ) act;
        // iterate to find previous equal logical insertion
        LogicalDependency<SimpleMode> dep = null;
        if ( this.previousBlocked != null ) {
            for ( dep = this.previousBlocked.getFirst(); dep != null; dep = dep.getNext() ) {
                if ( targetMatch ==  dep.getJustified() ) {
                    this.previousBlocked.remove( dep );
                    break;
                }
            }
        }

        if ( dep == null ) {
            SimpleMode mode = new SimpleMode();
            dep = new SimpleLogicalDependency((TruthMaintenanceSystemInternalMatch)this.internalMatch, targetMatch, mode );
            mode.setObject( dep );
        }
        ((TruthMaintenanceSystemInternalMatch)this.internalMatch).addBlocked(dep);

        if ( targetMatch.getBlockers().size() == 1 && targetMatch.isQueued()  ) {
            // it wasn't blocked before, but is now, so we must remove, so it cannot be executed.
            targetMatch.remove();

            if ( targetMatch.getActivationGroupNode() != null ) {
                targetMatch.getActivationGroupNode().getActivationGroup().removeActivation( targetMatch );
            }
        }
    }

    @Override
    public void unblockAllMatches(Match act) {
        TruthMaintenanceSystemRuleTerminalNodeLeftTuple targetMatch = ( TruthMaintenanceSystemRuleTerminalNodeLeftTuple ) act;
        boolean wasBlocked = (targetMatch.getBlockers() != null && !targetMatch.getBlockers().isEmpty() );

        for (LinkedListEntry entry = ( LinkedListEntry ) targetMatch.getBlockers().getFirst(); entry != null;  ) {
            LinkedListEntry tmp = ( LinkedListEntry ) entry.getNext();
            LogicalDependency dep = ( LogicalDependency ) entry.getObject();
            dep.getJustifier().removeBlocked( dep );
            entry = tmp;
        }

        if ( wasBlocked ) {
            RuleAgendaItem ruleAgendaItem = targetMatch.getRuleAgendaItem();
            InternalAgenda agenda = toStatefulKnowledgeSession().getAgenda();
            agenda.stageLeftTuple(ruleAgendaItem, targetMatch);
        }
    }
}
