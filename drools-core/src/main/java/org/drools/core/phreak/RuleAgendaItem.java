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
package org.drools.core.phreak;

import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.base.util.LinkedList;
import org.drools.base.util.DoubleLinkedEntry;
import org.drools.core.util.Queue.QueueEntry;

public class RuleAgendaItem implements DoubleLinkedEntry<RuleAgendaItem>, QueueEntry {

    private transient RuleExecutor executor;
    private RuleAgendaItem previous;
    private RuleAgendaItem next;
    private PathMemory pmem;
    private boolean declarativeAgendaEnabled;

    private int salience;

    private TerminalNode rtn;

    private int index;
    private boolean queued;
    private transient InternalAgendaGroup agendaGroup;

    public RuleAgendaItem() {

    }

    public RuleAgendaItem(final int salience,
                          final PathMemory pmem,
                          final TerminalNode rtn,
                          boolean declarativeAgendaEnabled,
                          InternalAgendaGroup agendaGroup) {
        this.pmem = pmem;
        this.declarativeAgendaEnabled = declarativeAgendaEnabled;
        this.salience = salience;
        this.rtn = rtn;
        this.index = -1;
        this.agendaGroup = agendaGroup;
    }

    public RuleExecutor getRuleExecutor() {
        if (executor == null) {
            executor = new RuleExecutor(pmem, this, declarativeAgendaEnabled);
        }
        return executor;
    }

    public RuleAgendaItem getPrevious() {
        return previous;
    }

    public void setPrevious(RuleAgendaItem previous) {
        this.previous = previous;
    }

    public RuleAgendaItem getNext() {
        return next;
    }

    public void setNext(RuleAgendaItem next) {
        this.next = next;
    }

    public boolean isInList( LinkedList<RuleAgendaItem> list ) {
        return previous != null || next != null || list.getFirst() == this;
    }

    public void clear() {
        previous = null;
        next = null;
    }

    public RuleBasePartitionId getPartition() {
        return this.rtn.getPartitionId();
    }

    /**
     * Retrieve the rule.
     *
     * @return The rule.
     */
    public RuleImpl getRule() {
        return this.rtn.getRule();
    }

    public TerminalNode getTerminalNode() {
        return this.rtn;
    }

    public int getSalience() {
        return this.salience;
    }

    public void setSalience(int salience) {
        this.salience = salience;
    }

    @Override
    public boolean isQueued() {
        return this.queued;
    }

    @Override
    public void setQueued(final boolean queued) {
        this.queued = queued;
    }

    @Override
    public void setQueueIndex(final int index) {
        this.index = index;
    }

    @Override
    public int getQueueIndex() {
        return this.index;
    }

    @Override
    public void dequeue() {
        if (this.agendaGroup != null) {
            this.agendaGroup.remove(this);
        }
        this.queued = false;
    }

    @Override
    public String toString() {
        return "[Activation rule=" + this.rtn.getRule().getName() + ",  salience=" + this.salience + "]";
    }

    public void remove() {
        dequeue();
    }

    public InternalAgendaGroup getAgendaGroup() {
        return this.agendaGroup;
    }

    public boolean isRuleInUse() {
        return rtn.getLeftTupleSource() != null;
    }
}
