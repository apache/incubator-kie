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
package org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.postgresql.reporting.model.JsonType;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresJsonField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMapping;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresPartitionField;
import org.kie.kogito.persistence.reporting.model.paths.PathSegment;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresContextTest {

    @Test
    void testPostgresContext() {
        final PostgresField field = new PostgresField("field1");
        final PostgresPartitionField partitionField = new PostgresPartitionField("partitionField1", "value");
        final PostgresMapping mapping = new PostgresMapping("sourceJsonPath",
                new PostgresJsonField("targetField1", JsonType.STRING));
        final PathSegment pathSegment = new PathSegment("segment", null);
        final Map<String, String> sourceTableFieldTypes = Map.of("field1", "text");

        final PostgresContext context = new PostgresContext("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(field),
                List.of(partitionField),
                "targetTableName",
                List.of(mapping),
                List.of(pathSegment),
                sourceTableFieldTypes);

        assertThat(context.getMappingId()).isEqualTo("mappingId");
        assertThat(context.getSourceTableName()).isEqualTo("sourceTableName");
        assertThat(context.getSourceTableJsonFieldName()).isEqualTo("sourceTableJsonFieldName");
        assertThat(context.getSourceTableIdentityFields()).isEqualTo(List.of(field));
        assertThat(context.getSourceTablePartitionFields()).isEqualTo(List.of(partitionField));
        assertThat(context.getTargetTableName()).isEqualTo("targetTableName");
        assertThat(context.getFieldMappings()).isEqualTo(List.of(mapping));
        assertThat(context.getMappingPaths()).isEqualTo(List.of(pathSegment));
        assertThat(context.getSourceTableFieldTypes()).isEqualTo(sourceTableFieldTypes);
    }
}
