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
package org.kie.kogito.persistence.reporting.database;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.reporting.model.Field;
import org.kie.kogito.persistence.reporting.model.PartitionField;
import org.kie.kogito.persistence.reporting.test.TestTypes.TestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ValidationsTest {

    @Test
    void testValidateMappingIdNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateMappingId(null));
    }

    @Test
    void testValidateMappingIdBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateMappingId(""));
    }

    @Test
    void testValidateMappingId() {
        assertThat(Validations.validateMappingId("mappingId")).isEqualTo("mappingId");
    }

    @Test
    void testValidateSourceTableNameNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableName(null));
    }

    @Test
    void testValidateSourceTableNameBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableName(""));
    }

    @Test
    void testValidateSourceTableName() {
        assertThat(Validations.validateSourceTableName("validateSourceTableName")).isEqualTo("validateSourceTableName");
    }

    @Test
    void testValidateSourceTableJsonFieldNameNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableJsonFieldName(null));
    }

    @Test
    void testValidateSourceTableJsonFieldNameBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableJsonFieldName(""));
    }

    @Test
    void testValidateSourceTableJsonFieldName() {
        assertThat(Validations.validateSourceTableJsonFieldName("validateSourceTableJsonFieldName")).isEqualTo("validateSourceTableJsonFieldName");
    }

    @Test
    void testValidateSourceTableIdentityFieldsNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableIdentityFields(null));
    }

    @Test
    void testValidateSourceTableIdentityFieldsEmpty() {
        final List<Field> fields = Collections.emptyList();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTableIdentityFields(fields));
    }

    @Test
    void testValidateSourceTablePartitionFieldsNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateSourceTablePartitionFields(null));
    }

    @Test
    void testValidateSourceTablePartitionFieldsEmpty() {
        final List<PartitionField> partitionFields = Collections.emptyList();
        assertThat(Validations.validateSourceTablePartitionFields(partitionFields)).isEqualTo(Collections.emptyList());
    }

    @Test
    void testValidateTargetTableNameNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateTargetTableName(null));
    }

    @Test
    void testValidateTargetTableNameBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateTargetTableName(""));
    }

    @Test
    void testValidateTargetTableName() {
        assertThat(Validations.validateTargetTableName("targetTableName")).isEqualTo("targetTableName");
    }

    @Test
    void testValidateTargetTableFieldsNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateFieldMappings(null));
    }

    @Test
    void testValidateTargetTableFieldsEmpty() {
        final List<TestMapping> mappings = Collections.emptyList();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Validations.validateFieldMappings(mappings));
    }
}
