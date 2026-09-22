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

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.accessor.DataProvider;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.util.index.TupleList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Targeted regression test for the guard in {@link PhreakFromNode#doLeftDeletes}. A left tuple
 * staged for delete has not necessarily been added to the node's left tuple memory: it may have
 * been staged and never actually added, or already removed. Removing it from the memory anyway
 * lets {@code LinkedList.remove()} fall into its middle-node branch and dereference a null
 * {@code previous} pointer. Every other Phreak node guards this call the same way.
 */
public class PhreakFromNodeDeleteTest {

    @Test
    public void doLeftDeletes_whenTupleWasNeverAddedToLeftMemory_doesNotNPE() {
        final TupleList leftTupleMemory = new TupleList();
        final FromNode.FromMemory fromMemory = newFromMemory(leftTupleMemory);

        // a tuple that reached the delete stage without ever being added to the left memory
        final LeftTuple leftTuple = new LeftTuple();
        assertThat(leftTuple.getMemory()).isNull();

        final TupleSets srcLeftTuples = new TupleSetsImpl();
        srcLeftTuples.addDelete(leftTuple);

        assertThatNoException().isThrownBy(() -> doLeftDeletes(fromMemory, srcLeftTuples));

        assertThat(leftTupleMemory.size()).isZero();
    }

    @Test
    public void doLeftDeletes_whenTupleWasAlreadyRemovedFromLeftMemory_doesNotNPE() {
        final TupleList leftTupleMemory = new TupleList();
        final FromNode.FromMemory fromMemory = newFromMemory(leftTupleMemory);

        final LeftTuple removed = new LeftTuple();
        final LeftTuple remaining = new LeftTuple();
        leftTupleMemory.add(removed);
        leftTupleMemory.add(remaining);

        // The first removal takes it out cleanly and clears its memory reference. After this,
        // removed.previous is null and the memory's first node is `remaining` - the exact state
        // the guard covers.
        leftTupleMemory.remove(removed);
        assertThat(removed.getMemory()).isNull();
        assertThat(removed.getPrevious()).isNull();
        assertThat(leftTupleMemory.getFirst()).isNotSameAs(removed);

        final TupleSets srcLeftTuples = new TupleSetsImpl();
        srcLeftTuples.addDelete(removed);

        assertThatNoException().isThrownBy(() -> doLeftDeletes(fromMemory, srcLeftTuples));

        // The memory is unchanged: `remaining` is still there.
        assertThat(leftTupleMemory.size()).isEqualTo(1);
        assertThat(leftTupleMemory.getFirst()).isSameAs(remaining);
    }

    @Test
    public void doLeftDeletes_whenTupleIsInLeftMemory_removesIt() {
        final TupleList leftTupleMemory = new TupleList();
        final FromNode.FromMemory fromMemory = newFromMemory(leftTupleMemory);

        final LeftTuple leftTuple = new LeftTuple();
        leftTupleMemory.add(leftTuple);
        assertThat(leftTupleMemory.size()).isEqualTo(1);

        final TupleSets srcLeftTuples = new TupleSetsImpl();
        srcLeftTuples.addDelete(leftTuple);

        doLeftDeletes(fromMemory, srcLeftTuples);

        assertThat(leftTupleMemory.size()).isZero();
        assertThat(leftTuple.getMemory()).isNull();
    }

    private static void doLeftDeletes(final FromNode.FromMemory fromMemory, final TupleSets srcLeftTuples) {
        // the ReteEvaluator is not used on the delete path
        new PhreakFromNode(null).doLeftDeletes(fromMemory, srcLeftTuples, new TupleSetsImpl(), new TupleSetsImpl());
    }

    private static FromNode.FromMemory newFromMemory(final TupleList leftTupleMemory) {
        final BetaMemory<Object> betaMemory = new BetaMemory<>(leftTupleMemory,
                                                               new TupleList(),
                                                               null,
                                                               NodeTypeEnums.FromNode);
        return new FromNode.FromMemory(betaMemory, mock(DataProvider.class));
    }
}
