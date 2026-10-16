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

import org.drools.model.DSL;
import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.ViewItemBuilder;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;

/**
 * Executable models compiled against a release that predates {@code sequence()} link to
 * {@code DSL.not(ViewItemBuilder, ViewItemBuilder...)} with return type {@link ExprViewItem}.
 * The return type is part of the JVM descriptor, so changing it makes those models fail with
 * {@link NoSuchMethodError}. Tests that are recompiled with the codebase cannot see this.
 */
public class DslNotBinaryCompatibilityTest {

    @Test
    public void dslNotKeepsPreviousReleaseDescriptor() throws NoSuchMethodException {
        assertThat(DSL.class.getMethod("not", ViewItemBuilder.class, ViewItemBuilder[].class).getReturnType())
                .isEqualTo(ExprViewItem.class);
    }

    @Test
    public void plainExistentialNotOutsideSequenceStillWorks() {
        Variable<Toy> ball = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);
        List<String> results = new ArrayList<>();

        Rule rule = rule("plain-not").build(
                pattern(ball).expr("isBall", t -> t.getName().equals("ball")),
                DSL.not(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                execute(() -> results.add("fired")));

        Model model = new ModelImpl().addRule(rule);
        KieSession ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
        try {
            ksession.insert(new Toy("ball"));
            ksession.fireAllRules();
        } finally {
            ksession.dispose();
        }
        assertThat(results).containsExactly("fired");
    }
}
