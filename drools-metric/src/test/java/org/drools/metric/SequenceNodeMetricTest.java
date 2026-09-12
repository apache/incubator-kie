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
package org.drools.metric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.KieBaseBuilder;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class SequenceNodeMetricTest extends AbstractMetricTest {

    /**
     * Builds a KieBase with a single rule whose LHS is: String() then sequence(Integer(), Long()).
     * Inserting a String activates the sequencer; inserting an Integer then a Long fires the rule.
     */
    private KieSession buildSequenceSession(List<String> results) {
        Variable<String>  anchor  = declarationOf(String.class);
        Variable<Integer> step1   = declarationOf(Integer.class);
        Variable<Long>    step2   = declarationOf(Long.class);

        Rule r = rule("sequence-metric-rule").build(
                pattern(anchor),
                sequence(
                        pattern(step1).expr("step1-expr", i -> i > 0),
                        pattern(step2).expr("step2-expr", l -> l > 0)
                ),
                execute(() -> results.add("fired"))
        );

        Model model = new ModelImpl().addRule(r);
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        return kieBase.newKieSession();
    }

    @Test
    public void sequenceNodeMetricIsRecordedWhenMetricsEnabled() {
        List<String> results = new ArrayList<>();
        KieSession ksession = buildSequenceSession(results);

        // Activate sequencer
        ksession.insert("anchor");
        ksession.fireAllRules();

        // Advance sequence: step 1
        ksession.insert(1);
        ksession.fireAllRules();

        // Advance sequence: step 2 — rule fires
        ksession.insert(1L);
        ksession.fireAllRules();

        ksession.dispose();

        assertThat(results).containsExactly("fired");

        // Metrics must have been recorded for the SequenceNode
        Collection<Timer> timers = Search.in(registry)
                .name("org.drools.metric.elapsed.time.per.evaluation")
                .timers();
        assertThat(timers).isNotEmpty();
    }

    @Test
    public void sequenceNodeMetricIsNotRecordedWhenMetricsDisabled() {
        // Override: disable metrics for this test
        System.setProperty("drools.metric.logger.enabled", "false");
        org.drools.metric.util.MetricLogUtils.recreateInstance();

        try {
            List<String> results = new ArrayList<>();
            KieSession ksession = buildSequenceSession(results);

            ksession.insert("anchor");
            ksession.fireAllRules();
            ksession.insert(1);
            ksession.fireAllRules();
            ksession.insert(1L);
            ksession.fireAllRules();
            ksession.dispose();

            assertThat(results).containsExactly("fired");

            // No timers should be registered when metrics are disabled
            Collection<Timer> timers = Search.in(registry)
                    .name("org.drools.metric.elapsed.time.per.evaluation")
                    .timers();
            assertThat(timers).isEmpty();
        } finally {
            System.setProperty("drools.metric.logger.enabled", "true");
            org.drools.metric.util.MetricLogUtils.recreateInstance();
        }
    }
}
