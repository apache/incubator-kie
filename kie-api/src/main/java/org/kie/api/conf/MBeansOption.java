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
 * An Enum for MBeans Enabled option.
 *
 * kie.mbeans = &lt;enabled|disabled&gt;
 *
 * DEFAULT = false
 */
public enum MBeansOption implements SingleValueKieBaseOption {

    ENABLED(true),
    DISABLED(false);

    /**
     * The property name for the mbeans option
     */
    public static final String PROPERTY_NAME = "kie.mbeans";

    public static OptionKey<MBeansOption> KEY = new OptionKey<>(TYPE, PROPERTY_NAME);

    private boolean value;

    MBeansOption( final boolean value ) {
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public boolean isEnabled() {
        return this.value;
    }

    public static boolean isEnabled(final String value) {
        if( value == null || value.trim().length() == 0 ) {
            return false;
        } else if ( "ENABLED".equalsIgnoreCase( value ) ) {
            return true;
        } else if ( "DISABLED".equalsIgnoreCase( value ) ) {
            return false;
        } else {
            throw new IllegalArgumentException( "Illegal enum value '" + value + "' for MBeans option. Should be either 'enabled' or 'disabled'" );
        }
    }


}
