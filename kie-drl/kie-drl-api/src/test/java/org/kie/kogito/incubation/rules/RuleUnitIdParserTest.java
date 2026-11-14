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
package org.kie.kogito.incubation.rules;

import org.junit.jupiter.api.Test;
import org.kie.drl.api.identifiers.InstanceQueryId;
import org.kie.drl.api.identifiers.QueryId;
import org.kie.drl.api.identifiers.RuleUnitId;
import org.kie.drl.api.identifiers.RuleUnitIdParser;
import org.kie.drl.api.identifiers.RuleUnitInstanceId;

import static org.assertj.core.api.Assertions.assertThat;

class RuleUnitIdParserTest {

    @Test
    void parseRuleUnitId() {
        assertThat(RuleUnitIdParser.parse("/rule-units/u").getClass()).isEqualTo(RuleUnitId.class);
        assertThat(RuleUnitIdParser.parse("/rule-units/u", RuleUnitId.class).ruleUnitId()).isEqualTo("u");
    }

    @Test
    void parseQueryId() {
        assertThat(RuleUnitIdParser.parse("/rule-units/u/queries/q").getClass()).isEqualTo(QueryId.class);
        assertThat(RuleUnitIdParser.parse("/rule-units/u/queries/q", RuleUnitId.class).ruleUnitId()).isEqualTo("u");
        assertThat(RuleUnitIdParser.parse("/rule-units/u/queries/q", QueryId.class).queryId()).isEqualTo("q");
    }

    @Test
    void parseRuleUnitInstanceId() {
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui").getClass()).isEqualTo(RuleUnitInstanceId.class);
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui", RuleUnitId.class).ruleUnitId()).isEqualTo("u");
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui", RuleUnitInstanceId.class).ruleUnitInstanceId()).isEqualTo("ui");
    }

    @Test
    void parseInstanceQueryId() {
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui/queries/q").getClass()).isEqualTo(InstanceQueryId.class);
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui/queries/q", RuleUnitId.class).ruleUnitId()).isEqualTo("u");
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui/queries/q", RuleUnitInstanceId.class).ruleUnitInstanceId()).isEqualTo("ui");
        assertThat(RuleUnitIdParser.parse("/rule-units/u/instances/ui/queries/q", InstanceQueryId.class).queryId()).isEqualTo("q");
    }

}