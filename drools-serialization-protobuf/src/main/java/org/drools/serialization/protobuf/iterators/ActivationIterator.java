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
package org.drools.serialization.protobuf.iterators;

import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.reteoo.Tuple;
import org.drools.base.util.Iterator;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

public class ActivationIterator
    implements
    Iterator<InternalMatch> {
    private InternalWorkingMemory wm;

    private Iterator<InternalMatch> nodeIter;

    private TerminalNode          node;
    
    private Iterator<TupleImpl> leftTupleIter;

    private Tuple                 currentTuple;

    ActivationIterator() {

    }

    private ActivationIterator(InternalWorkingMemory wm,
                               KieBase kbase) {
        this.wm = wm;

        nodeIter = TerminalNodeIterator.iterator( kbase );

        // Find the first node with Activations an set it.
        while ( currentTuple == null && (node = (TerminalNode) nodeIter.next()) != null ) {
            if ( !(node instanceof RuleTerminalNode) ) {
                continue;
            }
            leftTupleIter = LeftTupleIterator.iterator( wm, node );
            this.currentTuple = leftTupleIter.next();
        }
    }

    public static Iterator<InternalMatch> iterator(InternalWorkingMemory wm) {
        return PhreakActivationIterator.iterator(wm);
    }

    public static Iterator<InternalMatch> iterator(KieSession ksession) {
        return iterator((WorkingMemoryEntryPoint) ksession);
    }

    public static Iterator<InternalMatch> iterator(WorkingMemoryEntryPoint ksession ) {
        ReteEvaluator reteEvaluator = ksession.getReteEvaluator();
        return PhreakActivationIterator.iterator(reteEvaluator);
    }

    public InternalMatch next() {
        InternalMatch acc = null;
        if ( this.currentTuple != null ) {
            Object obj = currentTuple.getContextObject();
            acc = obj == Boolean.TRUE ? null : (InternalMatch)obj;
            currentTuple = leftTupleIter.next();

            while ( currentTuple == null && (node = (TerminalNode) nodeIter.next()) != null ) {
                if ( !(node instanceof RuleTerminalNode) ) {
                    continue;
                }                    
                leftTupleIter = LeftTupleIterator.iterator( wm, node );            
                this.currentTuple = leftTupleIter.next();
            }
        }

        return acc;
    }

}
