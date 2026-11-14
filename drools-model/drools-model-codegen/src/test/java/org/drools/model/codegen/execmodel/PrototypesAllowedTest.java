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
package org.drools.model.codegen.execmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.model.Index;
import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.model.impl.ModelImpl;
import org.drools.model.prototype.PrototypeVariable;
import org.drools.modelcompiler.KieBaseBuilder;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.conf.PrototypesOption;
import org.kie.api.io.ResourceType;
import org.kie.api.prototype.PrototypeFact;
import org.kie.api.prototype.PrototypeFactInstance;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.on;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.prototype.PrototypeDSL.protoPattern;
import static org.drools.model.prototype.PrototypeDSL.variable;
import static org.kie.api.prototype.PrototypeBuilder.prototype;

public class PrototypesAllowedTest {

    @Test
    public void testModel() {
        PrototypeVariable personV = variable(prototype( "Person" ).asFact());

        Rule rule = rule("adult" )
                .build(
                        protoPattern(personV)
                                .expr("age", Index.ConstraintType.GREATER_OR_EQUAL, 18 ),
                        on(personV).execute(p -> p.put("adult", true))
                );

        Model model = new ModelImpl().addRule(rule );
        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model );

        KieSession ksession = kieBase.newKieSession();

        PrototypeFact personFact = prototype("Person").asFact();

        PrototypeFactInstance sofia = personFact.newInstance();
        sofia.put("name", "Sofia" );
        sofia.put("age", 12 );

        ksession.insert(sofia);
        assertThat(ksession.fireAllRules()).isEqualTo(0);
        assertThat(sofia.get("adult")).isNull();

        PrototypeFactInstance mario = personFact.newInstance();
        mario.put("name", "Mario" );
        mario.put("age", 49 );

        ksession.insert(mario);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(mario.get("adult")).isEqualTo(true);
    }

    @Test
    public void testPrototypeDrl() {
        String str =
                """
                global java.util.List results
                rule Adult when
                  $p : Person( age >= 18 )
                then
                  $p.adult = true;
                  results.add($p.name);
                end
                """;

        KieSession ksession = new KieHelper()
                .addContent(str, ResourceType.DRL)
                .build(ExecutableModelProject.class, PrototypesOption.ALLOWED)
                .newKieSession();

        List<String> results = new ArrayList<>();
        ksession.setGlobal("results", results);

        PrototypeFact personFact = prototype("Person").asFact();

        PrototypeFactInstance sofia = personFact.newInstance();
        sofia.put("name", "Sofia" );
        sofia.put("age", 12 );

        ksession.insert(sofia);
        assertThat(ksession.fireAllRules()).isEqualTo(0);
        assertThat(sofia.get("adult")).isNull();

        PrototypeFactInstance mario = personFact.newInstance();
        mario.put("name", "Mario" );
        mario.put("age", 49 );

        ksession.insert(mario);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(mario.get("adult")).isEqualTo(true);

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo("Mario");
    }

    @Test
    public void testJoin() {
        String str =
                """
                package org.drools.prototype;
                
                rule "R1"
                when
                  $r : Result()
                  $p : Person(age >= 18)
                then
                  $r.value = $p.name + " can drink";
                end
               
                rule "R2"
                when
                  $r : Result()
                  $p : Person(age < 18)
                then
                  $r.value = $p.name + " can NOT drink";
                end
                """;

        KieSession ksession = new KieHelper()
                .addContent(str, ResourceType.DRL)
                .build(ExecutableModelProject.class, PrototypesOption.ALLOWED)
                .newKieSession();

        PrototypeFact personFact = prototype("Person").asFact();
        PrototypeFactInstance mark = personFact.newInstance();
        mark.put("name", "Mark" );
        mark.put("age", 17 );
        ksession.insert(mark);

        PrototypeFact resultFact = prototype("Result").asFact();
        PrototypeFactInstance result = resultFact.newInstance();
        ksession.insert(result);

        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(result.get("value")).isEqualTo("Mark can NOT drink");
    }


    @Test
    public void testAccessMapFieldsInMvel() {
        String str =
                """
                dialect "mvel"
                import java.util.Map
                global java.util.List results
                rule Adult when
                  $p : Map( age >= 18 )
                then
                  $p.adult = true;
                  results.add($p.name);
                end
                """;

        KieSession ksession = new KieHelper()
                .addContent(str, ResourceType.DRL)
                .build(ExecutableModelProject.class)
                .newKieSession();

        List<String> results = new ArrayList<>();
        ksession.setGlobal("results", results);

        Map<String, Object> sofia = new HashMap<>();
        sofia.put( "name", "Sofia" );
        sofia.put( "age", 12 );

        ksession.insert(sofia);
        assertThat(ksession.fireAllRules()).isEqualTo(0);
        assertThat(sofia.get("adult")).isNull();

        Map<String, Object> mario = new HashMap<>();
        mario.put( "name", "Mario" );
        mario.put( "age", 49 );

        ksession.insert(mario);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(mario.get("adult")).isEqualTo(true);

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo("Mario");
    }
}
