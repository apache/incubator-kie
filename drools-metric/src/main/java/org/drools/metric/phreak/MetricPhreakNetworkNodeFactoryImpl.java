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
package org.drools.metric.phreak;

import org.drools.core.common.ReteEvaluator;
import org.drools.core.phreak.PhreakAccumulateNode;
import org.drools.core.phreak.PhreakAsyncReceiveNode;
import org.drools.core.phreak.PhreakAsyncSendNode;
import org.drools.core.phreak.PhreakBranchNode;
import org.drools.core.phreak.PhreakEvalNode;
import org.drools.core.phreak.PhreakExistsNode;
import org.drools.core.phreak.PhreakFromNode;
import org.drools.core.phreak.PhreakGroupByNode;
import org.drools.core.phreak.PhreakJoinNode;
import org.drools.core.phreak.PhreakNetworkNodeFactory;
import org.drools.core.phreak.PhreakNotNode;
import org.drools.core.phreak.PhreakQueryNode;
import org.drools.core.phreak.PhreakQueryTerminalNode;
import org.drools.core.phreak.PhreakReactiveFromNode;
import org.drools.core.phreak.PhreakRuleTerminalNode;
import org.drools.core.phreak.PhreakTimerNode;
import org.drools.core.reteoo.SequenceNode.PhreakSequenceNode;
import org.drools.metric.util.MetricLogUtils;

public class MetricPhreakNetworkNodeFactoryImpl implements PhreakNetworkNodeFactory {

    @Override
    public PhreakJoinNode createPhreakJoinNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakJoinNodeMetric(reteEvaluator);
        } else {
            return new PhreakJoinNode(reteEvaluator);
        }
    }

    @Override
    public PhreakEvalNode createPhreakEvalNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakEvalNodeMetric(reteEvaluator);
        } else {
            return new PhreakEvalNode(reteEvaluator);
        }
    }

    @Override
    public PhreakFromNode createPhreakFromNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakFromNodeMetric(reteEvaluator);
        } else {
            return new PhreakFromNode(reteEvaluator);
        }
    }

    @Override
    public PhreakReactiveFromNode createPhreakReactiveFromNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakReactiveFromNodeMetric(reteEvaluator);
        } else {
            return new PhreakReactiveFromNode(reteEvaluator);
        }
    }

    @Override
    public PhreakNotNode createPhreakNotNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakNotNodeMetric(reteEvaluator);
        } else {
            return new PhreakNotNode(reteEvaluator);
        }
    }

    @Override
    public PhreakExistsNode createPhreakExistsNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakExistsNodeMetric(reteEvaluator);
        } else {
            return new PhreakExistsNode(reteEvaluator);
        }
    }

    @Override
    public PhreakAccumulateNode createPhreakAccumulateNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakAccumulateNodeMetric(reteEvaluator);
        } else {
            return new PhreakAccumulateNode(reteEvaluator);
        }
    }

    @Override
    public PhreakGroupByNode createPhreakGroupByNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakGroupByNodeMetric(reteEvaluator);
        } else {
            return new PhreakGroupByNode(reteEvaluator);
        }
    }

    @Override
    public PhreakBranchNode createPhreakBranchNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakBranchNodeMetric(reteEvaluator);
        } else {
            return new PhreakBranchNode(reteEvaluator);
        }
    }

    @Override
    public PhreakQueryNode createPhreakQueryNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakQueryNodeMetric(reteEvaluator);
        } else {
            return new PhreakQueryNode(reteEvaluator);
        }
    }

    @Override
    public PhreakTimerNode createPhreakTimerNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakTimerNodeMetric(reteEvaluator);
        } else {
            return new PhreakTimerNode(reteEvaluator);
        }
    }

    @Override
    public PhreakSequenceNode createPhreakSequenceNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakSequenceNodeMetric(reteEvaluator);
        } else {
            return new PhreakSequenceNode(reteEvaluator);
        }
    }

    @Override
    public PhreakAsyncSendNode createPhreakAsyncSendNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakAsyncSendNodeMetric(reteEvaluator);
        } else {
            return new PhreakAsyncSendNode(reteEvaluator);
        }
    }

    @Override
    public PhreakAsyncReceiveNode createPhreakAsyncReceiveNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakAsyncReceiveNodeMetric(reteEvaluator);
        } else {
            return new PhreakAsyncReceiveNode(reteEvaluator);
        }
    }

    @Override
    public PhreakRuleTerminalNode createPhreakRuleTerminalNode(ReteEvaluator reteEvaluator) {
        return new PhreakRuleTerminalNode(reteEvaluator); // TerminalNode is not BaseNode so cannot use PerfLogUtils
    }

    @Override
    public PhreakQueryTerminalNode createPhreakQueryTerminalNode(ReteEvaluator reteEvaluator) {
        if (MetricLogUtils.getInstance().isEnabled()) {
            return new PhreakQueryTerminalNodeMetric(reteEvaluator);
        } else {
            return new PhreakQueryTerminalNode(reteEvaluator);
        }
    }
}
