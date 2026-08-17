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

import java.util.List;

import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode.QueryElementNodeMemory;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.base.util.LinkedList;

/**
* Created with IntelliJ IDEA.
* User: mdproctor
* Date: 03/05/2013
* Time: 15:42
* To change this template use File | Settings | File Templates.
*/
public class PhreakQueryTerminalNode {
    
    private final ReteEvaluator reteEvaluator;

    public PhreakQueryTerminalNode(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
    }

    public void doNode(LinkedList<StackEntry> stack,
                       QueryTerminalNode qtnNode,
                       TupleSets srcLeftTuples) {
        if (srcLeftTuples.getDeleteFirst() != null) {
            doLeftDeletes(qtnNode, srcLeftTuples, stack);
        }

        if (srcLeftTuples.getUpdateFirst() != null) {
            doLeftUpdates(qtnNode, srcLeftTuples, stack);
        }

        if (srcLeftTuples.getInsertFirst() != null) {
            doLeftInserts(qtnNode, srcLeftTuples, stack);
        }

        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(QueryTerminalNode qtnNode,
                              TupleSets srcLeftTuples,
                              LinkedList<StackEntry> stack) {

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            //qtnNode.assertLeftTuple( leftTuple, leftTuple.getPropagationContext(), wm );

            // find the DroolsQuery object
            Tuple rootEntry = leftTuple.getRootTuple();

            DroolsQueryImpl dquery = (DroolsQueryImpl) rootEntry.getFactHandle().getObject();
            dquery.setQuery(qtnNode.getQuery());
            if (dquery.getStackEntry() != null) {
                checkAndTriggerQueryReevaluation(stack, rootEntry, dquery);
            }

            // Add results to the adapter
            dquery.getQueryResultCollector().rowAdded(qtnNode.getQuery(),
                                                      leftTuple,
                                                      reteEvaluator);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doLeftUpdates(QueryTerminalNode qtnNode,
                              TupleSets srcLeftTuples,
                              LinkedList<StackEntry> stack) {

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            // qtnNode.modifyLeftTuple( leftTuple, leftTuple.getPropagationContext(), wm );
            // find the DroolsQuery object
            TupleImpl rootEntry = leftTuple.getRootTuple();

            DroolsQueryImpl dquery = (DroolsQueryImpl) rootEntry.getFactHandle().getObject();
            dquery.setQuery(qtnNode.getQuery());
            if (dquery.getStackEntry() != null) {
                checkAndTriggerQueryReevaluation(stack, rootEntry, dquery);
            }

            // Add results to the adapter
            dquery.getQueryResultCollector().rowUpdated(qtnNode.getQuery(),
                                                        leftTuple,
                                                        reteEvaluator);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doLeftDeletes(QueryTerminalNode qtnNode,
                              TupleSets srcLeftTuples,
                              LinkedList<StackEntry> stack) {

        for (TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            //qtnNode.retractLeftTuple( leftTuple, leftTuple.getPropagationContext(), wm );

            // find the DroolsQuery object
            TupleImpl rootEntry = leftTuple.getRootTuple();

            DroolsQueryImpl dquery = (DroolsQueryImpl) rootEntry.getFactHandle().getObject();
            dquery.setQuery(qtnNode.getQuery());

            if (dquery.getStackEntry() != null) {
                checkAndTriggerQueryReevaluation(stack, rootEntry, dquery);
            }

            // Add results to the adapter
            dquery.getQueryResultCollector().rowRemoved(qtnNode.getQuery(),
                                                        leftTuple,
                                                        reteEvaluator);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }


    public static void checkAndTriggerQueryReevaluation(LinkedList<StackEntry> stack, Tuple rootEntry, DroolsQueryImpl dquery) {
        StackEntry stackEntry = dquery.getStackEntry();
        if (!isAdded(stack, stackEntry)) {
            // Ignore unless stackEntry is not added to stack

            // node must be marked as dirty
            ((QueryElementNodeMemory)stackEntry.getNodeMem()).setNodeDirtyWithoutNotify();

            if (stackEntry.getRmem().getPathEndNode().getPathNodes()[0] == ((LeftTupleSink)rootEntry.getSink()).getLeftTupleSource()) {
                // query is recursive, so just re-add the stack entry to the current stack. This happens for reactive queries, triggered by a beta node right input
                stack.add(stackEntry);
            } else {
                // parents is anther rule/query need to notify for agenda to schedule. query is reactive, triggered by right input,
                List<PathMemory> pmems = dquery.getRuleMemories();
                if (pmems != null) {
                    // StackEntry is null, when query is called directly from java

                    // reactivity comes form within the query, so need to notify parent rules to evaluate the results
                    for (int i = 0, length = pmems.size(); i < length; i++) {
                        PathMemory pmem = pmems.get(i);
                        pmem.doLinkRule(); // method already ignores is rule is activated and on agenda
                    }
                }
            }
        }
    }

    public static boolean isAdded(LinkedList<StackEntry> stack, StackEntry stackEntry) {
        return stackEntry == null || stackEntry.getPrevious() != null || stackEntry.getNext() != null || stack.getFirst() == stackEntry;
    }
}
