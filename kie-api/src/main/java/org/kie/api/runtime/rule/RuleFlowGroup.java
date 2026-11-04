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
package org.kie.api.runtime.rule;

public interface RuleFlowGroup {
    /**
     * @return
     *      The RuleFlowGroup name
     */
    public String getName();

    public void clear();

    /**
     * Activates this ruleflow-group by pushing it onto the focus stack.
     * Rules in this group become eligible to fire.
     *
     * <p>Unlike {@link AgendaGroup#setFocus()}, this method also fires
     * before/after {@code RuleFlowGroupActivated} events and sets the
     * group's active and ruleflow-listener flags.
     *
     * <p>The corresponding {@code RuleFlowGroupDeactivated} event fires
     * only when the group was activated with a process node instance.
     * A plain {@code activate()} call without a process fires
     * {@code RuleFlowGroupActivated} but never {@code RuleFlowGroupDeactivated}.
     */
    void activate();
}
