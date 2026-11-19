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
package org.kie.kogito.persistence.postgresql.reporting.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.kogito.event.cloudevents.utils.CloudEventUtils;
import org.kie.kogito.persistence.postgresql.reporting.model.JsonType;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresJsonField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMapping;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinition;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinitions;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresPartitionField;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresBootstrapLoaderImplTest {

    @Test
    void testLoadWithResource() throws JsonProcessingException {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));
        final PostgresMappingDefinitions definitions = new PostgresMappingDefinitions(List.of(definition));
        final String json = CloudEventUtils.Mapper.mapper().writeValueAsString(definitions);
        final InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        final PostgresBootstrapLoaderImpl loader = new PostgresBootstrapLoaderImpl(() -> is);

        final Optional<PostgresMappingDefinitions> result = loader.load();
        assertThat(result).isNotNull();
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getMappingDefinitions().size()).isEqualTo(1);

        final PostgresMappingDefinition resultDefinition = result.get().getMappingDefinitions().iterator().next();
        assertThat(resultDefinition).isEqualTo(definition);
    }

    @Test
    void testLoadWithoutResource() {
        final InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

        final PostgresBootstrapLoaderImpl loader = new PostgresBootstrapLoaderImpl(() -> is);

        final Optional<PostgresMappingDefinitions> result = loader.load();
        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
    }

}
