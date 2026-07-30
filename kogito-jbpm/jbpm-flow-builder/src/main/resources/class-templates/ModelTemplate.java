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
package org.jbpm.process.codegen;

import org.kie.kogito.MapInput;
import org.kie.kogito.MapInputId;
import org.kie.kogito.MapOutput;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.kie.kogito.MappableToModel;
import org.kie.kogito.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class XXXModel implements org.kie.kogito.Model, MapInput, MapInputId, MapOutput, MappableToModel<$modelClass$> {

    private String id;

    @JsonIgnore
    private transient Set<String> __modifiedFields;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    @JsonIgnore
    @Override
    public Set<String> getModifiedFields() {
        return __modifiedFields;
    }

    // Codegen-internal: carries the modified-fields set from a PATCH Input onto
    // its toModel() target. Left null otherwise, so toMap() includes every field.
    void addModifiedFields(Set<String> fields) {
        if (fields == null) {
            return;
        }
        if (__modifiedFields == null) {
            __modifiedFields = new HashSet<>();
        }
        __modifiedFields.addAll(fields);
    }

    // Called by the generated PUT handler before a full-replace update, so every
    // field counts as significant - unlike PATCH, which only touches tracked fields.
    void clearModifiedFields() {
        __modifiedFields = null;
    }

}
