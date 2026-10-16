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

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class PatternDSLSequenceTest {

    private final Variable<Toy>          toy = declarationOf(Toy.class);
    private final Variable<Relationship> relationship = declarationOf(Relationship.class);
    private final List<String>           results = new ArrayList<>();
    private KieSession                   ksession;

    private final Rule anchorlessRule =
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
        ksession = makeKSessionAnchorless();

        // Step 1, then step 2 in the same cycle — replay feeds Toy through step-1 filter,
        // Relationship then arrives in the same propagation and satisfies step 2.
        insertAndFire(
           new Toy("ball"),
           new Relationship("go", "done")
        );

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void sequenceFiresWithSeparateInserts() {
        ksession = makeKSessionAnchorless();

        // Step 1, then fireAll() — rule should NOT fire yet
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
        // This test verifies the ordering contract with an anchored rule: step facts
        // arrive after the sequencer starts, so insertion order is enforced.
        // An anchorless rule cannot test this because both facts would be in WM
        // simultaneously and replayed through the step-1 filter regardless of insertion order.
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule anchoredRule = rule("seq-order-rule").build(
                pattern(personV),
                sequence(
                    pattern(toyV).expr("toy-filter", t -> t.getName().equals("ball")),
                    pattern(relV).expr("rel-filter",  r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(anchoredRule);
        insertAndFire(new Person("anchor"));

        // Relationship arrives before Toy — must not fire
        insertAndFire(
            new Relationship("go", "done"),
            new Toy("ball"));

        assertThat(results).isEmpty();
    }

    @Test
    public void sequenceDoesNotFireWithoutToy() {
        ksession = makeKSessionAnchorless();

        // Step 2 arrives without step 1 — rule must NOT fire
        insertAndFire(
            new Relationship("go", "done")
        );

        assertThat(results).isEmpty();
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

    private KieSession makeKSessionAnchorless() {
        final Model model = new ModelImpl().addRule(anchorlessRule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

    private KieSession makeKSession(Rule rule) {
        final Model model = new ModelImpl().addRule(rule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

}
