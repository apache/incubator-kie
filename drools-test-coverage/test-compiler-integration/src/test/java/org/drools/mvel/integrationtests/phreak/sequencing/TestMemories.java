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
package org.drools.mvel.integrationtests.phreak.sequencing;

import org.drools.base.base.ClassObjectType;
import org.drools.base.reteoo.DynamicFilter;
import org.drools.base.reteoo.SignalAdapter;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.reteoo.sequencing.SequencerMemory;
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Pattern;
import org.drools.base.util.LinkedList;
import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.MockLeftTupleSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.SequenceNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.mvel.integrationtests.phreak.A;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.conf.ThreadSafeOption;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMemories extends AbstractPhreakSequencerSubsequenceTest {

    @Test
    public void testInitialisedMemoryBeforeStart() {
        initKBaseWithEmptyRule();
        buildContext = new BuildContext(kbase, Collections.emptyList() );
        buildContext.setRule(rule);

        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask), 0,
                                        new int[] {0, 1}, // B and C
                                        new int[] {0, 1}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);
        gate1.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(gate1);

        LogicGate gate2 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),1,
                                        new int[] {1, 2}, // C and D, C Filter is re-used
                                        new int[] {2, 3}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);
        gate2.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit2 = new LogicCircuit(gate2);

        seq0 = new Sequence(0, Step.of(circuit1), Step.of(circuit2));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        ObjectTypeNode aNode = kbase.getRete().getEntryPointNode(EntryPointId.DEFAULT).getObjectTypeNodes().get(new ClassObjectType(A.class));
        snode = (SequenceNode) aNode.getSinks()[0].getSinks()[0];

        SessionConfiguration sessionConf = kbase.getSessionConfiguration();
        sessionConf.setOption(ThreadSafeOption.NO);
        sessionConf.setClockType(ClockType.PSEUDO_CLOCK);

        session = (StatefulKnowledgeSessionImpl) kbase.newKieSession(sessionConf, null);

        InternalFactHandle fhA0 = (InternalFactHandle) session.insert(new A(0));
        nodeMemory = session.getNodeMemory(snode);
        LeftTuple lt = new LeftTuple(fhA0, snode, true);
        lt.setContextObject(snode.createSequencerMemory(lt, new MockLeftTupleSink(buildContext.getNextNodeId(), buildContext), nodeMemory));
        nodeMemory.getLeftTupleMemory().add(lt);
        sequencerMemory = (SequencerMemory) lt.getContextObject();

        SequenceMemory sequenceMemory = sequencerMemory.getOrCreateSequenceMemory(seq0, sequencerMemory.getData());
        sequencerMemory.setChildSequenceMemory(sequenceMemory);

        // make sure these are empty
        assertThat(nodeMemory.getFilters()).usingRecursiveComparison().isEqualTo(new DynamicFilter[3]);
        assertThat(nodeMemory.getActiveFilters()).usingRecursiveComparison().isEqualTo(new LinkedList[3]);
        assertThat(nodeMemory.getActiveFilters()).usingRecursiveComparison().isEqualTo(new LinkedList[3]);
        assertThat(sequenceMemory.getActiveSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[4]);
        assertThat(sequenceMemory.getSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[4]);
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0);
    }

}
