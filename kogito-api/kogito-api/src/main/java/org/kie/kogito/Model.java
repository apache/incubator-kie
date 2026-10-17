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
package org.kie.kogito;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents data model type of objects that are usually descriptor of data holders.
 *
 */
public interface Model extends MapInput, MapOutput {

    default void update(Map<String, Object> params) {
        Models.fromMap(this, params);
    }

    default Map<String, Object> updatePartially(Map<String, Object> params) {
        update(params);
        return params;
    }

    /**
     * Returns the set of field names that were explicitly set (e.g. during a PATCH request).
     * Returns null when tracking is not active — all fields are included in toMap().
     * Returns a non-null set when tracking is active — only those fields are included.
     */
    default Set<String> getModifiedFields() {
        return null;
    }

    /**
     * Returns the undeclared process variables carried by this model (legacy variable mode).
     * Returns an empty map when the model does not carry any.
     */
    @JsonIgnore
    default Map<String, Object> getAdditionalProperties() {
        return Collections.emptyMap();
    }

    /**
     * Sets the undeclared process variables carried by this model (legacy variable mode).
     * No-op unless the model supports carrying them.
     */
    default void setAdditionalProperties(Map<String, Object> additionalProperties) {
        // no-op by default
    }
}
