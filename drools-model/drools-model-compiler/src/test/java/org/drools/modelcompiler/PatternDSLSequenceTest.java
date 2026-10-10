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
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.xor;

public class PatternDSLSequenceTest {

    private final Variable<Person>       person = declarationOf(Person.class);
    private final Variable<Toy>          toy = declarationOf(Toy.class);
    private final Variable<Relationship> relationship = declarationOf(Relationship.class);
    private final List<String>           results = new ArrayList<>();
    private KieSession                   ksession;

    private final Rule rule =
            rule("seq-rule").build(
                sequence(
                    pattern(toy).expr("toy-filter",
                            t -> t.getName().equals("ball")),
                    pattern(relationship).expr("rel-filter",
                            r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
    );

    @Test
    public void sequenceFiresWhenToyThenRelationship() {
        ksession = makeKSession();

        // LHS anchor — activates the sequencer
        insertAndFire(
           new Person("anchor")
        );

        // Step 1, then step 2 — rule should fire
        insertAndFire(
           new Toy("ball"),
           new Relationship("go", "done")
        );

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void sequenceFiresWithSeparateInserts() {
        ksession = makeKSession();

        // LHS anchor — activates the sequencer
        insertAndFire(
                new Person("anchor")
        );

        // Step 1, then fireAll() — rule should NOT fire
        insertAndFire(
                new Toy("ball")
        );

        assertThat(results).isEmpty();

        // Then step 2 — rule should fire
        insertAndFire(
                new Relationship("go", "done")
        );

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void sequenceDoesNotFireWithoutCorrectOrder() {
        ksession = makeKSession();

        // LHS anchor — activates the sequencer
        insertAndFire(
           new Person("anchor")
        );

        // Step 1, then step 2 — sequence expects Toy then Relationship. We provide Relationship then Toy
        insertAndFire(
            new Relationship("go", "done"),
            new Toy("ball"));

        assertThat(results).isEmpty();
    }

    @Test
    public void sequenceDoesNotFireWithoutToy() {
        ksession = makeKSession();

        insertAndFire(
            new Person("anchor")
        );

        // Step 2 arrives without step 1 — rule must NOT fire
        insertAndFire(
            new Relationship("go", "done")
        );

        assertThat(results).isEmpty();
    }

    @Test
    public void xorReversionClearsGrandparentBit() {
        Variable<Toy>          toyVar   = declarationOf(Toy.class);
        Variable<Relationship> relVar   = declarationOf(Relationship.class);
        Variable<Adult>        adultVar = declarationOf(Adult.class);

        Rule deepRule = rule("deep-revert-rule").build(
                sequence(
                    and(
                        and(
                            xor(
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

    @AfterEach
    public void tearDown() {
        results.clear();
        ksession.dispose();
    }

    private void insertAndFire(Object... facts) {
        for (Object fact : facts) {
            ksession.insert(fact);
        }
        ksession.fireAllRules();
    }

    private KieSession makeKSession() {
        final Model model = new ModelImpl().addRule(rule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

}
