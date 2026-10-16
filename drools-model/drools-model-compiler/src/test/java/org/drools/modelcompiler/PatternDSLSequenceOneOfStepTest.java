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
package org.drools.modelcompiler;

// BUG: rewriteXorSteps blindly consumes the next step even when it is another XOR/XNOR.
// sequence(oneOf(a,b), oneOf(c,d), e) should be rejected (consecutive bare oneOf steps require a
// positive step between them) but instead silently rewrites to sequence(and(oneOf(a,b),oneOf(c,d)), e),
// binding oneOf(c,d) as the trigger for oneOf(a,b) rather than as an independent step.
// See: ViewPatternBuilder.rewriteRevertingGateSteps(), line ~262.

import java.util.ArrayList;
import java.util.List;

import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.DSL.exists;
import static org.drools.model.DSL.expr;
import static org.drools.model.PatternDSL.absent;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.oneOf;
import static org.drools.model.PatternDSL.noneOrAll;

/**
 * Specification tests for oneOf() as a sequence step.
 *
 * Contract: exactly one of the oneOf() children must match before the positive trigger.
 * Write {@code sequence(..., oneOf(a, b), trigger)} — the compiler wraps the pair into
 * {@code and(oneOf(a, b), trigger)} automatically.
 * Zero matches → AND gate never fires, sequence does not advance.
 * Two or more matches → oneOf() gate over-matches, triggers {@code VetoSignalProcessor},
 * sequence resets to step 0. See ADR-0004.
 */
public class PatternDSLSequenceOneOfStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void oneOfExactlyOneMatch_fires() {
        // sequence(oneOf(alarmA, alarmB), ack): exactly alarmA inserted, then ack → rule fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-one-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one → XOR matched
        insertAndFire(new Toy("ack"));    // AND gate fires → sequence advances → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void oneOfNeitherMatch_doesNotFire() {
        // sequence(oneOf(alarmA, alarmB), ack): neither alarm inserted → XOR stays UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-none").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // ack arrives but XOR never matched → AND gate never fires
        assertThat(results).isEmpty();
    }

    @Test
    public void oneOfBothMatch_rollsBackAndDoesNotFire() {
        // sequence(oneOf(alarmA, alarmB), ack): both alarms → XOR reverts to UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-both").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → reverts to UNMATCHED
        insertAndFire(new Toy("ack"));    // ack arrives but XOR is UNMATCHED → AND never fires
        assertThat(results).isEmpty();
    }

    @Test
    public void oneOfBareTopLevel_throws() {
        // sequence(oneOf(a, b)) — bare XOR as the trailing step is rejected during rule.build()
        // by ViewPatternBuilder's trailing-step guard.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-bare").build(
            pattern(person),
            sequence(
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void oneOfThreeOperandsBareTopLevel_throws() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-three-operands-bare").build(
            pattern(person),
            sequence(
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB")),
                    pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void oneOfBareFollowedByStep_sugarFiresOnExactlyOneMatch() {
        // sequence(oneOf(a, b), c) — same rule, different name to confirm rewrite is idempotent.
        // Exactly one alarm inserted, then ack → rule fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one → XOR matched
        insertAndFire(new Toy("ack"));    // ack closes the window → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void oneOfBareFollowedByStep_sugarDoesNotFireOnBothMatch() {
        // sequence(oneOf(a, b), c) sugar: both alarms inserted → XOR reverts to UNMATCHED → ack arrives but no fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-no-fire").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → reverts UNMATCHED
        insertAndFire(new Toy("ack"));    // ack arrives but XOR is UNMATCHED → does not fire
        assertThat(results).isEmpty();
    }


    @Test
    public void wrappedTrailingRevertingGate_throws() {
        // sequence(..., and(oneOf(a, b))) has no positive trigger in the trailing step.
        // It must be rejected at rule build time.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("wrapped-xor-rejected").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    oneOf(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    )
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void orWithOneNonPositiveBranch_throws() {
        // sequence(..., or(oneOf(a, b), c)) — the OR branch containing oneOf(a,b) has no
        // positive trigger; OR requires ALL branches to have one, so this must be rejected.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("or-non-positive-branch-rejected").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                or(
                    oneOf(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void andWithXorAndPositiveSibling_fires() {
        // sequence(..., and(oneOf(a, b), c)) — the AND composite contains the reverting gate
        // plus a positive sibling c.  AND only needs ONE child to be a positive trigger,
        // so this must compile and fire when exactly one alarm matches AND c arrives.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("and-xor-positive-sibling-accepted").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    oneOf(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one → XOR matched
        insertAndFire(new Toy("ack"));    // positive sibling arrives → AND fires → sequence advances → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void andWithXorAndNonStepSibling_passesTriggerCheckButIsRejectedAsEvalGate() {
        // sequence(..., and(oneOf(a, b), expr(...))) — expr(...) is a ViewItem but not a SequenceStep,
        // so ViewPatternBuilder.hasPositiveTrigger() counts it as a positive trigger and the trailing-gate
        // check passes.  The KieBase build then rejects it, because an EVAL cannot be a composite step gate.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("and-xor-non-step-sibling").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    oneOf(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    expr("alwaysTrue", alarmA, t -> true)
                )
            ),
            execute(() -> results.add("fired"))
        );

        assertThatThrownBy(() -> makeKSession(rule))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not yet support condition type EVAL as a composite step gate");
    }

    @Test
    public void andWithExistsSibling_passesTriggerCheckButIsRejectedAsExistsGate() {
        // sequence(..., and(exists(a), b)) — exists(...) is an ExistentialExprViewItem of type EXISTS,
        // so ViewPatternBuilder.hasPositiveTrigger() counts it as a positive trigger and the trailing-gate
        // check passes.  The KieBase build then rejects it, because EXISTS cannot be a composite step gate.
        Variable<Toy> alarmA = declarationOf(Toy.class);

        Rule rule = rule("and-exists-sibling").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    exists(pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA"))),
                    pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        assertThatThrownBy(() -> makeKSession(rule))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not yet support condition type EXISTS as a composite step gate");
    }

    // -------------------------------------------------------------------------
    // Bug #5 reproductions: consecutive bare xor/xnor steps
    //
    // rewriteXorSteps() must not consume a following XOR or XNOR step as the positive
    // trigger for the preceding XOR/XNOR.  The tests below document two things:
    //   1. The expected contract: consecutive bare xor/xnor steps should be rejected.
    //   2. The observable mis-wiring on the unfixed code.
    //
    // The two "throws" tests FAIL on the buggy code (no exception is thrown).
    // The "miswiring" test FAILS on the buggy code (the rule fires when it should not).
    // All three tests PASS once the fix is applied.
    // -------------------------------------------------------------------------

    @Test
    public void consecutiveBareXorSteps_noFollowingPositiveStep_throws() {
        // sequence(start, oneOf(a, b), oneOf(c, d)) — trailing xor run with no positive step.
        // The run is oneOf(a,b)+oneOf(c,d) but there is nothing after them: the sequence ends.
        // The trailing-step check must reject this with IllegalArgumentException.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("consecutive-xor-no-positive").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        oneOf(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        )
                ),
                execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");
    }

    @Test
    public void consecutiveBareXorXnorSteps_noFollowingPositiveStep_throws() {
        // sequence(start, oneOf(a, b), xnor(c, d)) — trailing mixed run with no positive step.
        // The run ends the sequence: the trailing-step guard must reject it.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("consecutive-xor-xnor-no-positive").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        org.drools.model.PatternDSL.noneOrAll(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        )
                ),
                execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");
    }

    // -------------------------------------------------------------------------
    // Consecutive XOR/XNOR guards — working runtime tests
    //
    // sequence(oneOf(a,b), oneOf(c,d), ack): both XOR groups are simultaneously active.
    // The AND composite fires only when both XOR gates are in MATCHED state and ack arrives.
    // -------------------------------------------------------------------------

    @Test
    public void twoConsecutiveXorGuards_bothExactlyOne_fires() {
        // sequence(oneOf(a,b), oneOf(c,d), ack):
        // Exactly one of {a,b} and exactly one of {c,d} matched → AND MATCHED → ack → rule fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-both-one-fires").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        oneOf(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // oneOf(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmC")); // oneOf(c,d): 1 match → MATCHED → AND both bits set
        insertAndFire(new Toy("ack"));    // AND fires → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void twoConsecutiveXorGuards_firstGroupBothMatch_doesNotFire() {
        // sequence(oneOf(a,b), oneOf(c,d), ack):
        // Both a and b match → oneOf(a,b) reverts to UNMATCHED → AND never fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-first-both-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        oneOf(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // oneOf(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // oneOf(a,b): 2 matches → REVERTS to UNMATCHED
        insertAndFire(new Toy("alarmC")); // oneOf(c,d): 1 match → MATCHED; but AND bit 1 cleared
        insertAndFire(new Toy("ack"));    // AND never fires — oneOf(a,b) is UNMATCHED
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveXorGuards_secondGroupBothMatch_doesNotFire() {
        // sequence(oneOf(a,b), oneOf(c,d), ack):
        // Both c and d match → oneOf(c,d) reverts to UNMATCHED → AND never fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-second-both-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        oneOf(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // oneOf(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmC")); // oneOf(c,d): 1 match → MATCHED; AND both bits set
        insertAndFire(new Toy("alarmD")); // oneOf(c,d): 2 matches → REVERTS to UNMATCHED; AND bit 2 cleared
        insertAndFire(new Toy("ack"));    // AND never fires — oneOf(c,d) is UNMATCHED
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveXorGuards_neitherGroupMatched_doesNotFire() {
        // sequence(oneOf(a,b), oneOf(c,d), ack):
        // Neither XOR group has any match → both AND bits unset → ack arrives → rule does not fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-neither-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        oneOf(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // no XOR group satisfied — AND never fires
        assertThat(results).isEmpty();
    }


    // -------------------------------------------------------------------------
    // Mixed consecutive guard types: oneOf() followed by absent()
    //
    // sequence(oneOf(a,b), absent(C), D): the XOR rewrite phase checks whether the
    // step immediately after the XOR run is a positive trigger.  absent(C) is a
    // guard, not a trigger, so hasPositiveTrigger(absent(C)) returns false and the
    // rewrite incorrectly throws "consecutive bare xor/xnor steps require a
    // positive pattern step".  The actual positive trigger D is never reached.
    //
    // These tests FAIL on the buggy code (exception thrown at rule.build time)
    // and PASS once the fix is applied.
    // -------------------------------------------------------------------------

    @Test
    public void oneOfFollowedByNotGuardThenPositive_buildDoesNotThrow() {
        // sequence(start, oneOf(a,b), absent(C), done):
        // The XOR run is followed by an absent() guard, then a positive step.
        // Rule.build() must NOT throw — this is a valid sequence.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        // On the buggy code this throws IllegalArgumentException because
        // hasPositiveTrigger(absent(blocker)) == false.
        Rule rule = rule("xor-not-positive-builds").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        absent(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        // Reaching here means build() did not throw — that is the assertion.
        ksession = makeKSession(rule);
        assertThat(ksession).isNotNull();
    }

    @Test
    public void oneOfFollowedByNotGuardThenPositive_fires() {
        // sequence(start, oneOf(a,b), absent(blocker), done):
        // Exactly one alarm fires, no blocker, then done → rule fires.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("xor-not-positive-fires").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        absent(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one XOR child matched
        // no blocker inserted — absence guard passes
        insertAndFire(new Toy("done"));   // positive trigger → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void oneOfFollowedByNotGuardThenPositive_notBlockerVetoes() {
        // sequence(start, oneOf(a,b), absent(blocker), done):
        // Exactly one alarm fires, then blocker arrives → absent() guard vetoes → rule must NOT fire.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("xor-not-positive-veto").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        absent(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("blocker")); // absent() guard vetoes → sequence reset
        insertAndFire(new Toy("done"));    // sequence is reset — must NOT fire
        assertThat(results).isEmpty();
    }

    @Test
    public void oneOfFollowedByMultiPatternAbsentThenPositive_fires() {
        // sequence(start, oneOf(a,b), absent(blockerA, blockerB), done):
        // absent(x, y) is a NOR CombinedExprViewItem.  Exactly one alarm, no blocker, then done → fires.
        Variable<Toy> alarmA   = declarationOf(Toy.class);
        Variable<Toy> alarmB   = declarationOf(Toy.class);
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("xor-nor-positive-fires").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        absent(
                                pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA")),
                                pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))
                        ),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one XOR child matched
        insertAndFire(new Toy("done"));   // no blocker arrived → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void oneOfFollowedByMultiPatternAbsentThenPositive_eitherBlockerVetoes() {
        // sequence(start, oneOf(a,b), absent(blockerA, blockerB), done):
        // The second blocker alone is enough to veto → rule must NOT fire.
        Variable<Toy> alarmA   = declarationOf(Toy.class);
        Variable<Toy> alarmB   = declarationOf(Toy.class);
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("xor-nor-positive-veto").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        absent(
                                pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA")),
                                pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))
                        ),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));   // exactly one XOR child matched
        insertAndFire(new Toy("blockerB")); // second guard pattern arrives → veto → sequence reset
        insertAndFire(new Toy("done"));     // sequence is reset — must NOT fire
        assertThat(results).isEmpty();
    }

    @Test
    public void oneOfBothMatch_resetsSequence_doesNotFire() {
        // sequence(start, oneOf(alarmA, alarmB), ack): both branches match → XOR reverts →
        // VetoSignalProcessor resets sequence to step 0 → ack arrives at step 0 → rule must NOT fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-both-resets").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED, step advances to ack
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → UNMATCHED → veto → sequence resets to step 0
        insertAndFire(new Toy("ack"));    // sequence is at step 0 (waiting for start) → must NOT fire
        assertThat(results).isEmpty();
    }

    @Test
    public void oneOfBothMatch_resetsAndRetries_fires() {
        // sequence(start, oneOf(alarmA, alarmB), ack):
        // First attempt: both branches match → sequence resets to step 0.
        // Second attempt: start re-fires, exactly one branch matches, ack → rule fires once.
        // Proves the sequence is correctly re-usable after a reset.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-both-resets-retries").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        // First attempt — over-match resets the sequence
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED, step advances to ack
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → UNMATCHED → veto → reset to step 0
        insertAndFire(new Toy("ack"));    // sequence is at step 0, ack has no effect → still no fire
        assertThat(results).isEmpty();
        // Second attempt — exactly one branch, then ack → fires
        insertAndFire(new Toy("start"));  // step 0 re-matches (new Toy("start") instance) → advance to xor step
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED, step advances to ack
        insertAndFire(new Toy("ack"));    // ack → rule fires
        assertThat(results).containsExactly("fired");
    }

    /**
     * Regression guard for the AND parent gate's matched-bit after a XOR veto.
     *
     * <p>When XOR over-matches, {@code VetoSignalProcessor} calls {@code step.deactivate()},
     * which calls {@code resetLogicGateMemory(andGateIndex)} — clearing <em>both</em>
     * {@code gateMemory[andIdx]} and {@code gateMatched[andIdx]}.  If that clear were ever
     * skipped (e.g. a future refactor of {@code deactivate()} that respects
     * {@code statusCanRevert}), the AND gate would stay in MATCHED state.  The next
     * sequence activation would then see the AND already satisfied; inserting {@code ack}
     * alone (without re-satisfying the XOR) would fire the rule spuriously.
     *
     * <p>Structure of the test:
     * <ol>
     *   <li>First attempt: alarmA (XOR matches), alarmB (XOR over-matches) → veto → reset to step 0.</li>
     *   <li>Second attempt: start advances to the XOR step.  Only {@code ack} is inserted —
     *       no XOR child.  The rule must NOT fire, proving the AND's matched-bit was cleared.</li>
     *   <li>Third attempt: start, then exactly one alarm, then ack → rule fires exactly once,
     *       confirming the gate is fully functional after two resets.</li>
     * </ol>
     */
    @Test
    public void oneOfVeto_clearsAndGateMatchedBit_ackAloneDoesNotFire() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-veto-clears-and-bit").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        oneOf(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));

        // Attempt 1: over-match → veto → sequence resets to step 0.
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED; AND bit 1 set
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → reverts → VetoSignalProcessor fires
        //   deactivate() must clear gateMatched[andIdx] = false here
        assertThat(results).isEmpty();

        // Attempt 2: start re-advances to the XOR/ack step; ack arrives but XOR has NOT been
        // re-satisfied. If AND's matched-bit were stale-true the rule would fire here — it must not.
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // XOR still UNMATCHED → AND cannot be MATCHED → no fire
        assertThat(results).isEmpty();

        // Attempt 3: full clean path proves the gate is usable after two resets.
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("ack"));    // AND: both bits set → fires
        assertThat(results).containsExactly("fired");
    }

    @AfterEach
    public void tearDown() {
        results.clear();
        if (ksession != null) { ksession.dispose(); }
    }

    private void insertAndFire(Object... facts) {
        for (Object fact : facts) { ksession.insert(fact); }
        ksession.fireAllRules();
    }

    private KieSession makeKSession(Rule rule) {
        final Model model = new ModelImpl().addRule(rule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }
}
