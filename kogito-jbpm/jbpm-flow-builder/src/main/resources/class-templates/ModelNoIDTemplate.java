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

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.kie.kogito.MappableToModel;
import org.kie.kogito.Model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class XXXModel implements Model, MapInput, MapInputId, MapOutput,
                                 MappableToModel<$modelClass$> {

    // Ephemeral REST request-body carrier - always tracks modifications from construction.
    @JsonIgnore
    private transient final Set<String> __modifiedFields = new HashSet<>();

    // Captures JSON properties that have no corresponding declared process variable field.
    @JsonIgnore
    private transient final Map<String, Object> __additionalProperties = new HashMap<>();

    @JsonIgnore
    @Override
    public Set<String> getModifiedFields() {
        return __modifiedFields;
    }

    // Marks a field as explicitly set, so PATCH can tell a null apart from an omitted field.
    void markModified(String field) {
        __modifiedFields.add(field);
    }

    @JsonAnySetter
    void __setAdditionalProperty(String name, Object value) {
        __additionalProperties.put(name, value);
    }

    @JsonAnyGetter
    Map<String, Object> __getAdditionalProperties() {
        return Collections.unmodifiableMap(__additionalProperties);
    }

    @JsonIgnore
    @Override
    public Map<String, Object> getAdditionalProperties() {
        return __additionalProperties;
    }

    // Overridden only so the setter can be hidden from JSON and OpenAPI, like the getter.
    @JsonIgnore
    @Override
    public void setAdditionalProperties(Map<String, Object> props) {
        __additionalProperties.clear();
        if (props != null) {
            __additionalProperties.putAll(props);
        }
    }

}
