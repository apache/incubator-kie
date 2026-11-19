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
package org.drools.example.api.multiplekbases;

import java.util.ArrayList;
import java.util.List;

import org.drools.core.time.impl.JDKTimerService;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class MultipleKbasesExampleTest {

    @Test
    public void testSimpleKieBase() {
        List<Integer> list = useKieSession("ksession1");
        // no packages imported means import everything
        assertThat(list.size()).isEqualTo(4);
        assertThat(list.containsAll(asList(0, 1, 2, 3))).isTrue();
    }

    @Test
    public void testKieBaseWithPackage() {
        List<Integer> list = useKieSession("ksession2");
        // import package org.some.pkg
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.containsAll(List.of(1))).isTrue();
    }

    @Test
    public void testKieBaseWithInclusion() {
        List<Integer> list = useKieSession("ksession3");
        // include ksession2 + import package org.some.pkg2
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.containsAll(asList(1, 2))).isTrue();
    }

    @Test
    public void testKieBaseWith2Packages() {
        List<Integer> list = useKieSession("ksession4");
        // import package org.some.pkg, org.other.pkg
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.containsAll(asList(1, 3))).isTrue();
    }

    @Test
    public void testKieBaseWithPackageAndTransitiveInclusion() {
        List<Integer> list = useKieSession("ksession5");
        // import package org.*
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.containsAll(asList(1, 2, 3))).isTrue();
    }

    @Test
    public void testKieBaseWithAllPackages() {
        List<Integer> list = useKieSession("ksession6");
        // import package org.some.*
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.containsAll(asList(1, 2))).isTrue();
    }

    private List<Integer> useKieSession(String name) {
        KieServices ks = KieServices.Factory.get();
        KieContainer kContainer = ks.getKieClasspathContainer();
        KieSession kSession = kContainer.newKieSession(name);

        List<Integer> list = new ArrayList<Integer>();
        kSession.setGlobal("list", list);
        kSession.insert(1);
        kSession.fireAllRules();

        return list;
    }

    @Test
    public void testEditSessionModel() {
        String name = "ksession6";
        ClockTypeOption pseudoClock = ClockTypeOption.PSEUDO;
        KieServices ks = KieServices.Factory.get();
        KieContainer kContainer = ks.newKieClasspathContainer();

        KieSessionModel kieSessionModel = kContainer.getKieSessionModel(name);
        ClockTypeOption clockType = kieSessionModel.getClockType();

        // clockType realtime
        assertThat(pseudoClock).isNotEqualTo(clockType);

        // change model to pseudo
        kieSessionModel.setClockType(pseudoClock);
        assertThat(pseudoClock).isEqualTo(kieSessionModel.getClockType());

        // new pseudo session
        KieSession kSession = kContainer.newKieSession(name);
        assertThat(kSession.getSessionClock().getClass()).isEqualTo(PseudoClockScheduler.class);
    }

    @Test
    public void testEditSessionModelAfterFirstCreatedKieSession() {
        String name = "ksession6";
        ClockTypeOption pseudoClock = ClockTypeOption.PSEUDO;
        KieServices ks = KieServices.Factory.get();
        KieContainer kContainer = ks.newKieClasspathContainer();

        KieSessionModel kieSessionModel = kContainer.getKieSessionModel(name);
        ClockTypeOption clockType = kieSessionModel.getClockType();

        // clockType realtime
        assertThat(clockType).isEqualTo(ClockTypeOption.REALTIME);
        assertThat(pseudoClock).isNotEqualTo(clockType);

        // session is realtime
        KieSession kSession = kContainer.newKieSession(name);
        assertThat(kSession.getSessionClock().getClass()).isEqualTo(JDKTimerService.class);

        // change model to pseudo
        kieSessionModel.setClockType(pseudoClock);

        // new session still realtime
        KieSession kSessionPseudo = kContainer.newKieSession(name);
        assertThat(kSessionPseudo.getSessionClock().getClass()).isEqualTo(PseudoClockScheduler.class);
    }
}