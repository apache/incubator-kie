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
import java.util.concurrent.atomic.AtomicInteger;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionsPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class SequenceReviewProbeTest {

    private final List<String> results = new ArrayList<>();
    private KieSession ksession;

    @AfterEach
    public void tearDown() {
        results.clear();
        if (ksession != null) {
            ksession.dispose();
        }
    }

    @Test
    public void plainRuleAndSequenceRuleShareAnEventType() {
        Variable<Toy> toy = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        Rule plainRule = rule("plain-rule").build(
                pattern(toy).expr("is-ball", t -> t.getName().equals("ball")),
                execute(() -> results.add("plain:ball"))
        );

        Rule seqRule = rule("seq-rule").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("seq-is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("seq:ball"))
        );

        Model model = new ModelImpl().addRule(plainRule).addRule(seqRule);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        ksession = kieBase.newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        // Both the plain rule and the sequence rule must fire.
        assertThat(results).contains("plain:ball");
        assertThat(results).contains("seq:ball");
    }

    @Test
    public void sameTypeInTwoSteps() {
        Variable<Toy>    toy    = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        Rule rule = rule("ball-then-doll").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(toy).expr("is-doll", t -> t.getName().equals("doll"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(
                new org.drools.model.impl.ModelImpl().addRule(rule))
                .newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        ksession.insert(new Toy("doll"));
        ksession.fireAllRules();

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void stepWithTwoConstraintsIsUnsatisfiable() {
        Variable<Toy>    toy    = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        // A step that requires name == "ball" AND name == "doll" simultaneously —
        // logically impossible, must never match any real Toy.
        Rule rule = rule("impossible-step").build(
                pattern(person),
                sequence(
                        pattern(toy)
                                .expr("is-ball", t -> t.getName().equals("ball"))
                                .expr("is-doll", t -> t.getName().equals("doll"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(
                new org.drools.model.impl.ModelImpl().addRule(rule))
                .newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        // A Toy("ball") satisfies "is-ball" but not "is-doll" — must NOT fire.
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        assertThat(results).isEmpty();
    }

    @Test
    public void stepWithTwoConstraintsBothSatisfiedFires() {
        Variable<Toy>    toy    = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        // A step that requires name starts with "t" AND length > 3.
        // Toy("toy-car") satisfies both — CombinedAlphaConstraint.isAllowed()
        // must return true (the all-pass branch at the end of the loop).
        Rule rule = rule("two-constraint-step").build(
                pattern(person),
                sequence(
                        pattern(toy)
                                .expr("starts-with-t", t -> t.getName().startsWith("t"))
                                .expr("longer-than-3",  t -> t.getName().length() > 3)
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(
                new org.drools.model.impl.ModelImpl().addRule(rule))
                .newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        // Toy("toy-car"): starts with "t" ✓, length 7 > 3 ✓ — both constraints pass.
        ksession.insert(new Toy("toy-car"));
        ksession.fireAllRules();

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void joinAfterSequenceHasCorrectTupleWidth() {
        Variable<Person> anchorV   = declarationOf(Person.class);
        Variable<Toy>    toyV      = declarationOf(Toy.class);
        Variable<Person> observerV = declarationOf(Person.class);

        Rule rule = rule("join-after-sequence").build(
                pattern(anchorV).expr("is-anchor",   p -> p.getName().equals("anchor")),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                pattern(observerV).expr("is-observer", p -> p.getName().equals("observer")),
                execute(() -> results.add("fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        AtomicInteger capturedHandleCount = new AtomicInteger(-1);
        ksession.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void matchCreated(MatchCreatedEvent event) {
                capturedHandleCount.set(event.getMatch().getFactHandles().size());
            }
        });

        ksession.insert(new Person("anchor"));
        ksession.insert(new Person("observer"));
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        assertThat(results).containsExactly("fired");
        assertThat(capturedHandleCount.get())
                .as("match must contain both anchor and observer fact handles")
                .isEqualTo(2);
    }

    @Test
    public void stepWithNoConstraintThrowsDescriptiveError() {
        Variable<Toy>    toy    = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        Rule rule = rule("unconstrained-step").build(
                pattern(person),
                sequence(
                        pattern(toy)
                ),
                execute(() -> results.add("fired"))
        );

        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(() ->
                KieBaseBuilder.createKieBaseFromModel(
                        new org.drools.model.impl.ModelImpl().addRule(rule)),
                Exception.class))
                .as("step with no constraint must throw a descriptive build error")
                .isNotNull()
                .isNotInstanceOf(IndexOutOfBoundsException.class)
                .hasMessageContaining("sequence step");
    }

    @Test
    public void pooledSessionReuseShouldNotLeakSequenceState() {
        Variable<Toy>    toy    = declarationOf(Toy.class);
        Variable<Person> person = declarationOf(Person.class);

        Rule rule = rule("seq-rule").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("fired"))
        );

        InternalKnowledgeBase kbase = KieBaseBuilder.createKieBaseFromModel(
                new ModelImpl().addRule(rule));
        KieSessionsPool pool = kbase.newKieSessionsPool(1);

        KieSession s1 = pool.newKieSession();
        s1.insert(new Person("anchor"));
        s1.fireAllRules();
        // Do NOT complete the sequence — dispose mid-flight.
        s1.dispose();

        KieSession s2 = pool.newKieSession();
        assertThat(s2).as("pool of 1 must return the same physical session").isSameAs(s1);

        s2.insert(new Person("anchor"));
        s2.fireAllRules();
        s2.insert(new Toy("ball"));
        s2.fireAllRules();
        int secondFires = results.size();

        pool.shutdown();

        assertThat(secondFires)
                .as("sequence must fire exactly once in session #2 — " +
                    "stale DynamicFilter/SignalAdapter state from session #1 must not cause extra or missing fires")
                .isEqualTo(1);
    }

}
