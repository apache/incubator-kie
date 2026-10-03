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
 * An Enum for Sequential option.
 *
 * drools.sequential = &lt;true|false&gt;
 *
 * DEFAULT = false
 */
public enum SequentialOption implements SingleValueRuleBaseOption {

    YES(true),
    NO(false);

    /**
     * The property name for the sequential mode option
     */
    public static final String PROPERTY_NAME = "drools.sequential";

    public static OptionKey<SequentialOption> KEY = new OptionKey(TYPE, PROPERTY_NAME);

    private boolean value;

    SequentialOption( final boolean value ) {
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public boolean isSequential() {
        return this.value;
    }

    public static SequentialOption determineSequential(String option) {
        if ( YES.name().equalsIgnoreCase(option) || "true".equalsIgnoreCase(option) ) {
            return YES;
        } else if ( NO.name().equalsIgnoreCase(option) || "false".equalsIgnoreCase(option) ) {
            return NO;
        }
        throw new IllegalArgumentException( "Illegal enum value '" + option + "' for DeclarativeAgendaOption" );
    }
}
