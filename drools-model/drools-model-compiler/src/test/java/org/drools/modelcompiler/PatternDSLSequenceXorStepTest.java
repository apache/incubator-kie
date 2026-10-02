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
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.xor;

/**
 * Specification tests for xor() as a sequence step.
 *
 * Contract: exactly one of the XOR children must match before the positive trigger.
 * Write {@code sequence(..., xor(a, b), trigger)} — the compiler wraps the pair into
 * {@code and(xor(a, b), trigger)} automatically.
 * Zero matches → AND gate never fires, sequence does not advance.
 * Two or more matches → XOR gate reverts to UNMATCHED, AND gate never fires.
 */
public class PatternDSLSequenceXorStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void xorExactlyOneMatch_fires() {
        // sequence(xor(alarmA, alarmB), ack): exactly alarmA inserted, then ack → rule fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-one-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
    public void xorNeitherMatch_doesNotFire() {
        // sequence(xor(alarmA, alarmB), ack): neither alarm inserted → XOR stays UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-none").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
    public void xorBothMatch_rollsBackAndDoesNotFire() {
        // sequence(xor(alarmA, alarmB), ack): both alarms → XOR reverts to UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-both").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
    public void xorBareTopLevel_throws() {
        // sequence(xor(a, b)) — bare XOR as the trailing step is rejected during rule.build()
        // by ViewPatternBuilder's trailing-step guard.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-bare").build(
            pattern(person),
            sequence(
                xor(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing not()/nor()/xor()/xnor() requires a following positive step");
    }

    @Test
    public void xorThreeOperandsBareTopLevel_throws() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-three-operands-bare").build(
            pattern(person),
            sequence(
                xor(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB")),
                    pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing not()/nor()/xor()/xnor() requires a following positive step");
    }

    @Test
    public void xorBareFollowedByStep_sugarFiresOnExactlyOneMatch() {
        // sequence(xor(a, b), c) — same rule, different name to confirm rewrite is idempotent.
        // Exactly one alarm inserted, then ack → rule fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
    public void xorBareFollowedByStep_sugarDoesNotFireOnBothMatch() {
        // sequence(xor(a, b), c) sugar: both alarms inserted → XOR reverts to UNMATCHED → ack arrives but no fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-no-fire").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
