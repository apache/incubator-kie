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
import org.kie.api.annotations.KieProperty;

/**
 * A class for the belief system configuration.
 */
public class BeliefSystemTypeOption implements SingleValueRuleRuntimeOption {

    private static final long serialVersionUID = 510l;

    /**
     * The property name for the belief system configuration
     */
    @KieProperty(type = "string")
    public static final String PROPERTY_NAME = "drools.beliefSystem";

    public static OptionKey<BeliefSystemTypeOption> KEY = new OptionKey<>(TYPE, PROPERTY_NAME);

    /**
     * Belief System Type
     */
    private final String beliefSystemType;

    /**
     * Private constructor to enforce the use of the factory method
     * @param beliefSystemType
     */
    private BeliefSystemTypeOption( String beliefSystemType ) {
        this.beliefSystemType = beliefSystemType;
    }

    /**
     * This is a factory method for this belief system configuration.
     * The factory method is a best practice for the case where the
     * actual object construction is changed in the future.
     *
     * @param beliefSystemType  the identifier for the belie system
     *
     * @return the actual type safe default clock type configuration.
     */
    public static BeliefSystemTypeOption get( String beliefSystemType ) {
        return new BeliefSystemTypeOption( beliefSystemType );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    /**
     * @return the configured belief system type
     */
    public String getBeliefSystemType() {
        return beliefSystemType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (( beliefSystemType == null) ? 0 :  beliefSystemType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) { return true; }
        if ( obj == null ) { return false; }
        if ( getClass() != obj.getClass() ) { return false; }
        BeliefSystemTypeOption other = (BeliefSystemTypeOption) obj;
        if (  beliefSystemType == null ) {
            if ( other.beliefSystemType != null ) {
                return false;
            }
        } else if ( ! beliefSystemType.equals( other.beliefSystemType) ) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "BeliefSystemTypeOption( "+ beliefSystemType +" )";
    }
}
