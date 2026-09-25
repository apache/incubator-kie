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
package org.kie.api.runtime.conf;

import org.kie.api.conf.OptionKey;

/**
 * An enum to configure the session query listener configuration.
 *
 * Query results are collected by a listener class. The "STANDARD"
 * query listener class copies and disconnects fact handles and objects
 * for query results, making them somewhat resilient to some working
 * memory actions. But this copying is costly. For the cases where
 * no concurrency exists between query execution and other working memory
 * actions, a lightweight listener implementation can be used, preventing
 * the copy and improving query performance significantly.
 */
public enum QueryListenerOption implements SingleValueRuleRuntimeOption {

    STANDARD("standard"),
    LIGHTWEIGHT("lightweight");

    /**
     * The property name for the clock type configuration
     */
    public static final String PROPERTY_NAME = "drools.queryListener";

    public static OptionKey<QueryListenerOption> KEY = new OptionKey<>(TYPE, PROPERTY_NAME);

    private String             option;

    QueryListenerOption(String option) {
        this.option = option;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public String getAsString() {
        return option;
    }

    public String toString() {
        return "QueryListenerClassOption( " + option + " )";
    }

    public static QueryListenerOption determineQueryListenerClassOption(String option) {
        if ( STANDARD.getAsString().equalsIgnoreCase( option ) ) {
            return STANDARD;
        } else if ( LIGHTWEIGHT.getAsString().equalsIgnoreCase( option ) ) {
            return LIGHTWEIGHT;
        }
        throw new IllegalArgumentException( "Illegal enum value '" + option + "' for QueryListenerOption" );
    }

}
