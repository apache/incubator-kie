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
package org.drools.core.phreak.actions;

import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeConf;

public class Delete extends AbstractPropagationEntry<ReteEvaluator> {
    private final EntryPointNode     epn;
    private final InternalFactHandle handle;
    private final PropagationContext context;
    private final ObjectTypeConf     objectTypeConf;

    public Delete(EntryPointNode epn, InternalFactHandle handle, PropagationContext context, ObjectTypeConf objectTypeConf) {
        this.epn            = epn;
        this.handle         = handle;
        this.context        = context;
        this.objectTypeConf = objectTypeConf;
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        execute(reteEvaluator, epn, handle, context, objectTypeConf);
    }

    public static void execute(ReteEvaluator reteEvaluator, EntryPointNode epn, InternalFactHandle handle, PropagationContext context, ObjectTypeConf objectTypeConf) {
        epn.propagateRetract(handle, context, objectTypeConf, reteEvaluator);
    }

    @Override
    public boolean isPartitionSplittable() {
        return true;
    }

    @Override
    public PropagationEntry<ReteEvaluator> getSplitForPartition(int partitionNr) {
        return new PartitionedDelete(handle, context, objectTypeConf, partitionNr);
    }

    @Override
    public String toString() {
        return "Delete of " + handle.getObject();
    }
}
