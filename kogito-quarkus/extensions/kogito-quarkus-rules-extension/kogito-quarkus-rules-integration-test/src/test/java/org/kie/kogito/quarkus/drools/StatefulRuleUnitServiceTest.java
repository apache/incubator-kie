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
package org.kie.kogito.quarkus.drools;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.ruleunits.api.DataSource;
import org.junit.jupiter.api.Test;
import org.kie.kogito.incubation.application.AppRoot;
import org.kie.kogito.incubation.common.EmptyDataContext;
import org.kie.kogito.incubation.common.ExtendedDataContext;
import org.kie.kogito.incubation.common.ExtendedReferenceContext;
import org.kie.kogito.incubation.common.MapDataContext;
import org.kie.kogito.incubation.common.MetaDataContext;
import org.kie.kogito.incubation.rules.InstanceQueryId;
import org.kie.kogito.incubation.rules.RuleUnitIds;
import org.kie.kogito.incubation.rules.RuleUnitInstanceId;
import org.kie.kogito.incubation.rules.data.DataSourceId;
import org.kie.kogito.incubation.rules.services.DataSourceService;
import org.kie.kogito.incubation.rules.services.StatefulRuleUnitService;
import org.kie.kogito.incubation.rules.services.contexts.RuleUnitMetaDataContext;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class StatefulRuleUnitServiceTest {
    @Inject
    AppRoot appRoot;
    @Inject
    StatefulRuleUnitService ruleUnitService;
    @Inject
    DataSourceService dataSourceService;

    @Test
    void testCreate() {
        AnotherService ruleUnitData =
                new AnotherService(
                        DataSource.createStore(),
                        DataSource.createStore());

        var id = appRoot.get(RuleUnitIds.class).get(AnotherService.class);
        var result = ruleUnitService.create(id, ExtendedReferenceContext.ofData(ruleUnitData));
        var meta = result.as(RuleUnitMetaDataContext.class);
        var instanceId = meta.id(RuleUnitInstanceId.class);
        assertThat(instanceId.ruleUnitId().asLocalUri().path()).isEqualTo("/rule-units/org.kie.kogito.quarkus.drools.AnotherService");
    }

    @Test
    void testQueryDsService() {
        AnotherService ruleUnitData =
                new AnotherService(
                        DataSource.createStore(),
                        DataSource.createStore());

        var id = appRoot.get(RuleUnitIds.class).get(AnotherService.class);
        MetaDataContext created = ruleUnitService.create(id, ExtendedReferenceContext.ofData(ruleUnitData));
        var meta = created.as(RuleUnitMetaDataContext.class);
        var ruid = meta.id(RuleUnitInstanceId.class);
        InstanceQueryId queryId = ruid.queries().get("Strings");

        DataSourceId dataSourceId = ruid.dataSources().get("strings");

        dataSourceService.add(dataSourceId, new StringHolder("hello folks"));
        dataSourceService.add(dataSourceId, new StringHolder("hello people"));
        dataSourceService.add(dataSourceId, new StringHolder("hello Mario"));
        dataSourceService.add(dataSourceId, new StringHolder("helicopter"));

        Stream<ExtendedDataContext> result = ruleUnitService.query(queryId, ExtendedReferenceContext.ofData(EmptyDataContext.Instance));
        List<String> strings = result
                .map(e -> e.data().as(MapDataContext.class).get("results", StringHolder.class).getValue())
                .collect(Collectors.toList());

        assertThat(strings.isEmpty()).isFalse();

        strings.removeAll(List.of("hello folks", "hello people", "hello Mario"));
        assertThat(strings.isEmpty()).isTrue();

    }

    @Test
    void testQueryDirectDs() {
        AnotherService ruleUnitData =
                new AnotherService(
                        DataSource.createStore(),
                        DataSource.createStore());

        var id = appRoot.get(RuleUnitIds.class).get(AnotherService.class);
        MetaDataContext created = ruleUnitService.create(id, ExtendedReferenceContext.ofData(ruleUnitData));
        var meta = created.as(RuleUnitMetaDataContext.class);
        var ruid = meta.id(RuleUnitInstanceId.class);
        InstanceQueryId queryId = ruid.queries().get("Strings");

        ruleUnitData.getStrings().add(new StringHolder("hello folks"));
        ruleUnitData.getStrings().add(new StringHolder("hello people"));
        ruleUnitData.getStrings().add(new StringHolder("hello Mario"));
        ruleUnitData.getStrings().add(new StringHolder("helicopter"));

        Stream<ExtendedDataContext> result = ruleUnitService.query(queryId, ExtendedReferenceContext.ofData(EmptyDataContext.Instance));
        List<String> strings = result
                .map(e -> e.data().as(MapDataContext.class).get("results", StringHolder.class).getValue())
                .collect(Collectors.toList());

        assertThat(strings.isEmpty()).isFalse();

        strings.removeAll(List.of("hello folks", "hello people", "hello Mario"));
        assertThat(strings.isEmpty()).isTrue();

    }

}
