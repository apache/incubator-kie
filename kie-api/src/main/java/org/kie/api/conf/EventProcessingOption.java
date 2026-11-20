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
package org.kie.api.conf;

/**
 * An Enum for Event Processing option.
 *
 * drools.eventProcessingMode = &lt;identity|equality&gt;
 *
 * When the rulebase is compiled in the CLOUD (default) event processing mode,
 * it behaves just like a regular rulebase.
 *
 * When the rulebase is compiled in the STREAM event processing mode, additional
 * assumptions are made. These assumptions allow the engine to perform a few optimisations
 * like:
 *
 * <ul>
 * <li> reasoning over absence of events (NOT CE), automatically adds an appropriate duration attribute
 * to the rule in order to avoid early rule firing. </li>
 * <li> memory management techniques may be employed when an event no longer can match other events
 * due to session clock continuous increment. </li>
 * </ul>
 */
public enum EventProcessingOption
        implements SingleValueRuleBaseOption {

    CLOUD("cloud"),
    STREAM("stream");

    /**
     * The property name for the sequential mode option
     */
    public static final String PROPERTY_NAME = "drools.eventProcessingMode";

    public static OptionKey KEY = new OptionKey(TYPE, PROPERTY_NAME);

    private String             string;

    EventProcessingOption(String mode) {
        this.string = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public String getMode() {
        return string;
    }

    public String toString() {
        return "EventProcessingOption( "+string+ " )";
    }

    public String toExternalForm() {
        return this.string;
    }

    public static EventProcessingOption determineEventProcessingMode(String mode) {
        if ( STREAM.getMode().equalsIgnoreCase( mode ) ) {
            return STREAM;
        } else if ( CLOUD.getMode().equalsIgnoreCase( mode ) ) {
            return CLOUD;
        }
        throw new IllegalArgumentException( "Illegal enum value '" + mode + "' for EventProcessingMode" );
    }
}
