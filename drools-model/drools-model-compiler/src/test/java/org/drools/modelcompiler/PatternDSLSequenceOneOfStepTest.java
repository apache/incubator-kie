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

import java.util.ArrayList;
import java.util.List;

import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Adult;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Relationship;
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
import static org.drools.model.PatternDSL.noneOrAll;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.oneOf;

public class PatternDSLSequenceOneOfStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void oneOfExactlyOneMatchFires() {
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
    public void oneOfNeitherMatchDoesNotFire() {
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
    public void oneOfBothMatchRollsBackAndDoesNotFire() {
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
    public void oneOfBareTopLevelThrows() {
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
    public void oneOfThreeOperandsBareTopLevelThrows() {
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
    public void trailingOneOfWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyA = declarationOf(Toy.class);
        Variable<Toy> toyB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-oneOf").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    oneOf(
                        pattern(toyA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(toyB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    )
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void oneOfReversionClearsGrandparentBit() {
        Variable<Toy>          toyVar   = declarationOf(Toy.class);
        Variable<Relationship> relVar   = declarationOf(Relationship.class);
        Variable<Adult>        adultVar = declarationOf(Adult.class);

        Rule deepRule = rule("deep-revert-rule").build(
                sequence(
                    and(
                        and(
                            oneOf(
                                pattern(toyVar).expr("toy-alpha-filter", t -> t.getName().equals("alpha")),
                                pattern(toyVar).expr("toy-beta-filter",  t -> t.getName().equals("beta"))
                            ),
                            pattern(relVar).expr("rel-filter", r -> r.getStart().equals("go"))
                        ),
                        pattern(adultVar).expr("adult-filter", a -> a.getName().equals("final"))
                    )
                ),
                execute(() -> results.add("fired"))
        );

        final Model   model   = new ModelImpl().addRule(deepRule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        ksession = kieBase.newKieSession();

        // Anchor — activates the sequencer
        insertAndFire(new Person("anchor"));

        // XOR step: insert toyA → XOR matched, inner AND waits for rel
        insertAndFire(new Toy("alpha"));
        assertThat(results).isEmpty();

        // Insert rel → inner AND matched; outer AND has inner-AND's bit set, waits for adult
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).isEmpty();

        // Insert toyB → XOR reverts (exactly one → now two → XOR un-matches)
        // clearParentBit must propagate up to clear the outer AND's stale bit too
        insertAndFire(new Toy("beta"));
        assertThat(results).isEmpty();

        // adult arrives — outer AND must NOT fire: inner-AND's bit was cleared by revert chain
        insertAndFire(new Adult("final", 30));
        assertThat(results).as("rule must not fire after XOR reversion cleared grandparent bit").isEmpty();
    }


    @Test
    public void wrappedTrailingRevertingGateThrows() {
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
    public void orWithOneNonPositiveBranchThrows() {
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
    public void andWithXorAndPositiveSiblingFires() {
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
    public void andWithXorAndNonStepSiblingPassesTriggerCheckButIsRejectedAsEvalGate() {
        // sequence(..., and(oneOf(a, b), expr(...))) — expr(...) is a ViewItem but not a SequenceStep,
        // so ViewPatternBuilder.hasPositiveTrigger() counts it as a positive trigger and the trailing-gate
        // check passes.  The KieBase build then rejects it, because an EVAL cannot be a composite step gate (yet).
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
    public void andWithExistsSiblingPassesTriggerCheckButIsRejectedAsExistsGate() {
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

    @Test
    public void consecutiveBareXorStepsNoFollowingPositiveStepThrows() {
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
    public void consecutiveBareXorXnorStepsNoFollowingPositiveStepThrows() {
        // sequence(start, oneOf(a, b), noneOrAll(c, d)) — trailing mixed run with no positive step.
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

    @Test
    public void twoConsecutiveXorGuardsBothExactlyOneFires() {
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
    public void twoConsecutiveXorGuardsFirstGroupBothMatchDoesNotFire() {
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
    public void twoConsecutiveXorGuardsSecondGroupBothMatchDoesNotFire() {
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
    public void twoConsecutiveXorGuardsNeitherGroupMatchedDoesNotFire() {
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

    @Test
    public void oneOfFollowedByNotGuardThenPositiveBuildDoesNotThrow() {
        // sequence(start, oneOf(a,b), absent(C), done):
        // The XOR run is followed by an absent() guard, then a positive step.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

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

        ksession = makeKSession(rule);
        assertThat(ksession).isNotNull();
    }

    @Test
    public void oneOfFollowedByNotGuardThenPositiveFires() {
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
    public void oneOfFollowedByNotGuardThenPositiveNotBlockerVetoes() {
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
    public void oneOfFollowedByMultiPatternAbsentThenPositiveFires() {
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
    public void oneOfFollowedByMultiPatternAbsentThenPositiveEitherBlockerVetoes() {
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
    public void oneOfBothMatchResetsAndRetriesFires() {
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

    @Test
    public void oneOfVetoClearsAndGateMatchedBitAckAloneDoesNotFire() {
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

    @Test
    public void oneOfWithTwoNoneOrAllChildrenAtStep0ThrowsAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmE = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("xor-two-xnor-step0").build(
                pattern(person),
                sequence(
                    oneOf(
                        noneOrAll(
                            pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        noneOrAll(
                            pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                            pattern(alarmE).expr("isAlarmE", t -> t.getName().equals("alarmE"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    @Test
    public void oneOfWithTwoNoneOrAllChildrenAtLaterStepThrowsAtBuildTime() {
        Variable<Toy> start = declarationOf(Toy.class);
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmE = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("xor-two-xnor-later-step").build(
                pattern(person),
                sequence(
                    pattern(start).expr("isStart", t -> t.getName().equals("start")),
                    oneOf(
                        noneOrAll(
                            pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        noneOrAll(
                            pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                            pattern(alarmE).expr("isAlarmE", t -> t.getName().equals("alarmE"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    @Test
    public void oneOfWithNestedAndOrChildrenMatchingOnActivationThrowsAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmE = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(and(noneOrAll(A, B), noneOrAll(C, E)), noneOrAll(F, G)) -> both branches match on activation -> 2 matches
        assertThatThrownBy(() ->
            rule("xor-nested-and-xnor").build(
                pattern(person),
                sequence(
                    oneOf(
                        and(
                            noneOrAll(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                            ),
                            noneOrAll(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmE).expr("isAlarmE", t -> t.getName().equals("alarmE"))
                            )
                        ),
                        noneOrAll(
                            pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                            pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    @Test
    public void oneOfWithNestedAndRequiringFactDoesNotThrowAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(and(noneOrAll(A, B), pattern(C)), noneOrAll(F, G))
        // Branch 1: and(noneOrAll, patternC) requires patternC, so does not match on activation (0 matches)
        // Branch 2: noneOrAll(F, G) matches on activation (1 match)
        // Exactly 1 child matches on activation -> valid build!
        Rule rule = rule("xor-nested-and-with-pattern").build(
            pattern(person),
            sequence(
                oneOf(
                    and(
                        noneOrAll(
                            pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                    ),
                    noneOrAll(
                        pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                        pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                    )
                ),
                pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    // ── matchesOnActivation(): XNOR nested in XNOR nested in XOR, and XOR nested in XOR ──

    @Test
    public void oneOfWithNoneOrAllNestingMatchingNoneOrAllThrowsAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmE = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(noneOrAll(noneOrAll(A, B), noneOrAll(C, E)), noneOrAll(F, G))
        // Outer child 1: both inner XNORs match on activation (2 of 2) -> matches.
        // Outer child 2: matches on activation -> 2 matching children -> build-time failure.
        assertThatThrownBy(() ->
            rule("xor-xnor-of-xnors").build(
                pattern(person),
                sequence(
                    oneOf(
                        noneOrAll(
                            noneOrAll(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                            ),
                            noneOrAll(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmE).expr("isAlarmE", t -> t.getName().equals("alarmE"))
                            )
                        ),
                        noneOrAll(
                            pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                            pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    @Test
    public void oneOfWithNoneOrAllNestingPartiallyMatchingNoneOrAllDoesNotThrowAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(noneOrAll(noneOrAll(A, B), pattern(C)), noneOrAll(F, G))
        // Outer child 1: 1 of 2 grandchildren match on activation -> does not match.
        // Outer child 2: matches on activation -> exactly 1 matching child -> valid build.
        Rule rule = rule("xor-xnor-of-xnor-and-pattern").build(
            pattern(person),
            sequence(
                oneOf(
                    noneOrAll(
                        noneOrAll(
                            pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                    ),
                    noneOrAll(
                        pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                        pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                    )
                ),
                pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void oneOfWithNestedOneOfMatchingOnActivationThrowsAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(oneOf(noneOrAll(A, B), pattern(C)), noneOrAll(F, G))
        // Outer child 1 (inner oneOf): exactly 1 grandchild matches on activation -> matches.
        // Outer child 2: matches on activation -> 2 matching children -> build-time failure.
        assertThatThrownBy(() ->
            rule("xor-of-xor").build(
                pattern(person),
                sequence(
                    oneOf(
                        oneOf(
                            noneOrAll(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                            ),
                            pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                        ),
                        noneOrAll(
                            pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                            pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    // ── matchesOnActivation(): OR and NOR children of oneOf() ──

    @Test
    public void oneOfWithOrChildMatchingOnActivationThrowsAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(or(noneOrAll(A, B), pattern(C)), noneOrAll(F, G))
        // Outer child 1: or() matches on activation because its noneOrAll branch does.
        // Outer child 2: matches on activation -> 2 matching children -> build-time failure.
        assertThatThrownBy(() ->
            rule("xor-or-matching").build(
                pattern(person),
                sequence(
                    oneOf(
                        or(
                            noneOrAll(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                            ),
                            pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                        ),
                        noneOrAll(
                            pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                            pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                        )
                    ),
                    pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence(): oneOf() has 2 children that match on step activation");
    }

    @Test
    public void oneOfWithOrChildOfPatternsOnlyDoesNotThrowAtBuildTime() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(or(pattern(A), pattern(B)), noneOrAll(F, G))
        // Outer child 1: no or() branch matches on activation (patterns need a fact) -> 0 matches.
        // Outer child 2: matches on activation -> exactly 1 matching child -> valid build.
        Rule rule = rule("xor-or-of-patterns").build(
            pattern(person),
            sequence(
                oneOf(
                    or(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    noneOrAll(
                        pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                        pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                    )
                ),
                pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        assertThat(rule).isNotNull();
    }

    @Test
    public void oneOfWithMultiPatternAbsentChildDoesNotCountAbsentAsActivationMatch() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmF = declarationOf(Toy.class);
        Variable<Toy> alarmG = declarationOf(Toy.class);
        Variable<Toy> ack = declarationOf(Toy.class);

        // oneOf(absent(A, B), noneOrAll(F, G))
        // absent(A, B) is a NOR composite: it never counts as matching on activation.
        // Only noneOrAll(F, G) matches -> exactly 1 matching child -> valid build.
        Rule rule = rule("xor-nor-child").build(
            pattern(person),
            sequence(
                oneOf(
                    absent(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    noneOrAll(
                        pattern(alarmF).expr("isAlarmF", t -> t.getName().equals("alarmF")),
                        pattern(alarmG).expr("isAlarmG", t -> t.getName().equals("alarmG"))
                    )
                ),
                pattern(ack).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        assertThat(rule).isNotNull();
    }

    @Test
    public void oneOfUnderOrUnderAndHappyPathExactlyOneAndPositiveFires() {
        // and(or(oneOf(alarmA, alarmB), cFact), dFact) — happy path.
        // Arrivals: start, alarmA (XOR matches once, OR satisfied), dFact (AND completes) -> fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("oneof-under-or-under-and-happy").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    or(
                        oneOf(
                            pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isCFact", t -> t.getName().equals("cFact"))
                    ),
                    pattern(toy).expr("isDFact", t -> t.getName().equals("dFact"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // oneOf: 1 match -> MATCHED; OR satisfied via XOR branch
        insertAndFire(new Toy("dFact"));  // AND: OR bit + dFact bit both set -> AND fires -> rule fires
        assertThat(results)
            .as("oneOf exactly one match under or, then d arrives: rule must fire")
            .containsExactly("fired");
    }

    @Test
    public void oneOfUnderOrUnderAndOrderACBDXorOvermatchVetoResets() {
        // and(or(oneOf(alarmA, alarmB), cFact), dFact) — reviewer's exact ordering (issue #7160 item 3).
        // Arrivals: start, a (XOR matches), c (OR other-branch, OR already satisfied),
        //           b (XOR over-matches -> VETO -> sequence resets to step 0), d (no-op at step 0).
        // The reset is proven by a second attempt below: start2, cFact2, dFact2 -> fires once,
        // demonstrating the sequence genuinely returned to step 0 rather than getting stuck.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("oneof-under-or-under-and-a-c-b-d").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart2", t -> t.getName().equals("start")),
                and(
                    or(
                        oneOf(
                            pattern(alarmA).expr("isAlarmA2", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB2", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isCFact2", t -> t.getName().equals("cFact"))
                    ),
                    pattern(toy).expr("isDFact2", t -> t.getName().equals("dFact"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        // First attempt — veto ordering
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // oneOf: 1 match -> MATCHED; OR satisfied via XOR branch
        insertAndFire(new Toy("cFact"));  // OR other branch — already satisfied; no-op
        insertAndFire(new Toy("alarmB")); // oneOf: 2 matches -> XOR reverts -> VETO -> sequence resets
        insertAndFire(new Toy("dFact"));  // sequence at step 0; dFact has no matching step
        assertThat(results)
            .as("XOR over-match resets the sequence even when OR was already satisfied by another branch (order a,c,b,d)")
            .isEmpty();
        // Second attempt — proves the sequence reset to step 0 (not stuck)
        insertAndFire(new Toy("start"));  // step 0 matches again -> sequence advances to the and() step
        insertAndFire(new Toy("cFact"));  // OR satisfied via non-XOR branch (no over-match risk)
        insertAndFire(new Toy("dFact"));  // AND: both bits set -> fires
        assertThat(results)
            .as("after veto-reset the sequence is reusable: second attempt with cFact+dFact must fire once")
            .containsExactly("fired");
    }

    @Test
    public void oneOfUnderOrUnderAndOrderCABDXorOvermatchVetoResets() {
        // and(or(oneOf(alarmA, alarmB), cFact), dFact).
        // Arrivals: start, c (OR satisfied via non-XOR branch, AND now waiting for dFact),
        //           a (XOR inside or — or step already consumed), b (XOR over-matches -> VETO -> reset), d.
        // Confirms: veto semantics hold regardless of which OR branch satisfies first.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("oneof-under-or-under-and-c-a-b-d").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart3", t -> t.getName().equals("start")),
                and(
                    or(
                        oneOf(
                            pattern(alarmA).expr("isAlarmA3", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB3", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isCFact3", t -> t.getName().equals("cFact"))
                    ),
                    pattern(toy).expr("isDFact3", t -> t.getName().equals("dFact"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("cFact"));  // OR satisfied via non-XOR branch; AND waiting for dFact
        insertAndFire(new Toy("alarmA")); // XOR child inside or; or step already consumed
        insertAndFire(new Toy("alarmB")); // XOR over-matches -> VETO -> sequence resets
        insertAndFire(new Toy("dFact"));  // sequence at step 0; dFact has no matching step
        assertThat(results)
            .as("XOR over-match resets the sequence even when OR was satisfied by the non-XOR branch first (order c,a,b,d)")
            .isEmpty();
    }

    @Test
    public void oneOfUnderOrUnderAndOrderABCDXorOvermatchBeforeOrSatisfiedVetoResets() {
        // and(or(oneOf(alarmA, alarmB), cFact), dFact).
        // Arrivals: start, a, b (XOR over-matches immediately -> VETO -> reset), c, d.
        // Confirms: veto fires even before the cFact branch of or arrives.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("oneof-under-or-under-and-a-b-c-d").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart4", t -> t.getName().equals("start")),
                and(
                    or(
                        oneOf(
                            pattern(alarmA).expr("isAlarmA4", t -> t.getName().equals("alarmA")),
                            pattern(alarmB).expr("isAlarmB4", t -> t.getName().equals("alarmB"))
                        ),
                        pattern(toy).expr("isCFact4", t -> t.getName().equals("cFact"))
                    ),
                    pattern(toy).expr("isDFact4", t -> t.getName().equals("dFact"))
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match -> MATCHED; OR satisfied via XOR branch
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches -> VETO -> sequence resets to step 0
        insertAndFire(new Toy("cFact"));  // no-op: sequence at step 0
        insertAndFire(new Toy("dFact"));  // no-op: sequence at step 0
        assertThat(results)
            .as("XOR over-match before cFact arrives resets the sequence; cFact and dFact are then no-ops (order a,b,c,d)")
            .isEmpty();
    }

    @Test
    public void wrappedOneOfAtIntermediateStepOvermatchVetoesLikeBareOneOf() {
        // Copilot review of PR #7160 (2026-10-29), ViewPatternBuilder.rewriteRevertingGateSteps().
        // sequence(start, and(oneOf(a, b)), ack) must behave like sequence(start, oneOf(a, b), ack):
        // a then b over-matches the oneOf, which vetoes the attempt, so ack must not fire.
        // Today only bare XOR/XNOR steps are folded into the following trigger; the AND-wrapped form
        // stays a separate runtime step that completes on 'a' and ignores 'b'.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("wrapped-oneof-intermediate").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStartW", t -> t.getName().equals("start")),
                and(
                    oneOf(
                        pattern(alarmA).expr("isAlarmAW", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmBW", t -> t.getName().equals("alarmB"))
                    )
                ),
                pattern(toy).expr("isAckW", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        // First attempt — over-match resets sequence to step 0
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB")); // over-match: must veto
        insertAndFire(new Toy("ack"));
        assertThat(results)
            .as("oneOf over-match inside and(...) at an intermediate step must veto the attempt")
            .isEmpty();

        // Second attempt — proves sequence reset to step 0 and remains reusable
        insertAndFire(new Toy("start"));  // re-matches step 0, advances to the oneOf step
        insertAndFire(new Toy("alarmA")); // exactly one matches oneOf
        insertAndFire(new Toy("ack"));    // positive step -> rule fires
        assertThat(results)
            .as("after veto-reset the sequence is reusable: second attempt with start+alarmA+ack must fire once")
            .containsExactly("fired");
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
