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
 * <p>
 * An enum to enable Declarative Agenda option.
 * This is an experimental feature.
 * </p>
 *
 * <pre>
 * drools.declarativeAgenda = &lt;true|false&gt;
 * </pre>
 *
 * <b>DEFAULT = false.</b>
 *
 */
public enum DeclarativeAgendaOption implements SingleValueRuleBaseOption {

    ENABLED(true), DISABLED(false);

    /**
     * The property name for the L and R Unlinking option
     */
    public static final String PROPERTY_NAME = "drools.declarativeAgendaEnabled";

    public static OptionKey KEY = new OptionKey(TYPE, PROPERTY_NAME);

    private boolean value;

    DeclarativeAgendaOption(final boolean value) {
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public boolean isDeclarativeAgendaEnabled() {
        return this.value;
    }

    public static DeclarativeAgendaOption determineDeclarativeAgenda(String option) {
        if ( ENABLED.name().equalsIgnoreCase(option) || "true".equalsIgnoreCase(option) ) {
            return ENABLED;
        } else if ( DISABLED.name().equalsIgnoreCase(option) || "false".equalsIgnoreCase(option) ) {
            return DISABLED;
        }
        throw new IllegalArgumentException( "Illegal enum value '" + option + "' for DeclarativeAgendaOption" );
    }
}
