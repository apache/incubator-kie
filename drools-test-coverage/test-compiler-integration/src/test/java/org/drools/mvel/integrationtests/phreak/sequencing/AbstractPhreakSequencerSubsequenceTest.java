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
import org.drools.base.base.ObjectType;
import org.drools.base.base.ValueResolver;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.consequence.Consequence;
import org.drools.base.rule.consequence.ConsequenceContext;
import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.SequenceNode;
import org.drools.core.reteoo.SequenceNode.SequenceNodeMemory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.SequencerMemory;
import org.drools.core.rule.JavaDialectRuntimeData;
import org.drools.kiesession.rulebase.SessionsAwareKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.mvel.integrationtests.phreak.A;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.conf.ThreadSafeOption;
import org.kie.internal.conf.CompositeBaseConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AbstractPhreakSequencerSubsequenceTest {
    StatefulKnowledgeSessionImpl session;
    SequenceNodeMemory nodeMemory;
    PathMemory         pmem;
    SequencerMemory    sequencerMemory;
    InternalFactHandle fhA0;
    BuildContext       buildContext;
    Sequence           seq0;
    SequenceNode       snode;

    Pattern bpattern;
    Pattern cpattern;
    Pattern dpattern;
    Pattern epattern;

    RuleImpl                   rule;
    InternalKnowledgePackage   pkg;
    SessionsAwareKnowledgeBase kbase;

    public void initKBaseWithEmptyRule() {
        CompositeBaseConfiguration conf = (CompositeBaseConfiguration) RuleBaseFactory.newKnowledgeBaseConfiguration();
        conf.setOption(EventProcessingOption.STREAM);

        KnowledgeBaseImpl rbase = new KnowledgeBaseImpl("ID", conf );

        kbase = new SessionsAwareKnowledgeBase(rbase);

        rule = new RuleImpl("rule1").setPackage("org.pkg1");
        rule.setConsequence(new Consequence() {
            @Override
            public String getName() {
                return "r1";
            }

            @Override
            public void evaluate(ConsequenceContext knowledgeHelper, ValueResolver valueResolver) throws Exception {
            }
        });
        pkg  = CoreComponentFactory.get().createKnowledgePackage("org.pkg1");
        pkg.getDialectRuntimeRegistry().setDialectData( "java", new JavaDialectRuntimeData());
        pkg.addRule( rule );

        final ObjectType aObjectType = new ClassObjectType(A.class);
        final ObjectType bObjectType = new ClassObjectType(BEvent.class);
        final ObjectType cObjectType = new ClassObjectType(CEvent.class);
        final ObjectType dObjectType = new ClassObjectType(DEvent.class);
        final ObjectType eObjectType = new ClassObjectType(EEvent.class);

        Pattern aPattern = new Pattern(0, aObjectType);
        rule.addPattern(aPattern);

        bpattern = new Pattern(0, bObjectType, "b" );
        bpattern.addConstraint(new AlphaConstraint((Predicate1<BEvent>) b -> b.getText().equals("b")));

        cpattern = new Pattern(0, cObjectType, "c" );
        cpattern.addConstraint(new AlphaConstraint( (Predicate1<CEvent>) c -> c.getText().equals("c")));

        dpattern = new Pattern(0, dObjectType, "d" );
        dpattern.addConstraint(new AlphaConstraint( (Predicate1<DEvent>) d -> d.getText().equals("d")));

        epattern = new Pattern(0, eObjectType, "e" );
        epattern.addConstraint(new AlphaConstraint( (Predicate1<EEvent>) e -> e.getText().equals("e")));
    }

    public static BuildContext createContext() {

        CompositeBaseConfiguration conf = (CompositeBaseConfiguration) RuleBaseFactory.newKnowledgeBaseConfiguration();

        KnowledgeBaseImpl rbase = new KnowledgeBaseImpl("ID",
                                                        conf );
        BuildContext buildContext = new BuildContext(rbase, Collections.emptyList() );

        RuleImpl                 rule = new RuleImpl("rule1").setPackage("org.pkg1");
        InternalKnowledgePackage pkg  = CoreComponentFactory.get().createKnowledgePackage("org.pkg1");
        pkg.getDialectRuntimeRegistry().setDialectData( "java", new JavaDialectRuntimeData());

        pkg.addRule( rule );
        buildContext.setRule( rule );

        return buildContext;
    }

    void createSession() {
        SessionConfiguration sessionConf = kbase.getSessionConfiguration();
        sessionConf.setOption(ThreadSafeOption.NO);
        sessionConf.setClockType(ClockType.PSEUDO_CLOCK);

        if (snode == null) {
            ObjectTypeNode aNode = kbase.getRete().getEntryPointNode(EntryPointId.DEFAULT).getObjectTypeNodes().get(new ClassObjectType(A.class));
            snode = (SequenceNode) aNode.getSinks()[0].getSinks()[0];
        }

        if (session != null) {
            nodeMemory      = null;
            sequencerMemory = null;
            session.dispose();
            session = null;
        }

        session = (StatefulKnowledgeSessionImpl) kbase.newKieSession(sessionConf, null);

        fhA0 = (InternalFactHandle) session.insert(new A(0));
        session.fireAllRules();
        nodeMemory = session.getNodeMemory(snode);
        pmem = nodeMemory.getSegmentMemory().getPathMemories().get(0);
        sequencerMemory = (SequencerMemory) fhA0.getFirstLeftTuple().getContextObject();
    }

    public int getCurrentStep(SequencerMemory sqncrMemory) {
        SequenceMemory sqncMemory = sqncrMemory.getChildSequenceMemory();

        List<SequenceMemory> leafSequences = new ArrayList<>();
        getLeafSequences(sqncrMemory, sqncMemory, leafSequences);

        if (leafSequences.isEmpty()) {
            return -1;
        }
        return leafSequences.get(0).getStep();
    }

    public void getLeafSequences(SequencerMemory sqncrMemory, SequenceMemory sqncMemory, List<SequenceMemory> leafSequences) {
        Sequence sqnc = sqncMemory.getSequence();
        if (sqncMemory.getStep() >= sqnc.getSteps().length ) {
            return;
        }
        leafSequences.add(sqncMemory);
    }
}
