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
import static org.drools.model.PatternDSL.xnor;

/**
 * Specification tests for xnor() as a sequence step.
 *
 * Contract: zero or all of the XNOR children must match before the positive trigger.
 * Write {@code sequence(..., xnor(a, b), trigger)} — the compiler wraps the pair into
 * {@code and(xnor(a, b), trigger)} automatically.
 */
public class PatternDSLSequenceXnorStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void xnorNeitherMatch_fires() {
        // sequence(xnor(alarmA, alarmB), ack): neither alarm inserted (0 matches) → XNOR matched → ack arrives → fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-none").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xnor(
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
        insertAndFire(new Toy("ack")); // ack arrives with 0 alarm matches → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xnorOnlyOneMatch_doesNotFire() {
        // sequence(xnor(alarmA, alarmB), ack): exactly one alarm inserted → XNOR unmatched → ack arrives → does not fire
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-one").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xnor(
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
        insertAndFire(new Toy("alarmA")); // exactly one → XNOR transitions to unmatched
        insertAndFire(new Toy("ack"));    // ack arrives, but XNOR is unmatched → does not fire
        assertThat(results).isEmpty();
    }

    @Test
    public void xnorBothMatch_fires() {
        // sequence(xnor(alarmA, alarmB), ack): both alarms inserted → XNOR matched → ack arrives → fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-both").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xnor(
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
        insertAndFire(new Toy("alarmA")); // 1 match → unmatched
        insertAndFire(new Toy("alarmB")); // 2 matches → MATCHED again!
        insertAndFire(new Toy("ack"));    // ack arrives, XNOR matched → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xnorBareTopLevel_throws() {
        // sequence(xnor(a, b)) — bare XNOR as the trailing step is rejected during rule.build()
        // by ViewPatternBuilder's trailing-step guard.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xnor-bare").build(
            pattern(person),
            sequence(
                xnor(
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
    public void xnorBareFollowedByStep_sugarFiresOnNeitherMatch() {
        // sequence(xnor(a, b), c) — same rule, different name to confirm rewrite is idempotent.
        // Neither alarm inserted (0 matches) → XNOR matched → ack arrives → fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-bare-followed-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xnor(
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
        insertAndFire(new Toy("ack")); // ack arrives with 0 alarm matches → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xnorBareFollowedByStep_sugarDoesNotFireOnOneMatch() {
        // sequence(xnor(a, b), c) sugar: exactly one alarm → XNOR unmatched → ack arrives but no fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-bare-followed-no-fire").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xnor(
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
        insertAndFire(new Toy("alarmA")); // exactly one → XNOR transitions to unmatched
        insertAndFire(new Toy("ack"));    // ack arrives but XNOR unmatched → does not fire
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
