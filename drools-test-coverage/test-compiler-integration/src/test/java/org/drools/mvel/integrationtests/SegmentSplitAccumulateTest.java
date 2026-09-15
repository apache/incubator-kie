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
package org.drools.mvel.integrationtests;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.testcoverage.common.model.A;
import org.drools.testcoverage.common.model.B;
import org.drools.testcoverage.common.model.C;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule whose path contains an AccumulateNode stops firing when its segment is split by a rule that is
 * added later and shares the same LeftInputAdapterNode.
 *
 * <p>SegmentPrototype.splitProtos() renumbers the node position bits of the second half of a split segment
 * by calling MemoryPrototype.setNodePosMaskBit() on each memory prototype. AccumulateMemoryPrototype holds
 * the bit in a wrapped BetaMemoryPrototype and its populateMemory() copies that wrapped bit into the
 * BetaMemory, so a renumbering applied to the wrapper alone never reaches the accumulate node and the node
 * is created with its pre-split bit.</p>
 *
 * <p>At runtime the accumulate node then links and dirties the bit of the node that follows it, and
 * SegmentCursor.moveToNextAvailableSegment() skips nodes whose dirty bit is clear, so the staged right
 * tuples of the accumulate node are never processed and the rule never fires.</p>
 *
 * <p>Two packages are used because KnowledgeBaseImpl.addPackages() sorts packages by rule count descending
 * and adds them one at a time: the first package gets its segment prototypes created in bulk once all of
 * its rules are attached, while every later package is added rule by rule through
 * ReteooRuleBuilder.attachTerminalNode() and PhreakBuilder.addRule(), which is the only path that splits an
 * existing segment.</p>
 */
public class SegmentSplitAccumulateTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    private static final String HEADER =
            "package org.drools.mvel.integrationtests.segmentsplit;\n" +
            "import " + A.class.getCanonicalName() + ";\n" +
            "import " + B.class.getCanonicalName() + ";\n" +
            "import " + C.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n";

    private static final String RULE_ACCUMULATE_FROM =
            "rule \"R1 accumulate then from\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  accumulate( $b : B( value == $v ); $list : collectList( $b ); $list.size > 0 )\n" +
            "  B( $r : value ) from $list.get(0)\n" +
            "then\n" +
            "  results.add( \"R1:\" + $r );\n" +
            "end\n";

    private static final String RULE_ACCUMULATE_ONLY =
            "rule \"R1 accumulate\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  accumulate( B( value == $v ); $n : count(); $n > 0 )\n" +
            "then\n" +
            "  results.add( \"R1:\" + $n );\n" +
            "end\n";

    private static final String RULE_JOIN =
            "rule \"R1 join\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  B( value == $v, $r : value )\n" +
            "then\n" +
            "  results.add( \"R1:\" + $r );\n" +
            "end\n";

    private static final String RULE_SHARING_LIA =
            "rule \"R2 shares the A pattern\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  C( value == $v )\n" +
            "then\n" +
            "  results.add( \"R2:\" + $v );\n" +
            "end\n";

    /** A second package with more rules, so that it is built first and the package under test is split. */
    private static final String BIGGER_PACKAGE =
            "package org.drools.mvel.integrationtests.segmentsplit.other;\n" +
            "import " + C.class.getCanonicalName() + ";\n" +
            "rule \"O1\" when C( value == 101 ) then end\n" +
            "rule \"O2\" when C( value == 102 ) then end\n" +
            "rule \"O3\" when C( value == 103 ) then end\n";

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateAndFromRuleBuiltBeforeSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertRuleFiresWhenRightFactArrivesLater(kieBaseTestConfiguration,
                                                 HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA, "R1:1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOnlyRuleBuiltBeforeSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertRuleFiresWhenRightFactArrivesLater(kieBaseTestConfiguration,
                                                 HEADER + RULE_ACCUMULATE_ONLY + RULE_SHARING_LIA, "R1:1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateAndFromRuleBuiltAfterSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertRuleFiresWhenRightFactArrivesLater(kieBaseTestConfiguration,
                                                 HEADER + RULE_SHARING_LIA + RULE_ACCUMULATE_FROM, "R1:1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOnlyRuleBuiltAfterSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertRuleFiresWhenRightFactArrivesLater(kieBaseTestConfiguration,
                                                 HEADER + RULE_SHARING_LIA + RULE_ACCUMULATE_ONLY, "R1:1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void joinRuleBuiltBeforeSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertRuleFiresWhenRightFactArrivesLater(kieBaseTestConfiguration,
                                                 HEADER + RULE_JOIN + RULE_SHARING_LIA, "R1:1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateRuleWithAllFactsInsertedBeforeFirstFire(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA);
        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);
            ksession.insert(new A(1));
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).containsExactly("R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * A single package has all of its rules attached before its segment prototypes are created in bulk, so
     * no segment is ever split and the accumulate node keeps the bit it was built with.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateRuleBeforeSharingRuleInASinglePackage(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("segment-split-single", kieBaseTestConfiguration,
                                                                   HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA);
        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);
            ksession.insert(new A(1));
            ksession.fireAllRules();
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).containsExactly("R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * The accumulate node is the root of the segment split off by the rule sharing the LeftInputAdapterNode,
     * so its position bit within that segment has to be the first one.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateNodeKeepsItsPositionBitAfterSegmentSplit(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA);

        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class);
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        AccumulateNode accNode = null;
        for (LeftTupleSink sink : liaNode.getSinkPropagator().getSinks()) {
            if (sink instanceof AccumulateNode) {
                accNode = (AccumulateNode) sink;
            }
        }
        assertThat(accNode).as("the accumulate node shares the LeftInputAdapterNode").isNotNull();

        KieSession ksession = kbase.newKieSession();
        try {
            ksession.setGlobal("results", new ArrayList<String>());
            ksession.insert(new A(1));
            ksession.fireAllRules();

            InternalWorkingMemory wm = (InternalWorkingMemory) ksession;
            BetaMemory bm = ((AccumulateMemory) wm.getNodeMemory(accNode)).getBetaMemory();
            SegmentMemory smem = bm.getSegmentMemory();

            assertThat(smem.getRootNode()).as("the split made the accumulate node a segment root").isEqualTo(accNode);
            assertThat(bm.getNodePosMaskBit()).as("position bit of the first node of the segment").isEqualTo(1L);
        } finally {
            ksession.dispose();
        }
    }

    private void assertRuleFiresWhenRightFactArrivesLater(KieBaseTestConfiguration kieBaseTestConfiguration,
                                                          String drl, String expected) {
        KieSession ksession = buildKieBase(kieBaseTestConfiguration, drl).newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            // A(1) reaches the accumulate node while no B exists, so the result constraint is not satisfied
            ksession.insert(new A(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before B(1) exists").isEmpty();

            // B(1) arrives on the right input of the accumulate node
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once B(1) exists").containsExactly(expected);
        } finally {
            ksession.dispose();
        }
    }

    private KieBase buildKieBase(KieBaseTestConfiguration kieBaseTestConfiguration, String drl) {
        return KieBaseUtil.getKieBaseFromKieModuleFromDrl("segment-split", kieBaseTestConfiguration,
                                                          BIGGER_PACKAGE, drl);
    }

    private ObjectTypeNode getObjectTypeNode(KieBase kbase, Class<?> nodeClass) {
        for (ObjectTypeNode n : ((InternalRuleBase) kbase).getRete().getObjectTypeNodes()) {
            if (((ClassObjectType) n.getObjectType()).getClassType() == nodeClass) {
                return n;
            }
        }
        return null;
    }
}
