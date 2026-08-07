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
package org.drools.kiesession;

import java.util.List;

import org.drools.core.common.ActivationGroupNode;
import org.drools.core.common.ActivationNode;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalRuleFlowGroup;
import org.drools.core.common.PhreakPropagationContext;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.rule.consequence.Consequence;
import org.drools.core.common.PropagationContext;
import org.drools.base.util.LinkedList;
import org.kie.api.runtime.rule.FactHandle;

public class MockInternalMatch implements InternalMatch {

    private RuleImpl rule;

    public MockInternalMatch() {
        this.rule = new RuleImpl();
    }

    public RuleImpl getRule() {
        return this.rule;
    }

    public Consequence getConsequence() {
        return getRule().getConsequence();
    }

    public int getSalience() {
        return 0;
    }

    public TupleImpl getTuple() {
        return null;
    }

    public PropagationContext getPropagationContext() {
        return new PhreakPropagationContext();
    }

    public long getActivationNumber() {
        return 0;
    }

    public void remove() {
    }

    public boolean isQueued() {
        return false;
    }

    public void setQueued(final boolean activated) {
    }

    public ActivationGroupNode getActivationGroupNode() {
        return null;
    }

    public void setActivationGroupNode( final ActivationGroupNode activationGroupNode ) {
    }

    public InternalAgendaGroup getAgendaGroup() {
        return null;
    }

    public InternalRuleFlowGroup getRuleFlowGroup() {
        return null;
    }

    public ActivationNode getActivationNode() {
        return null;
    }

    public void setActivationNode( final ActivationNode ruleFlowGroupNode ) {
    }
    public List<FactHandle> getFactHandles() {
        return null;
    }

    public List<Object> getObjects() {
        return null;
    }

    public Object getDeclarationValue( String variableName ) {
        return null;
    }

    public List<String> getDeclarationIds() {
        return null;
    }

    public InternalFactHandle getActivationFactHandle() {
        return null;
    }

    public boolean isAdded() {
        return false;
    }

    public LinkedList getBlocked() {
        return null;
    }

    public boolean isMatched() {
        return false;
    }

    public void setMatched(boolean matched) { }

    public boolean isActive() {
        return false;
    }

    public void setActive(boolean active) { }

    public boolean isRuleAgendaItem() {
        return false;
    }

    @Override
    public void dequeue() {
    }

    @Override
    public int getQueueIndex() {
        return 0;
    }

    @Override
    public void setQueueIndex(int index) {

    }

    @Override
    public RuleAgendaItem getRuleAgendaItem() {
        return null;
    }

    @Override
    public void setActivationFactHandle(InternalFactHandle factHandle) {

    }

    @Override
    public TerminalNode getTerminalNode() {
        return null;
    }

    @Override
    public String toExternalForm() {
        return null;
    }

    @Override
    public Runnable getCallback() {
        return null;
    }

    @Override
    public void setCallback(Runnable callback) {

    }
}
