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
package org.drools.core.phreak;

import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.SequenceNode.PhreakSequenceNode;
import org.kie.api.internal.utils.KieService;

public interface PhreakNetworkNodeFactory extends KieService {

    PhreakJoinNode createPhreakJoinNode(ReteEvaluator reteEvaluator);

    PhreakEvalNode createPhreakEvalNode(ReteEvaluator reteEvaluator);

    PhreakFromNode createPhreakFromNode(ReteEvaluator reteEvaluator);

    PhreakReactiveFromNode createPhreakReactiveFromNode(ReteEvaluator reteEvaluator);

    PhreakNotNode createPhreakNotNode(ReteEvaluator reteEvaluator);

    PhreakExistsNode createPhreakExistsNode(ReteEvaluator reteEvaluator);

    PhreakAccumulateNode createPhreakAccumulateNode(ReteEvaluator reteEvaluator);

    PhreakGroupByNode createPhreakGroupByNode(ReteEvaluator reteEvaluator);

    PhreakBranchNode createPhreakBranchNode(ReteEvaluator reteEvaluator);

    PhreakQueryNode createPhreakQueryNode(ReteEvaluator reteEvaluator);

    PhreakTimerNode createPhreakTimerNode(ReteEvaluator reteEvaluator);

    PhreakAsyncSendNode createPhreakAsyncSendNode(ReteEvaluator reteEvaluator);

    PhreakSequenceNode createPhreakSequenceNode(ReteEvaluator reteEvaluator);

    PhreakAsyncReceiveNode createPhreakAsyncReceiveNode(ReteEvaluator reteEvaluator);

    PhreakRuleTerminalNode createPhreakRuleTerminalNode(ReteEvaluator reteEvaluator);

    PhreakQueryTerminalNode createPhreakQueryTerminalNode(ReteEvaluator reteEvaluator);

    class Factory {

        private static class LazyHolder {

            private static final PhreakNetworkNodeFactory INSTANCE = createInstance();

            private static PhreakNetworkNodeFactory createInstance() {
                PhreakNetworkNodeFactory factory = KieService.load(PhreakNetworkNodeFactory.class);
                return factory != null ? factory : new PhreakNetworkNodeFactoryImpl();
            }
        }

        public static PhreakNetworkNodeFactory get() {
            return LazyHolder.INSTANCE;
        }

        private Factory() {}
    }
}
