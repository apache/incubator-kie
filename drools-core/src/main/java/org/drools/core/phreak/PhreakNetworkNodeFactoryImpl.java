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

import org.drools.core.reteoo.SequenceNode.PhreakSequenceNode;

import org.drools.core.common.ReteEvaluator;

public class PhreakNetworkNodeFactoryImpl implements PhreakNetworkNodeFactory {

    @Override
    public PhreakJoinNode createPhreakJoinNode(ReteEvaluator reteEvaluator) {
        return new PhreakJoinNode(reteEvaluator);
    }

    @Override
    public PhreakEvalNode createPhreakEvalNode(ReteEvaluator reteEvaluator) {
        return new PhreakEvalNode(reteEvaluator);
    }

    @Override
    public PhreakFromNode createPhreakFromNode(ReteEvaluator reteEvaluator) {
        return new PhreakFromNode(reteEvaluator);
    }

    @Override
    public PhreakReactiveFromNode createPhreakReactiveFromNode(ReteEvaluator reteEvaluator) {
        return new PhreakReactiveFromNode(reteEvaluator);
    }

    @Override
    public PhreakNotNode createPhreakNotNode(ReteEvaluator reteEvaluator) {
        return new PhreakNotNode(reteEvaluator);
    }

    @Override
    public PhreakExistsNode createPhreakExistsNode(ReteEvaluator reteEvaluator) {
        return new PhreakExistsNode(reteEvaluator);
    }

    @Override
    public PhreakAccumulateNode createPhreakAccumulateNode(ReteEvaluator reteEvaluator) {
        return new PhreakAccumulateNode(reteEvaluator);
    }

    @Override
    public PhreakGroupByNode createPhreakGroupByNode(ReteEvaluator reteEvaluator) {
        return new PhreakGroupByNode(reteEvaluator);
    }

    @Override
    public PhreakBranchNode createPhreakBranchNode(ReteEvaluator reteEvaluator) {
        return new PhreakBranchNode(reteEvaluator);
    }

    @Override
    public PhreakQueryNode createPhreakQueryNode(ReteEvaluator reteEvaluator) {
        return new PhreakQueryNode(reteEvaluator);
    }

    @Override
    public PhreakTimerNode createPhreakTimerNode(ReteEvaluator reteEvaluator) {
        return new PhreakTimerNode(reteEvaluator);
    }

    @Override
    public PhreakSequenceNode createPhreakSequenceNode(ReteEvaluator reteEvaluator) {
        return new PhreakSequenceNode(reteEvaluator);
    }

    @Override
    public PhreakAsyncSendNode createPhreakAsyncSendNode(ReteEvaluator reteEvaluator) {
        return new PhreakAsyncSendNode(reteEvaluator);
    }

    @Override
    public PhreakAsyncReceiveNode createPhreakAsyncReceiveNode(ReteEvaluator reteEvaluator) {

        return new PhreakAsyncReceiveNode(reteEvaluator);
    }

    @Override
    public PhreakRuleTerminalNode createPhreakRuleTerminalNode(ReteEvaluator reteEvaluator) {
        return new PhreakRuleTerminalNode(reteEvaluator);
    }

    @Override
    public PhreakQueryTerminalNode createPhreakQueryTerminalNode(ReteEvaluator reteEvaluator) {
        return new PhreakQueryTerminalNode(reteEvaluator);
    }
}
