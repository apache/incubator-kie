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
        ksession = makeKSessionAnchorless();

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
    public void retractAnchorFactStopsAnchorlessSequencer() {
        ksession = makeKSessionAnchorless();

        // In the first-pattern-as-anchor design, the Toy fact IS the LIAN anchor.
        // Retracting it stops the sequencer — same contract as for anchored rules.
        FactHandle toyHandle = ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                    // step-0 fires; sequencer at step 1

        ksession.retract(toyHandle);               // retract the anchor fact — stops the sequencer
        ksession.fireAllRules();

        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                    // sequencer stopped, must NOT fire

        assertThat(results)
                .as("retracting the anchor fact must stop the anchorless sequencer")
                .isEmpty();
    }

    @Test
    public void updateAnchorFactRestartsAnchorlessSequencerAndAdvancesPastStep0() {
        ksession = makeKSessionAnchorless();

        // In the first-pattern-as-anchor design, updating the anchor fact triggers a stop+start
        // cycle.  Since the anchor fact IS the step-0 match, step 0 fires again immediately after
        // restart, and the sequencer advances to step 1.
        Toy toy = new Toy("ball");
        FactHandle toyHandle = ksession.insert(toy);
        ksession.fireAllRules();                    // step-0 fires; sequencer at step 1

        ksession.update(toyHandle, toy);            // update anchor fact — stop+restart
        ksession.fireAllRules();                    // step-0 fires again; sequencer back at step 1

        // Sequencer is at step 1 again, so Relationship satisfies it.
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();

        assertThat(results)
                .as("after anchor update, sequencer restarts and step-0 fires immediately; Relationship satisfies step 1")
                .containsExactly("fired");
    }

    @Test
    public void updateAnchorFactToNonMatchingValueKepsSequencerAtStep0() {
        ksession = makeKSessionAnchorless();

        // Step-0 predicate is t.getName().equals("ball").
        // After a stop+restart cycle the anchor fact is re-asserted to step 0.
        // When the updated fact no longer satisfies the predicate, step 0 must
        // reject it silently — the sequencer stays at step 0 and must not fire
        // when step 1 arrives without a prior step-0 match.
        Toy toy = new Toy("ball");
        FactHandle toyHandle = ksession.insert(toy);
        ksession.fireAllRules();                    // step-0 fires; sequencer at step 1

        // Mutate the fact so it no longer satisfies step 0's predicate.
        toy.setName("not-ball");
        ksession.update(toyHandle, toy);            // stop+restart; re-fire step 0 with "not-ball"
        ksession.fireAllRules();                    // step-0 must reject "not-ball"; sequencer stays at step 0

        // Step-1 event arrives, but sequencer is at step 0 — must NOT complete.
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();

        assertThat(results)
                .as("step-0 predicate rejected the updated fact; sequencer must stay at step 0 and not fire when step 1 arrives without a new step-0 match")
                .isEmpty();
    }

    @Test
    public void removedRuleNoLongerReceivesStepEvents() {
        Variable<Toy> toyV = declarationOf(Toy.class);

        Rule rule = rule("seq-remove-rule").build(
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("fired"))
        );

        Model model = new ModelImpl().addRule(rule);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        ksession = kieBase.newKieSession();

        // Start the sequencer (InitialFact activates it)
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

    private KieSession makeKSessionAnchorless() {
        // No anchor pattern: sequencer starts via InitialFact on session creation.
        Rule rule = rule("lifecycle-rule-anchorless").build(
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
}
