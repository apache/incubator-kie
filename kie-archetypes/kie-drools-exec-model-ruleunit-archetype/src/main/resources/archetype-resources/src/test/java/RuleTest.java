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
#*
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
*#
#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import java.util.List;

import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class RuleTest {

    static final Logger LOG = LoggerFactory.getLogger(RuleTest.class);

    @Test
    public void test() {
        LOG.info("Creating RuleUnit");
        MeasurementUnit measurementUnit = new MeasurementUnit();

        try (RuleUnitInstance<MeasurementUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(measurementUnit)) {
            LOG.info("Insert data");
            measurementUnit.getMeasurements().add(new Measurement("color", "red"));
            measurementUnit.getMeasurements().add(new Measurement("color", "green"));
            measurementUnit.getMeasurements().add(new Measurement("color", "blue"));

            LOG.info("Run query. Rules are also fired");
            List<Measurement> queryResult = instance.executeQuery("FindColor").toList("$m");

            assertThat(queryResult).hasSize(3);
            assertThat(measurementUnit.getControlSet()).as("contains red").contains("red");
            assertThat(measurementUnit.getControlSet()).as("contains green").contains("green");
            assertThat(measurementUnit.getControlSet()).as("contains blue").contains("blue");
        }
    }
}