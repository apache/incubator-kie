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
import org.drools.core.reteoo.ModifyPreviousTuples;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeNode;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static org.drools.core.reteoo.EntryPointNode.removeRightTuplesMatchingOTN;

public class Update extends AbstractPropagationEntry<ReteEvaluator> implements Externalizable {
    private InternalFactHandle handle;
    private PropagationContext context;
    private ObjectTypeConf     objectTypeConf;

    public Update() {}

    public Update(InternalFactHandle handle, PropagationContext context, ObjectTypeConf objectTypeConf) {
        this.handle         = handle;
        this.context        = context;
        this.objectTypeConf = objectTypeConf;
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        execute(handle, context, objectTypeConf, reteEvaluator);
    }

    public InternalFactHandle getHandle() {
        return handle;
    }

    public static void execute(InternalFactHandle handle, PropagationContext pctx, ObjectTypeConf objectTypeConf, ReteEvaluator reteEvaluator) {
        if (objectTypeConf == null) {
            objectTypeConf = handle.getEntryPoint(reteEvaluator).getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf(handle.getEntryPointId(), handle.getObject());
        }
        ModifyPreviousTuples modifyPreviousTuples = new ModifyPreviousTuples(handle.detachLinkedTuples());
        ObjectTypeNode[]     cachedNodes          = objectTypeConf.getObjectTypeNodes();
        for (int i = 0, length = cachedNodes.length; i < length; i++) {
            cachedNodes[i].modifyObject(handle, modifyPreviousTuples, pctx, reteEvaluator);
            if (i < cachedNodes.length - 1) {
                removeRightTuplesMatchingOTN(pctx, reteEvaluator, modifyPreviousTuples, cachedNodes[i], 0);
            }
        }
        modifyPreviousTuples.retractTuples(pctx, reteEvaluator);
    }

    @Override
    public boolean isPartitionSplittable() {
        return true;
    }

    @Override
    public PropagationEntry<ReteEvaluator> getSplitForPartition(int partitionNr) {
        return new PartitionedUpdate(handle, context, objectTypeConf, partitionNr);
    }

    @Override
    public String toString() {
        return "Update of " + handle.getObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(next);
        out.writeObject(handle);
        out.writeObject(context);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        @SuppressWarnings("unchecked")
        PropagationEntry<ReteEvaluator> next = (PropagationEntry<ReteEvaluator>) in.readObject();
        this.next    = next;
        this.handle  = (InternalFactHandle) in.readObject();
        this.context = (PropagationContext) in.readObject();
    }
}
