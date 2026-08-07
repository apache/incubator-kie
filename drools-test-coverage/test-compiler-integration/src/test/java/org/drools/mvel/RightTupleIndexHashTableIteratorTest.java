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
package org.drools.mvel;

import java.util.ArrayList;
import java.util.List;

import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.SingleBetaConstraints;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.LeftTuple;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.MockRightTupleSink;
import org.drools.core.reteoo.RightTuple;
import org.drools.base.rule.constraint.BetaConstraint;
import org.drools.core.reteoo.TupleImpl;
import org.drools.base.util.FastIterator;
import org.drools.core.util.index.TupleIndexHashTable;
import org.drools.core.util.index.TupleIndexHashTable.FieldIndexHashTableFullIterator;
import org.drools.core.util.index.TupleList;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class RightTupleIndexHashTableIteratorTest extends AbstractTupleIndexHashTableIteratorTest {

    @ParameterizedTest(name = "useLambdaConstraint={0}")
    @MethodSource("parameters")
    public void test1(boolean useLambdaConstraint) {
        BetaConstraint constraint0 = createFooThisEqualsDBetaConstraint(useLambdaConstraint);

        BetaConstraint[] constraints = new BetaConstraint[]{constraint0};

        RuleBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration().as(RuleBaseConfiguration.KEY);

        BetaConstraints betaConstraints = null;

        betaConstraints = new SingleBetaConstraints(constraints, config);

        BetaMemory betaMemory = betaConstraints.createBetaMemory(config, NodeTypeEnums.JoinNode);

        KieBase kBase = KnowledgeBaseFactory.newKnowledgeBase();
        KieSession ss = kBase.newKieSession();

        InternalFactHandle fh1 = (InternalFactHandle) ss.insert(new Foo("brie", 1));
        InternalFactHandle fh2 = (InternalFactHandle) ss.insert(new Foo("brie", 1));
        InternalFactHandle fh3 = (InternalFactHandle) ss.insert(new Foo("soda", 1));
        InternalFactHandle fh4 = (InternalFactHandle) ss.insert(new Foo("soda", 1));
        InternalFactHandle fh5 = (InternalFactHandle) ss.insert(new Foo("bread", 3));
        InternalFactHandle fh6 = (InternalFactHandle) ss.insert(new Foo("bread", 3));
        InternalFactHandle fh7 = (InternalFactHandle) ss.insert(new Foo("cream", 3));
        InternalFactHandle fh8 = (InternalFactHandle) ss.insert(new Foo("gorda", 15));
        InternalFactHandle fh9 = (InternalFactHandle) ss.insert(new Foo("beer", 16));

        InternalFactHandle fh10 = (InternalFactHandle) ss.insert(new Foo("mars", 0));
        InternalFactHandle fh11 = (InternalFactHandle) ss.insert(new Foo("snicker", 0));
        InternalFactHandle fh12 = (InternalFactHandle) ss.insert(new Foo("snicker", 0));
        InternalFactHandle fh13 = (InternalFactHandle) ss.insert(new Foo("snicker", 0));

        betaMemory.getRightTupleMemory().add(new RightTuple(fh1, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh2, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh3, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh4, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh5, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh6, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh7, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh8, new MockRightTupleSink()));
        betaMemory.getRightTupleMemory().add(new RightTuple(fh9, new MockRightTupleSink()));

        TupleIndexHashTable hashTable = (TupleIndexHashTable) betaMemory.getRightTupleMemory();
        // can't create a 0 hashCode, so forcing 
        TupleList rightTupleList = new TupleList();
        rightTupleList.add(new RightTuple(fh10, null));
        hashTable.getTable()[0] = rightTupleList;
        rightTupleList = new TupleList();
        rightTupleList.add(new RightTuple(fh11, null));
        rightTupleList.add(new RightTuple(fh12, null));
        rightTupleList.add(new RightTuple(fh13, null));
        hashTable.getTable()[0].setNext(rightTupleList);

        List tableIndexList = createTableIndexListForAssertion(hashTable);
        assertThat(tableIndexList.size()).isEqualTo(5);

        List resultList = new ArrayList<LeftTuple>();
        FastIterator<TupleImpl> it = betaMemory.getRightTupleMemory().fullFastIterator();
        for (TupleImpl rightTuple = it.next(null); rightTuple != null; rightTuple =  it.next(rightTuple)) {
            resultList.add(rightTuple);
        }

        assertThat(resultList.size()).isEqualTo(13);
    }

    @ParameterizedTest(name = "useLambdaConstraint={0}")
    @MethodSource("parameters")
    public void testLastBucketInTheTable(boolean useLambdaConstraint) {
        // JBRULES-2574
        // setup the entry array with an element in the first bucket, one 
        // in the middle and one in the last bucket
        TupleIndexHashTable hashIndex = new TupleIndexHashTable();
        TupleList[] entries = hashIndex.getTable();
        entries[0] = new TupleList();
        entries[5] = new TupleList();
        entries[9] = new TupleList();

        RightTuple[] tuples = new RightTuple[]{new RightTuple(), new RightTuple(), new RightTuple()};

        // set return values for methods
        entries[0].addFirst(tuples[0]);
        entries[5].addFirst(tuples[1]);
        entries[9].addFirst(tuples[2]);

        // create the iterator
        FieldIndexHashTableFullIterator iterator = new FieldIndexHashTableFullIterator(hashIndex);

        // test it
        assertThat(iterator.next()).isSameAs(tuples[0]);
        assertThat(iterator.next()).isSameAs(tuples[1]);
        assertThat(iterator.next()).isSameAs(tuples[2]);
        assertThat(iterator.next()).isNull();
    }
}
