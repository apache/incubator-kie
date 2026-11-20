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

public class TimedRuleExecutionOption implements SingleValueRuleRuntimeOption {

    private static final long serialVersionUID = 510l;

    public static final String PROPERTY_NAME = "drools.timedRuleExecution";

    public static OptionKey<TimedRuleExecutionOption> KEY = new OptionKey<>(TYPE, PROPERTY_NAME);

    public static final TimedRuleExecutionOption YES = new TimedRuleExecutionOption(rules -> true);

    public static final TimedRuleExecutionOption NO = new TimedRuleExecutionOption(null);

    private final TimedRuleExecutionFilter filter;

    private TimedRuleExecutionOption( final TimedRuleExecutionFilter filter ) {
        this.filter = filter;
    }

    @Override
    public String getPropertyName() {
        return PROPERTY_NAME;
    }

    public TimedRuleExecutionFilter getFilter() {
        return filter;
    }

    public static class FILTERED extends TimedRuleExecutionOption {
        public FILTERED(TimedRuleExecutionFilter filter) {
            super(filter);
        }
    }

    public static TimedRuleExecutionOption resolve(String value) {
        return Boolean.valueOf( value ) ? YES : NO;
    }
}
