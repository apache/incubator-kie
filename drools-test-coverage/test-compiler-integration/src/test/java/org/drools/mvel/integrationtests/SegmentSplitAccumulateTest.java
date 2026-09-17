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
import org.drools.core.reteoo.LeftTupleNode;
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
 * <p>The same renumbering runs on the removal path: SegmentPrototype.mergeProtos() reassigns the position
 * bits of the merged memory prototypes with the very same MemoryPrototype.setNodePosMaskBit() calls. A live
 * session survives that even with the stale wrapped bit, because EagerPhreakBuilder.mergeSegment() pushes the
 * bits of the prototypes into the existing memories, but a session created after the removal is populated
 * from the prototypes and sees the stale bit.</p>
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

    private static final String PACKAGE = "org.drools.mvel.integrationtests.segmentsplit";

    private static final String HEADER =
            "package " + PACKAGE + ";\n" +
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

    /**
     * The accumulate source is a conjunction, so it is compiled into a subnetwork and the accumulate node
     * reads its right input from a TupleToObjectNode. That node is held by the BetaMemoryPrototype wrapped
     * in the AccumulateMemoryPrototype, so this rule covers the branch of BetaMemoryPrototype.populateMemory()
     * that also has to resolve a SubnetworkPathMemory.
     */
    private static final String RULE_ACCUMULATE_SUBNETWORK =
            "rule \"R1 accumulate over subnetwork\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  accumulate( B( value == $v ) and C( value == $v ); $n : count(); $n > 0 )\n" +
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

    /**
     * The accumulate node sits behind a join instead of directly behind the LeftInputAdapterNode, so that the
     * node that splits its segment and the node whose segment it is merged into can be different ones.
     */
    private static final String RULE_ACCUMULATE_BEHIND_JOIN =
            "rule \"R1 accumulate behind a join\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  B( value == $v )\n" +
            "  accumulate( C( value == $v ); $n : count(); $n > 0 )\n" +
            "then\n" +
            "  results.add( \"R1:\" + $n );\n" +
            "end\n";

    /** Splits the segment at the join, which makes the accumulate node a segment root. */
    private static final String RULE_SPLITTING_THE_JOIN =
            "rule \"R2 shares the A and B patterns\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  B( value == $v )\n" +
            "  C( value == $v )\n" +
            "then\n" +
            "  results.add( \"R2:\" + $v );\n" +
            "end\n";

    /** Splits the segment at the LeftInputAdapterNode, so the join is a segment of its own. */
    private static final String RULE_SPLITTING_THE_LIA =
            "rule \"R3 shares the A pattern\"\n" +
            "when\n" +
            "  A( $v : value )\n" +
            "  C( value == $v )\n" +
            "then\n" +
            "  results.add( \"R3:\" + $v );\n" +
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
        AccumulateNode accNode = findAccumulateNodeUnderTheSharedLia(kbase);

        KieSession ksession = kbase.newKieSession();
        try {
            ksession.setGlobal("results", new ArrayList<String>());
            ksession.insert(new A(1));
            ksession.fireAllRules();

            BetaMemory bm = getBetaMemory(ksession, accNode);
            SegmentMemory smem = bm.getSegmentMemory();

            assertThat(smem.getRootNode()).as("the split made the accumulate node a segment root").isEqualTo(accNode);
            assertThat(bm.getNodePosMaskBit()).as("position bit of the first node of the segment").isEqualTo(1L);
        } finally {
            ksession.dispose();
        }
    }

    /**
     * Removing the rule that caused the split merges the two segments back together through
     * SegmentPrototype.mergeProtos(), which renumbers the position bits of the merged memory prototypes with
     * the same MemoryPrototype.setNodePosMaskBit() calls that splitProtos() uses. EagerPhreakBuilder then
     * pushes those bits into the memories of the already running session by reading them back through
     * MemoryPrototype.getNodePosMaskBit(), so the accumulate node of a live session must end up with the bit
     * it holds inside the merged segment and keep firing.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateRuleStillFiresAfterTheSharingRuleIsRemovedFromALiveSession(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA);
        AccumulateNode accNode = findAccumulateNodeUnderTheSharedLia(kbase);

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before B(1) exists").isEmpty();

            kbase.removeRule(PACKAGE, "R2 shares the A pattern");

            BetaMemory bm = getBetaMemory(ksession, accNode);
            SegmentMemory smem = bm.getSegmentMemory();
            assertThat(smem.getRootNode()).as("the merge put the accumulate node back behind the LeftInputAdapterNode")
                    .isNotEqualTo(accNode);
            assertThat(bm.getNodePosMaskBit()).as("position bit of the accumulate node within the merged segment")
                    .isEqualTo(2L);

            // B(1) arrives on the right input of the accumulate node after the segments were merged
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once B(1) exists").containsExactly("R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * The same removal, but the accumulate rule is the one that splits the segment of the already built
     * sharing rule, so the split and the merge are both applied incrementally to a live session.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateRuleBuiltAfterSharingRuleStillFiresAfterTheSharingRuleIsRemoved(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_SHARING_LIA + RULE_ACCUMULATE_FROM);

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before B(1) exists").isEmpty();

            kbase.removeRule(PACKAGE, "R2 shares the A pattern");

            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once B(1) exists").containsExactly("R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * A session created after the removal takes its memories from the merged prototypes through
     * MemoryPrototype.populateMemory(), which reads the bit of the BetaMemoryPrototype wrapped in the
     * AccumulateMemoryPrototype.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateRuleFiresInASessionCreatedAfterTheSharingRuleIsRemoved(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_FROM + RULE_SHARING_LIA);
        kbase.removeRule(PACKAGE, "R2 shares the A pattern");

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before B(1) exists").isEmpty();

            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once B(1) exists").containsExactly("R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * Removing the rule that split the segment at the join leaves the accumulate node one position further
     * down the merged segment than it held when its prototype was built, so the bit that
     * MemoryPrototype.populateMemory() copies into a session created afterwards has to be the merged one and
     * not the one the wrapped BetaMemoryPrototype was constructed with.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateBehindAJoinFiresInASessionCreatedAfterTheSplittingRuleIsRemoved(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_BEHIND_JOIN
                                                                       + RULE_SPLITTING_THE_JOIN + RULE_SPLITTING_THE_LIA);
        kbase.removeRule(PACKAGE, "R2 shares the A and B patterns");

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before C(1) exists").isEmpty();

            AccumulateNode accNode = findAccumulateNode(kbase);
            BetaMemory bm = getBetaMemory(ksession, accNode);
            assertThat(bm.getSegmentMemory().getRootNode())
                    .as("the merge put the accumulate node behind the join").isNotEqualTo(accNode);
            assertThat(bm.getNodePosMaskBit())
                    .as("position bit of the accumulate node within the merged segment").isEqualTo(2L);

            ksession.insert(new C(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once C(1) exists").containsExactly("R1:1", "R3:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * The same removal against a running session, which EagerPhreakBuilder.mergeSegment() updates by reading
     * the merged bits back through MemoryPrototype.getNodePosMaskBit().
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateBehindAJoinFiresInALiveSessionAfterTheSplittingRuleIsRemoved(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_BEHIND_JOIN
                                                                       + RULE_SPLITTING_THE_JOIN + RULE_SPLITTING_THE_LIA);

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("nothing may fire before C(1) exists").isEmpty();

            kbase.removeRule(PACKAGE, "R2 shares the A and B patterns");

            AccumulateNode accNode = findAccumulateNode(kbase);
            BetaMemory bm = getBetaMemory(ksession, accNode);
            assertThat(bm.getNodePosMaskBit())
                    .as("position bit of the accumulate node within the merged segment").isEqualTo(2L);

            ksession.insert(new C(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once C(1) exists").containsExactly("R1:1", "R3:1");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOverSubnetworkRuleBuiltBeforeSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertSubnetworkRuleFiresWhenTheSubnetworkIsCompletedLater(
                kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_SUBNETWORK + RULE_SHARING_LIA);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOverSubnetworkRuleBuiltAfterSharingRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        assertSubnetworkRuleFiresWhenTheSubnetworkIsCompletedLater(
                kieBaseTestConfiguration, HEADER + RULE_SHARING_LIA + RULE_ACCUMULATE_SUBNETWORK);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOverSubnetworkRuleStillFiresAfterTheSharingRuleIsRemoved(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_SUBNETWORK + RULE_SHARING_LIA);

        KieSession ksession = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new A(1));
            ksession.insert(new C(1));
            ksession.fireAllRules();
            assertThat(results).as("only the sharing rule may fire before B(1) exists").containsExactly("R2:1");

            kbase.removeRule(PACKAGE, "R2 shares the A pattern");

            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once the subnetwork matches")
                    .containsExactly("R2:1", "R1:1");
        } finally {
            ksession.dispose();
        }
    }

    /**
     * An accumulate whose source is a subnetwork always roots its own segment: the subnetwork branches off the
     * left tuple source of the accumulate node, which gives that source more than one sink and therefore makes
     * it a segment tip (BuildtimeSegmentUtilities.isNonTerminalTipNode()). Adding or removing a rule that
     * shares the LeftInputAdapterNode can neither split nor merge that segment away, so the position bit of
     * such an accumulate node is pinned to the first one whatever else happens to the network.
     *
     * <p>This test pins down that invariant, because it is the reason why the renumbering bug cannot reach an
     * accumulate over a subnetwork: should the node ever stop being a segment root, its bit would start being
     * renumbered through MemoryPrototype.setNodePosMaskBit() like any other. It also covers the branch of
     * BetaMemoryPrototype.populateMemory() that resolves the SubnetworkPathMemory of the TupleToObjectNode
     * held by the prototype wrapped in the AccumulateMemoryPrototype.</p>
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void accumulateOverSubnetworkNodeIsAlwaysASegmentRoot(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = buildKieBase(kieBaseTestConfiguration, HEADER + RULE_ACCUMULATE_SUBNETWORK + RULE_SHARING_LIA);
        AccumulateNode accNode = findAccumulateNodeUnderTheSharedLia(kbase);

        KieSession ksession = kbase.newKieSession();
        try {
            ksession.setGlobal("results", new ArrayList<String>());
            ksession.insert(new A(1));
            ksession.fireAllRules();

            BetaMemory bm = getBetaMemory(ksession, accNode);
            SegmentMemory smem = bm.getSegmentMemory();

            assertThat(bm.getSubnetworkPathMemory()).as("the accumulate source is a subnetwork").isNotNull();
            assertThat(smem.getRootNode()).as("the accumulate node is a segment root").isEqualTo(accNode);
            assertThat(bm.getNodePosMaskBit()).as("position bit of the first node of the segment").isEqualTo(1L);

            kbase.removeRule(PACKAGE, "R2 shares the A pattern");

            BetaMemory bmAfter = getBetaMemory(ksession, accNode);
            assertThat(bmAfter.getSegmentMemory().getRootNode())
                    .as("the subnetwork keeps the left tuple source a segment tip, so no merge happens")
                    .isEqualTo(accNode);
            assertThat(bmAfter.getNodePosMaskBit()).as("position bit is unchanged by the removal").isEqualTo(1L);
        } finally {
            ksession.dispose();
        }
    }

    private void assertSubnetworkRuleFiresWhenTheSubnetworkIsCompletedLater(KieBaseTestConfiguration kieBaseTestConfiguration,
                                                                            String drl) {
        KieSession ksession = buildKieBase(kieBaseTestConfiguration, drl).newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            // C(1) only matches the sharing rule, the subnetwork of the accumulate still misses its B
            ksession.insert(new A(1));
            ksession.insert(new C(1));
            ksession.fireAllRules();
            assertThat(results).as("only the sharing rule may fire before B(1) exists").containsExactly("R2:1");

            // B(1) completes the subnetwork, which propagates through the TupleToObjectNode to the right
            // input of the accumulate node
            ksession.insert(new B(1));
            ksession.fireAllRules();
            assertThat(results).as("R1 must fire once the subnetwork matches").containsExactly("R2:1", "R1:1");
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

    private AccumulateNode findAccumulateNodeUnderTheSharedLia(KieBase kbase) {
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class);
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        AccumulateNode accNode = null;
        for (LeftTupleSink sink : liaNode.getSinkPropagator().getSinks()) {
            if (sink instanceof AccumulateNode) {
                accNode = (AccumulateNode) sink;
            }
        }
        assertThat(accNode).as("the accumulate node shares the LeftInputAdapterNode").isNotNull();
        return accNode;
    }

    private AccumulateNode findAccumulateNode(KieBase kbase) {
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class);
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        AccumulateNode accNode = findAccumulateNode(liaNode);
        assertThat(accNode).as("the accumulate node is reachable from the shared LeftInputAdapterNode").isNotNull();
        return accNode;
    }

    private AccumulateNode findAccumulateNode(LeftTupleNode node) {
        if (node.getSinkPropagator() == null) {
            return null;
        }
        for (LeftTupleSink sink : node.getSinkPropagator().getSinks()) {
            if (sink instanceof AccumulateNode) {
                return (AccumulateNode) sink;
            }
            AccumulateNode found = findAccumulateNode((LeftTupleNode) sink);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BetaMemory getBetaMemory(KieSession ksession, AccumulateNode accNode) {
        InternalWorkingMemory wm = (InternalWorkingMemory) ksession;
        return ((AccumulateMemory) wm.getNodeMemory(accNode)).getBetaMemory();
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
