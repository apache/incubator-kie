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
package org.drools.serialization.protobuf;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.StringReader;

import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.marshalling.MarshallerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A dynamic fact is one whose setters notify the session, so that changing it re-evaluates the
 * rules matching it without an explicit {@code update}. The notification is a JavaBeans
 * {@link PropertyChangeListener} registration, and the listener is the entry point itself, which
 * is not serializable: {@link PropertyChangeSupport} drops it on write and the unmarshalled fact
 * comes back with an empty listener list. These tests pin that a round trip keeps the fact
 * dynamic, for both ways a fact becomes dynamic.
 */
public class DynamicFactMarshallingTest {

    private final DeserializationFilterTestSupport filterSupport = new DeserializationFilterTestSupport();

    @BeforeEach
    public void setUpDeserializationFilter() {
        // the fact carries its PropertyChangeSupport into the blob
        filterSupport.setUp("org.drools.serialization.protobuf.DynamicFactMarshallingTest$DynamicFact",
                            "java.beans.*",
                            "java.util.*");
    }

    @AfterEach
    public void clearDeserializationFilter() {
        filterSupport.tearDown();
    }

    private static final String RULE =
            "import " + DynamicFact.class.getCanonicalName() + ";\n" +
            "rule \"name changed\"\n" +
            "when\n" +
            "    DynamicFact( name == \"changed\" )\n" +
            "then\n" +
            "end\n";

    private static final String DECLARED_DYNAMIC_RULE =
            "import " + DynamicFact.class.getCanonicalName() + ";\n" +
            "declare DynamicFact\n" +
            "    @propertyChangeSupport\n" +
            "end\n" +
            RULE;

    /**
     * Sanity check on a session that was never marshalled: this is the behaviour the round trip
     * has to preserve.
     */
    @Test
    public void dynamicallyInsertedFact_inALiveSession_reevaluatesRulesOnSetter() {
        final KieBase kieBase = knowledgeBase(RULE);
        final KieSession session = kieBase.newKieSession();
        try {
            final DynamicFact fact = new DynamicFact("initial");
            dynamicInsert(session, fact);

            assertThat(session.fireAllRules()).isZero();

            fact.setName("changed");

            assertThat(session.fireAllRules()).isEqualTo(1);
        } finally {
            session.dispose();
        }
    }

    @Test
    public void dynamicallyInsertedFact_afterRoundTrip_reevaluatesRulesOnSetter() throws Exception {
        final KieBase kieBase = knowledgeBase(RULE);
        final KieSession session = kieBase.newKieSession();
        dynamicInsert(session, new DynamicFact("initial"));
        session.fireAllRules();

        final KieSession restored = roundTrip(kieBase, session);
        try {
            final DynamicFact restoredFact = theFactIn(restored);
            restoredFact.setName("changed");

            assertThat(restored.fireAllRules())
                    .as("the restored fact lost its PropertyChangeListener, so the setter did not notify the session")
                    .isEqualTo(1);
        } finally {
            restored.dispose();
        }
    }

    @Test
    public void factOfTypeDeclaredWithPropertyChangeSupport_afterRoundTrip_reevaluatesRulesOnSetter() throws Exception {
        final KieBase kieBase = knowledgeBase(DECLARED_DYNAMIC_RULE);
        final KieSession session = kieBase.newKieSession();
        session.insert(new DynamicFact("initial"));
        session.fireAllRules();

        final KieSession restored = roundTrip(kieBase, session);
        try {
            final DynamicFact restoredFact = theFactIn(restored);
            restoredFact.setName("changed");

            assertThat(restored.fireAllRules())
                    .as("the restored fact lost its PropertyChangeListener, so the setter did not notify the session")
                    .isEqualTo(1);
        } finally {
            restored.dispose();
        }
    }

    /**
     * A fact inserted without the dynamic flag must stay non-dynamic across a round trip: the fix
     * re-registers the listeners that were there, it does not hand one to every fact that happens
     * to expose {@code addPropertyChangeListener}.
     */
    @Test
    public void plainlyInsertedFact_afterRoundTrip_staysNonDynamic() throws Exception {
        final KieBase kieBase = knowledgeBase(RULE);
        final KieSession session = kieBase.newKieSession();
        session.insert(new DynamicFact("initial"));
        session.fireAllRules();

        final KieSession restored = roundTrip(kieBase, session);
        try {
            final DynamicFact restoredFact = theFactIn(restored);
            restoredFact.setName("changed");

            assertThat(restored.fireAllRules()).isZero();
        } finally {
            restored.dispose();
        }
    }

    private static void dynamicInsert(final KieSession session, final DynamicFact fact) {
        ((WorkingMemoryEntryPoint) session.getEntryPoint(org.drools.base.rule.EntryPointId.DEFAULT.getEntryPointId()))
                .insert(fact, true);
    }

    private static DynamicFact theFactIn(final KieSession session) {
        return (DynamicFact) session.getObjects(object -> object instanceof DynamicFact).iterator().next();
    }

    /**
     * Marshals the session, disposes it as a reload from a database would, and unmarshals it into
     * a session of its own.
     */
    private static KieSession roundTrip(final KieBase kieBase, final KieSession session) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MarshallerFactory.newMarshaller(kieBase).marshall(baos, session);
        session.dispose();

        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return MarshallerFactory.newMarshaller(kieBase).unmarshall(bais);
    }

    private static KieBase knowledgeBase(final String drl) {
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newReaderResource(new StringReader(drl)), ResourceType.DRL);
        if (kbuilder.hasErrors()) {
            throw new IllegalStateException(kbuilder.getErrors().toString());
        }
        final InternalKnowledgeBase kieBase =
                KnowledgeBaseFactory.newKnowledgeBase(RuleBaseFactory.newRuleBase(RuleBaseFactory.newKnowledgeBaseConfiguration()));
        kieBase.addPackages(kbuilder.getKnowledgePackages());
        return kieBase;
    }

    public static class DynamicFact implements Serializable {

        private static final long serialVersionUID = 1L;

        private final PropertyChangeSupport support = new PropertyChangeSupport(this);

        private String name;

        public DynamicFact(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            final String old = this.name;
            this.name = name;
            support.firePropertyChange("name", old, name);
        }

        public void addPropertyChangeListener(final PropertyChangeListener listener) {
            support.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(final PropertyChangeListener listener) {
            support.removePropertyChangeListener(listener);
        }
    }
}
