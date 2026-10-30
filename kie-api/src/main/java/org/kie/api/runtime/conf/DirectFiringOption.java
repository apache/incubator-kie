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
 * An option to define if the KieSession should directly firing consequences bypassing the agenda.
 * By default this option is disabled. Enabling this will bring a performance improvement at the cost
 * of being no longer able to use salience, no-loop and other features that affect rules' precedence.
 *
 * drools.directFiring = &lt;true|false&gt;
 *
 * DEFAULT = false
 */
public enum DirectFiringOption implements SingleValueRuleRuntimeOption {

    YES(true),
    NO(false);

    private static final long serialVersionUID = 510l;

    /**
     * The property name for the direct firing configuration
     */
    public static final String PROPERTY_NAME = "drools.directFiring";

    public static OptionKey<DirectFiringOption> KEY = new OptionKey<>(TYPE, PROPERTY_NAME);

    private final boolean directFiring;

    /**
     * Private constructor to enforce the use of the factory method
     * @param directFiring
     */
    DirectFiringOption( final boolean directFiring ) {
        this.directFiring = directFiring;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public boolean isDirectFiring() {
        return directFiring;
    }

}