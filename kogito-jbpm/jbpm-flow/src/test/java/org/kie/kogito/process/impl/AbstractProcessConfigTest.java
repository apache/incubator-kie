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
package org.kie.kogito.process.impl;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.WorkItemHandlerConfig;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class AbstractProcessConfigTest {

    private static class MockProcessConfig extends AbstractProcessConfig {
        protected MockProcessConfig(Iterable<WorkItemHandlerConfig> workItemHandlerConfig) {
            super(workItemHandlerConfig, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    @Test
    void testOneWorkItemHandlerConfig() {
        CachedWorkItemHandlerConfig workItemConfig = new CachedWorkItemHandlerConfig();
        ProcessConfig config = new MockProcessConfig(List.of(workItemConfig));
        assertThat(config.workItemHandlers()).isSameAs(workItemConfig);
    }

    @Test
    void testMultipleWorkItemHandlerConfig() {
        CachedWorkItemHandlerConfig workItemConfig1 = new CachedWorkItemHandlerConfig();
        CachedWorkItemHandlerConfig workItemConfig2 = new CachedWorkItemHandlerConfig();
        final String name1 = "Javierito1";
        final String name2 = "Javierito2";
        final KogitoWorkItemHandler workItem1 = mock(KogitoWorkItemHandler.class);
        final KogitoWorkItemHandler workItem2 = mock(KogitoWorkItemHandler.class);
        workItemConfig1.register(name1, workItem1);
        workItemConfig2.register(name2, workItem2);
        ProcessConfig config = new MockProcessConfig(List.of(workItemConfig1, workItemConfig2));
        assertThat(config.workItemHandlers().names()).containsExactlyInAnyOrder(name1, name2);
        assertThat(config.workItemHandlers().forName(name1)).isSameAs(workItem1);
        assertThat(config.workItemHandlers().forName(name2)).isSameAs(workItem2);
        final String name3 = "Javierito3";
        final KogitoWorkItemHandler workItem3 = mock(KogitoWorkItemHandler.class);
        workItemConfig2.register(name3, workItem3);
        assertThat(config.workItemHandlers().names()).containsExactlyInAnyOrder(name1, name2, name3);
        assertThat(config.workItemHandlers().forName(name3)).isSameAs(workItem3);
    }

}
