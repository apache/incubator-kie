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

import java.util.Collections;
import java.util.Set;

import org.kie.api.PropertiesConfiguration;

public interface OptionsConfiguration<T extends Option, S extends SingleValueOption, M extends MultiValueOption> extends PropertiesConfiguration {

    void makeImmutable();

    /**
     * Sets an option value
     *
     * @param option the option class for the option being requested
     * @param <C> the option type
     */
    <C extends T> void setOption( C option );

    /**
     * Gets an option value
     *
     * @param optionKey SingleValueOption OptionKey to look up the valye for.
     * @param <C extends S> C
     *
     * @return the Option value for the given option. Returns null if option is
     *         not configured.
     */
    <C extends S> C getOption( OptionKey<C> optionKey );

    /**
     * Gets an option value for the given option + key. This method should
     * be used for multi-value options, like accumulate functions configuration
     * where one option has multiple values, distinguished by a sub-key.
     *
     * @param optionKey the option class for the option being requested
     * @param subKey the key for the option being requested
     * @param <C extends M> C
     *
     * @return the Option value for the given option + key. Returns null if option is
     *         not configured.
     */
    default <C extends M> C getOption( OptionKey<C> optionKey, String subKey ) {
        return null; // not all configurations have multi options,
    }

    /**
     * Deprecated, KEY now exists top level, and its preferred to be explicit that this is a sub key
     *
     * @param optionKey the option key
     * @return the set of sub keys
     * @param <C> the option type
     * @deprecated since 8.35.0, use {@link #getOptionSubKeys(OptionKey)} instead. The method name
     *             should explicitly indicate it returns sub keys. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    default <C extends M> Set<String> getOptionKeys(OptionKey<C> optionKey) {
        return getOptionSubKeys(optionKey);
    }

    /**
     * Retrieves the set of all sub keys for a MultiValueOption.
     *
     * @param optionKey The OptionKey for the MultiValueOption
     * @return a Set of Strings
     */
    default <C extends M> Set<String> getOptionSubKeys(OptionKey<C> optionKey) {
        return Collections.emptySet();
    }

    ClassLoader getClassLoader();

    <X extends OptionsConfiguration<T, S, M>> X as(ConfigurationKey<X> configuration);

}
