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
package org.drools.tms.agenda;

import org.drools.core.common.ActivationsManager;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.Sink;
import org.drools.core.common.PropagationContext;
import org.drools.core.reteoo.TupleImpl;
import org.drools.base.util.LinkedList;
import org.drools.tms.LogicalDependency;
import org.drools.tms.SimpleMode;
import org.drools.tms.TruthMaintenanceSystemImpl;
import org.drools.tms.beliefsystem.ModedAssertion;

public class TruthMaintenanceSystemRuleTerminalNodeLeftTuple<T extends ModedAssertion<T>> extends RuleTerminalNodeLeftTuple implements TruthMaintenanceSystemInternalMatch<T> {

    private LinkedList<SimpleMode> blockers;
    private LinkedList<LogicalDependency<T>> justified;
    private LinkedList<LogicalDependency<SimpleMode>> blocked;

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple() {
    }

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple(InternalFactHandle factHandle, Sink sink, boolean leftTupleMemoryEnabled) {
        super(factHandle, sink, leftTupleMemoryEnabled);
    }

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple(InternalFactHandle factHandle, TupleImpl leftTuple, Sink sink) {
        super(factHandle, leftTuple, sink);
    }

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple(TupleImpl leftTuple, Sink sink, PropagationContext pctx, boolean leftTupleMemoryEnabled) {
        super(leftTuple, sink, pctx, leftTupleMemoryEnabled);
    }

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple(TupleImpl leftTuple, TupleImpl rightTuple, Sink sink) {
        super(leftTuple, rightTuple, sink);
    }

    public TruthMaintenanceSystemRuleTerminalNodeLeftTuple(TupleImpl leftTuple, TupleImpl rightTuple, TupleImpl currentLeftChild, TupleImpl currentRightChild, Sink sink, boolean leftTupleMemoryEnabled) {
        super(leftTuple, rightTuple, currentLeftChild, currentRightChild, sink, leftTupleMemoryEnabled);
    }

    @Override
    public LinkedList<SimpleMode> getBlockers() {
        return this.blockers;
    }

    @Override
    public boolean hasBlockers() {
        return blockers != null && !blockers.isEmpty();
    }

    @Override
    public void addBlocked(final LogicalDependency<SimpleMode> dep) {
        // Adds the blocked to the blockers list
        if (this.blocked == null) {
            this.blocked = new LinkedList<>();
        }

        this.blocked.add(dep);

        // now ad the blocker to the blocked's list - we need to check that references are null first
        TruthMaintenanceSystemRuleTerminalNodeLeftTuple blocked = (TruthMaintenanceSystemRuleTerminalNodeLeftTuple) dep.getJustified();
        if (blocked.blockers == null) {
            blocked.blockers = new LinkedList<>();
            blocked.blockers.add(dep.getMode());
        } else if (dep.getMode().getNext() == null && dep.getMode().getPrevious() == null && blocked.getBlockers().getFirst() != dep.getMode()) {
            blocked.blockers.add(dep.getMode());
        }
    }

    @Override
    public void removeAllBlockersAndBlocked(ActivationsManager activationsManager) {
        if (this.blockers != null) {
            // Iterate and remove this node's logical dependency list from each of it's blockers
            for (SimpleMode node = blockers.getFirst(); node != null; node = node.getNext()) {
                LogicalDependency dep = node.getObject();
                dep.getJustifier().getBlocked().remove(dep);
            }
        }
        this.blockers = null;

        if (this.blocked != null) {
            // Iterate and remove this node's logical dependency list from each of it's blocked
            for (LogicalDependency<SimpleMode> dep = blocked.getFirst(); dep != null; ) {
                LogicalDependency<SimpleMode> tmp = dep.getNext();
                removeBlocked(dep);
                TruthMaintenanceSystemRuleTerminalNodeLeftTuple justified = (TruthMaintenanceSystemRuleTerminalNodeLeftTuple) dep.getJustified();
                if (justified.getBlockers().isEmpty() && justified.isActive()) {
                    activationsManager.stageLeftTuple(justified.getRuleAgendaItem(), justified);
                }
                dep = tmp;
            }
        }
        this.blocked = null;
    }

    @Override
    public void removeBlocked(final LogicalDependency<SimpleMode> dep) {
        this.blocked.remove(dep);

        TruthMaintenanceSystemRuleTerminalNodeLeftTuple blocked = (TruthMaintenanceSystemRuleTerminalNodeLeftTuple) dep.getJustified();
        blocked.blockers.remove(dep.getMode());
    }

    @Override
    public LinkedList<LogicalDependency<SimpleMode>> getBlocked() {
        return this.blocked;
    }

    @Override
    public void setBlocked(LinkedList<LogicalDependency<SimpleMode>> justified) {
        this.blocked = justified;
    }

    @Override
    public void addLogicalDependency(final LogicalDependency<T> node) {
        if (this.justified == null) {
            this.justified = new LinkedList<>();
        }

        this.justified.add(node);
    }

    @Override
    public LinkedList<LogicalDependency<T>> getLogicalDependencies() {
        return this.justified;
    }

    @Override
    public void setLogicalDependencies(LinkedList<LogicalDependency<T>> justified) {
        this.justified = justified;
    }

    @Override
    public void cancelActivation(ActivationsManager activationsManager) {
        removeAllBlockersAndBlocked( activationsManager );
        super.cancelActivation( activationsManager );
        TruthMaintenanceSystemImpl.removeLogicalDependencies( this );
    }
}
