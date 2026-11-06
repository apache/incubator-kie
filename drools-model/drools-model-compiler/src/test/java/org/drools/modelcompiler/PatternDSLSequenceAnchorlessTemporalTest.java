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
import org.drools.modelcompiler.domain.Relationship;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.entryPoint;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

/**
 * Tests for strict temporal semantics of anchorless sequences.
 *
 * Under the first-pattern-as-anchor design, an anchorless {@code sequence(A, B)} means:
 * <ul>
 *   <li>The sequencer starts when the first matching fact of type A arrives via the LIAN.</li>
 *   <li>Only facts that arrive after A can satisfy B.</li>
 *   <li>Facts in WM before A arrived are invisible to the sequencer.</li>
 *   <li>B arriving before A must not satisfy the sequence.</li>
 * </ul>
 */
public class PatternDSLSequenceAnchorlessTemporalTest {

    private final List<String> results = new ArrayList<>();
    private KieSession ksession;

    @AfterEach
    public void tearDown() {
        results.clear();
        if (ksession != null) {
            ksession.dispose();
            ksession = null;
        }
    }

    private void insertAndFire(Object... facts) {
        for (Object f : facts) {
            ksession.insert(f);
        }
        ksession.fireAllRules();
    }

    private KieSession makeKSession(Rule rule) {
        final Model model = new ModelImpl().addRule(rule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

    /**
     * User story 3 — strict ordering for anchorless sequences.
     *
     * B (Relationship) arrives before A (Toy). The sequence must NOT fire because
     * the sequencer has not started yet when B arrived.
     */
    @Test
    public void bBeforeAMustNotFireAnchorlessSequence() {
        Variable<Toy>          toyV = declarationOf(Toy.class);
        Variable<Relationship> relV = declarationOf(Relationship.class);

        Rule rule = rule("b-before-a-rule").build(
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        // Step-2 fact arrives before step-1 fact — sequence must NOT fire.
        insertAndFire(new Relationship("go", "done"));
        assertThat(results)
                .as("sequence must not fire when step-2 arrives before step-1")
                .isEmpty();

        // Now step-1 arrives — but step-2 is already in the past, must not fire.
        insertAndFire(new Toy("ball"));
        assertThat(results)
                .as("sequence must not fire: step-2 arrived before step-1 started the sequencer")
                .isEmpty();
    }

    /**
     * User story 3 — strict ordering within a single fireAllRules() cycle.
     *
     * B (Relationship) is inserted before A (Toy), both before one fireAllRules().
     * The sequence must NOT fire: B reached the AlphaAdapter while no step-1 filter
     * was active, so it is not captured.
     */
    @Test
    public void bBeforeAInSameCycleMustNotFireAnchorlessSequence() {
        Variable<Toy>          toyV = declarationOf(Toy.class);
        Variable<Relationship> relV = declarationOf(Relationship.class);

        Rule rule = rule("b-before-a-same-cycle-rule").build(
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        insertAndFire(new Relationship("go", "done"), new Toy("ball"));

        assertThat(results)
                .as("sequence must not fire when step-2 is inserted before step-1 in the same cycle")
                .isEmpty();
    }

    /**
     * User story 7 — A then B inserted in the same cycle (before one fireAllRules()) fires.
     */
    @Test
    public void aThenBInSameCycleFiresAnchorlessSequence() {
        Variable<Toy>          toyV = declarationOf(Toy.class);
        Variable<Relationship> relV = declarationOf(Relationship.class);

        Rule rule = rule("a-then-b-same-cycle-rule").build(
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        insertAndFire(new Toy("ball"), new Relationship("go", "done"));

        assertThat(results)
                .as("A inserted before B in the same cycle must fire")
                .containsExactly("fired");
    }

    /**
     * User story 2 + 6 — A then B in separate fireAllRules() cycles fires on cycle 2.
     */
    @Test
    public void aThenBInSeparateCyclesFires() {
        Variable<Toy>          toyV = declarationOf(Toy.class);
        Variable<Relationship> relV = declarationOf(Relationship.class);

        Rule rule = rule("separate-cycles-rule").build(
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        // Cycle 1: step-1 fact arrives, sequence starts.
        insertAndFire(new Toy("ball"));
        assertThat(results).isEmpty();

        // Cycle 2: step-2 fact arrives, sequence fires.
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).containsExactly("fired");
    }

    /**
     * User story 9 — three-step anchorless sequence with the same type in steps 1 and 3.
     *
     * sequence(Toy("ball"), Relationship("go"), Toy("doll")) must fire only when
     * all three arrive in order, starting from A (Toy("ball")).
     */
    @Test
    public void threeStepAnchorlessWithSameTypeInFirstAndThirdStep() {
        Variable<Toy>          toyV1 = declarationOf(Toy.class);
        Variable<Relationship> relV  = declarationOf(Relationship.class);
        Variable<Toy>          toyV2 = declarationOf(Toy.class);

        Rule rule = rule("three-step-anchorless-type-reuse").build(
                sequence(
                        pattern(toyV1).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",    r -> r.getStart().equals("go")),
                        pattern(toyV2).expr("is-doll",  t -> t.getName().equals("doll"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        // Step-1: ball arrives — sequencer starts at step 2
        insertAndFire(new Toy("ball"));
        assertThat(results).isEmpty();

        // Step-2: Relationship arrives — sequencer advances to step 3
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).isEmpty();

        // Step-3: doll arrives — sequence completes
        insertAndFire(new Toy("doll"));
        assertThat(results).containsExactly("fired");
    }

    /**
     * Entry-point contract — anchorless sequence wired to a named entry point.
     *
     * {@code sequence(Toy from ep1, Relationship from ep1)} fires only when both
     * steps are inserted via {@code "ep1"} in order. Facts inserted via the default
     * entry point are invisible to the sequencer, because the OTN/AlphaAdapter ring
     * is built against the entry-point node that was active in the build context when
     * the sequence was compiled ({@code context.getCurrentEntryPoint()} — not a
     * hardcoded default).
     */
    @Test
    public void anchorlessSequenceOnNamedEntryPointFiresOnlyForThatEntryPoint() {
        Variable<Toy>          toyV = declarationOf(Toy.class);
        Variable<Relationship> relV = declarationOf(Relationship.class);

        Rule rule = rule("ep-seq-rule").build(
                sequence(
                        pattern(toyV, entryPoint("ep1")).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV, entryPoint("ep1")).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);

        // Facts on the DEFAULT entry point must be invisible to the sequencer.
        ksession.insert(new Toy("ball"));
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();
        assertThat(results)
                .as("facts on the default entry point must not satisfy a sequence wired to ep1")
                .isEmpty();

        // Step 1 via ep1 — sequencer starts.
        ksession.getEntryPoint("ep1").insert(new Toy("ball"));
        ksession.fireAllRules();
        assertThat(results).isEmpty();

        // Step 2 via ep1 — sequence completes.
        ksession.getEntryPoint("ep1").insert(new Relationship("go", "done"));
        ksession.fireAllRules();
        assertThat(results)
                .as("sequence wired to ep1 must fire when both steps arrive in order via ep1")
                .containsExactly("fired");
    }
}
