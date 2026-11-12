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
package org.jbpm.flow.serialization;

import org.drools.core.util.KeyStoreConstants;

/**
 * Saves and restores the deserialization filter system properties around a test.
 */
public class DeserializationFilterTestSupport {

    private String savedAllowedPatterns;
    private String savedFilterEnabled;

    /**
     * Saves the current filter properties and resets them to their defaults: filter enabled, no extra patterns.
     */
    public void setUp() {
        savedAllowedPatterns = System.getProperty(KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS);
        savedFilterEnabled = System.getProperty(KeyStoreConstants.PROP_ENABLE_DESER_FILTER);
        System.clearProperty(KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS);
        System.clearProperty(KeyStoreConstants.PROP_ENABLE_DESER_FILTER);
    }

    public void allow(String... patterns) {
        System.setProperty(KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS, String.join(";", patterns));
    }

    public void disableFilter() {
        System.setProperty(KeyStoreConstants.PROP_ENABLE_DESER_FILTER, "false");
    }

    public void tearDown() {
        restore(KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS, savedAllowedPatterns);
        restore(KeyStoreConstants.PROP_ENABLE_DESER_FILTER, savedFilterEnabled);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
