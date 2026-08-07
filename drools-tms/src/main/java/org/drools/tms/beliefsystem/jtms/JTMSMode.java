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
package org.drools.tms.beliefsystem.jtms;

import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.tms.beliefsystem.ModedAssertion;
import org.drools.tms.LogicalDependency;
import org.drools.base.util.AbstractLinkedListNode;
import org.drools.base.beliefsystem.Mode;

public class JTMSMode<M extends JTMSMode<M>> extends AbstractLinkedListNode<M> implements ModedAssertion<M> {
    private BeliefSystem<M> bs;
    private String value;
    private LogicalDependency<M> dep;
    private Mode nextMode;

    public JTMSMode(String value, BeliefSystem bs) {
        this.value = value;
        this.bs = bs;
    }

    public JTMSMode(String value, BeliefSystem bs,  Mode nextMode) {
        this.value = value;
        this.bs = bs;
        this.nextMode = nextMode;
    }

    @Override
    public BeliefSystem getBeliefSystem() {
        return bs;
    }

    public String getValue() {
        return value;
    }

    public LogicalDependency<M> getLogicalDependency() {
        return dep;
    }

    public void setLogicalDependency(LogicalDependency<M> dep) {
        this.dep = dep;
    }

    public Mode getNextMode() {
        return nextMode;
    }
}
