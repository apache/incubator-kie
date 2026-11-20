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
package org.drools.example.api.reactivekiesession;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveKieSessionExampleTest {

    @Test
    public void testGo() {
        KieServices ks = KieServices.Factory.get();
        KieContainer kContainer = ks.getKieClasspathContainer();

        KieSession ksession = kContainer.newKieSession();

        List<String> list = new ArrayList<String>();
        ksession.setGlobal( "list", list );

        ksession.fireAllRules();

        assertThat( list.size() ).isEqualTo( 2 );
        assertThat( list.contains( "car" ) ).isTrue();
        assertThat( list.contains( "ball" ) ).isTrue();

        list.clear();

        ksession.insert("Debbie");
        ksession.fireAllRules();
        ksession.fireAllRules();

        assertThat( list.size() ).isEqualTo( 1 );
        assertThat( list.contains( "doll" ) ).isTrue();
    }
}
