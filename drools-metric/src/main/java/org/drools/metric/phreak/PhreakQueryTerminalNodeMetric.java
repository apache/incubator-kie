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
import org.drools.core.common.TupleSets;
import org.drools.core.phreak.PhreakQueryTerminalNode;
import org.drools.core.phreak.StackEntry;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.base.util.LinkedList;
import org.drools.metric.util.MetricLogUtils;

public class PhreakQueryTerminalNodeMetric extends PhreakQueryTerminalNode {

    public PhreakQueryTerminalNodeMetric(ReteEvaluator reteEvaluator) {
        super(reteEvaluator);
    }

    @Override
    public void doNode(LinkedList<StackEntry> stack,
                       QueryTerminalNode qtnNode,
                       TupleSets srcLeftTuples) {

        try {
            MetricLogUtils.getInstance().startMetrics(qtnNode);

            super.doNode(stack, qtnNode, srcLeftTuples);

        } finally {
            MetricLogUtils.getInstance().logAndEndMetrics();
        }
    }
}
