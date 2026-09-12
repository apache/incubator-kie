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
import org.drools.modelcompiler.domain.Relationship;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class PatternDSLSequenceLifecycleTest {

    private final Variable<Person>       person       = declarationOf(Person.class);
    private final Variable<Toy>          toy          = declarationOf(Toy.class);
    private final Variable<Relationship> relationship = declarationOf(Relationship.class);

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

    @Test
    public void retractAnchorStopsSequencer() {
        ksession = makeKSession();

        FactHandle anchorHandle = ksession.insert(new Person("anchor"));
        ksession.fireAllRules();                    // activates sequencer

        ksession.retract(anchorHandle);
        ksession.fireAllRules();                    // stop() must be called

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step-1 — sequencer gone, must not fire

        assertThat(results).isEmpty();
    }

    @Test
    public void retractAnchorMidSequenceStopped() {
        ksession = makeKSession();

        FactHandle anchorHandle = ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step 1 consumed, step 2 waiting

        ksession.retract(anchorHandle);
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // step-2 — sequencer stopped, must not fire

        assertThat(results).isEmpty();
    }

    @Test
    public void twoAnchorsConcurrentlyBothFire() {
        ksession = makeKSession();

        ksession.insert(new Person("anchor-A"));
        ksession.insert(new Person("anchor-B"));
        ksession.fireAllRules();                    // both sequencers start

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step-1 consumed by BOTH sequencers

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // step-2 consumed by BOTH sequencers

        assertThat(results).hasSize(2);
    }

    @Test
    public void twoAnchorsSingleStepSequenceBothFire() {
        ksession = makeKSessionSingleStep();

        ksession.insert(new Person("anchor-A"));
        ksession.insert(new Person("anchor-B"));
        ksession.fireAllRules();                    // both single-step sequencers start

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        assertThat(results)
                .as("each anchor's sequence must complete and fire the rule independently")
                .hasSize(2);
    }

    @Test
    public void sequenceRestartAfterUpdate() {
        ksession = makeKSession();

        Person anchor = new Person("anchor");
        FactHandle anchorHandle = ksession.insert(anchor);
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step 1 consumed

        ksession.update(anchorHandle, anchor);      // update restarts the sequence
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // step-2 without a new step-1 — must NOT fire

        assertThat(results).isEmpty();
    }

    @Test
    public void sequenceDoesNotFireAgainAfterCompletion() {
        ksession = makeKSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // sequence complete — fired once

        assertThat(results).containsExactly("fired");

        // A second step-1 event after completion must not restart the sequence
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        assertThat(results).hasSize(1);             // still only one firing
    }

    @Test
    public void sequenceRefirableAfterUpdateFollowingCompletion() {
        ksession = makeKSession();

        Person anchor = new Person("anchor");
        FactHandle anchorHandle = ksession.insert(anchor);
        ksession.fireAllRules();

        // First run: complete the sequence
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();
        assertThat(results).containsExactly("fired");   // fired once

        // Restart: update resets the sequencer
        ksession.update(anchorHandle, anchor);
        ksession.fireAllRules();

        // Second run: complete the sequence again
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();

        assertThat(results).hasSize(2);                 // fired a second time
    }

    @Test
    public void retractStepEventDoesNotUndoStep() {
        ksession = makeKSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        FactHandle toyHandle = ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step-1 consumed; sequence now at step-2

        ksession.retract(toyHandle);               // retract the step-1 fact — no-op for the sequencer
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // step-2 arrives — sequence is still at step-2, fires

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void updateStepEventDoesNotUndoStep() {
        ksession = makeKSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        Toy toy = new Toy("ball");
        FactHandle toyHandle = ksession.insert(toy);
        ksession.fireAllRules();                    // step-1 consumed; sequence now at step-2

        ksession.update(toyHandle, toy);            // update the step-1 fact — no-op for the sequencer
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // step-2 arrives — sequence fires

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void removedRuleNoLongerReceivesStepEvents() {
        Variable<Person> personV = declarationOf(Person.class);
        Variable<Toy>    toyV    = declarationOf(Toy.class);

        Rule rule = rule("seq-remove-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("fired"))
        );

        Model model = new ModelImpl().addRule(rule);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        ksession = kieBase.newKieSession();

        // Start the sequencer
        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        // Remove the rule from the KieBase while the session is active
        kieBase.removeRule("defaultpkg", "seq-remove-rule");

        // Insert the step event — the removed rule must NOT fire
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();

        assertThat(results)
                .as("no rule should fire after it has been removed from the KieBase")
                .isEmpty();
    }

    private KieSession makeKSession() {
        Rule rule = rule("lifecycle-rule").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relationship).expr("is-go", r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );
        Model model = new ModelImpl().addRule(rule);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

    private KieSession makeKSessionSingleStep() {
        Rule rule = rule("lifecycle-single-step").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("fired"))
        );
        Model model = new ModelImpl().addRule(rule);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }
}
