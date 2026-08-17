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
package org.drools.beliefs.bayes;

import java.util.Arrays;

import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.tms.beliefsystem.ModedAssertion;
import org.drools.tms.LogicalDependency;
import org.drools.base.util.AbstractLinkedListNode;
import org.drools.base.beliefsystem.Mode;

public class BayesHardEvidence<M extends BayesHardEvidence<M>> extends AbstractLinkedListNode<M> implements ModedAssertion<M> {
    private double[] distribution;
    private BeliefSystem<M> beliefSystem;
    private LogicalDependency<M> dep;
    private Mode nextMode;

    public BayesHardEvidence(BeliefSystem<M> beliefSystem,
                             double[] distribution) {
        this.beliefSystem = beliefSystem;
        this.distribution = distribution;
    }

    public BayesHardEvidence(BeliefSystem<M> beliefSystem,
                             double[] distribution,
                             Mode nextMode) {
        this.beliefSystem = beliefSystem;
        this.distribution = distribution;
        this.nextMode = nextMode;
    }

    public LogicalDependency<M> getLogicalDependency() {
        return dep;
    }

    public void setLogicalDependency(LogicalDependency<M> dep) {
        this.dep = dep;
    }

    public double[] getDistribution() {
        return distribution;
    }

    public Mode getNextMode() {
        return nextMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        BayesHardEvidence that = (BayesHardEvidence) o;

        if (!Arrays.equals(distribution, that.distribution)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(distribution);
    }

    @Override
    public BeliefSystem getBeliefSystem() {
        return beliefSystem;
    }
}
