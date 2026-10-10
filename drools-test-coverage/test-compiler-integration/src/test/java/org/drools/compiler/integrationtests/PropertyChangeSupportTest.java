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
package org.drools.compiler.integrationtests;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Role;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyChangeSupportTest {

    public static class DynamicFact {
        private PropertyChangeSupport support = new PropertyChangeSupport(this);
        private String name;
        private String value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            String old = this.name;
            this.name = name;
            support.firePropertyChange("name", old, name);
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            String old = this.value;
            this.value = value;
            support.firePropertyChange("value", old, value);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            support.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            support.removePropertyChangeListener(listener);
        }

        public int getListenerCount() {
            return support.getPropertyChangeListeners().length;
        }
    }

    @org.kie.api.definition.type.PropertyChangeSupport
    public static class AnnotatedDynamicFact extends DynamicFact {
    }

    @org.kie.api.definition.type.PropertyChangeSupport
    @Role(Role.Type.EVENT)
    @Expires("1s")
    public static class ExpiringDynamicFact extends DynamicFact {
    }

    public static class Trigger {
        private final String name;

        public Trigger(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Test
    public void testPropertyChanged() {
        // DROOLS-3607
        String drl =
                "import " + DynamicFact.class.getCanonicalName() + ";\n" +
                "declare DynamicFact\n" +
                "  @propertyChangeSupport\n" +
                "end\n" +
                "rule rule1\n" +
                "   when\n" +
                "     $fact: DynamicFact(name == \"user1\")\n" +
                "   then\n" +
                "     $fact.setName(\"user2\");\n" +
                " end\n" +
                " \n" +
                " rule rule2\n" +
                "   when\n" +
                "     $fact: DynamicFact(name == \"user2\")\n" +
                "   then\n" +
                "     $fact.setValue($fact.getValue() + \"VAL1\");\n" +
                " end";

        KieSession ksession = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();

        DynamicFact fact = new DynamicFact();
        fact.setName("user1");
        fact.setValue("");

        ksession.insert(fact);
        ksession.fireAllRules(10);

        assertThat(fact.getName()).isEqualTo("user2");

        assertThat(fact.getValue()).isEqualTo("VAL1");
    }

    @Test
    public void testDeleteUnregistersFactInsertedAsDynamic() {
        // a fact inserted with the dynamic flag must be released on delete, even if its type isn't dynamic
        String drl =
                "import " + DynamicFact.class.getCanonicalName() + ";\n" +
                "import " + Trigger.class.getCanonicalName() + ";\n" +
                "global java.util.List facts\n" +
                "rule insertDynamic\n" +
                "   when\n" +
                "     $t: Trigger()\n" +
                "   then\n" +
                "     delete($t);\n" +
                "     DynamicFact fact = new DynamicFact();\n" +
                "     fact.setName($t.getName());\n" +
                "     facts.add(fact);\n" +
                "     insert(fact, true);\n" +
                " end\n" +
                " \n" +
                " rule deleteDynamic\n" +
                "   when\n" +
                "     $fact: DynamicFact()\n" +
                "   then\n" +
                "     delete($fact);\n" +
                " end";

        KieSession ksession = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
        try {
            List<DynamicFact> facts = new ArrayList<>();
            ksession.setGlobal("facts", facts);

            for (int i = 0; i < 10; i++) {
                ksession.insert(new Trigger("t" + i));
                ksession.fireAllRules();
            }

            assertThat(ksession.getFactCount()).isZero();
            assertThat(facts).hasSize(10).allSatisfy(fact -> assertThat(fact.getListenerCount()).isZero());
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testClassLevelAnnotationMakesTypeDynamic() {
        // the class annotation must work like declaring the type with @propertyChangeSupport in DRL
        String drl =
                "import " + AnnotatedDynamicFact.class.getCanonicalName() + ";\n" +
                "rule rule1\n" +
                "   when\n" +
                "     $fact: AnnotatedDynamicFact(name == \"user1\")\n" +
                "   then\n" +
                "     $fact.setName(\"user2\");\n" +
                " end\n" +
                " \n" +
                " rule rule2\n" +
                "   when\n" +
                "     $fact: AnnotatedDynamicFact(name == \"user2\")\n" +
                "   then\n" +
                "     $fact.setValue($fact.getValue() + \"VAL1\");\n" +
                " end";

        KieSession ksession = new KieHelper().addContent(drl, ResourceType.DRL).build().newKieSession();
        try {
            AnnotatedDynamicFact fact = new AnnotatedDynamicFact();
            fact.setName("user1");
            fact.setValue("");

            FactHandle handle = ksession.insert(fact);
            assertThat(fact.getListenerCount()).isEqualTo(1);

            ksession.fireAllRules(10);

            assertThat(fact.getName()).isEqualTo("user2");
            assertThat(fact.getValue()).isEqualTo("VAL1");

            ksession.delete(handle);
            assertThat(fact.getListenerCount()).isZero();
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testExpiryUnregistersDynamicFact() {
        String drl =
                "import " + ExpiringDynamicFact.class.getCanonicalName() + ";\n" +
                "rule rule1\n" +
                "   when\n" +
                "     ExpiringDynamicFact()\n" +
                "   then\n" +
                " end";

        KieBase kbase = new KieHelper().addContent(drl, ResourceType.DRL).build(EventProcessingOption.STREAM);
        KieSessionConfiguration ksconf = KieServices.get().newKieSessionConfiguration();
        ksconf.setOption(ClockTypeOption.PSEUDO);
        KieSession ksession = kbase.newKieSession(ksconf, null);
        try {
            ExpiringDynamicFact fact = new ExpiringDynamicFact();
            ksession.insert(fact);
            ksession.fireAllRules();
            assertThat(fact.getListenerCount()).isEqualTo(1);

            SessionPseudoClock clock = ksession.getSessionClock();
            clock.advanceTime(2, TimeUnit.SECONDS);
            ksession.fireAllRules();

            assertThat(ksession.getFactCount()).isZero();
            assertThat(fact.getListenerCount()).isZero();
        } finally {
            ksession.dispose();
        }
    }
}
