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

import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.WorkingMemoryReteExpireAction;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.reteoo.ClassObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.time.JobContext;
import org.drools.core.time.impl.DefaultJobHandle;
import org.drools.core.time.impl.PointInTimeTrigger;
import org.kie.api.prototype.PrototypeEventInstance;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static org.drools.base.rule.TypeDeclaration.NEVER_EXPIRES;

public class Insert extends AbstractPropagationEntry<ReteEvaluator> implements Externalizable {
    private static final ObjectTypeNode.ExpireJob job = new ObjectTypeNode.ExpireJob();

    private InternalFactHandle handle;
    private PropagationContext context;
    private ObjectTypeConf     objectTypeConf;

    public Insert() {}

    public Insert(InternalFactHandle handle, PropagationContext context, ReteEvaluator reteEvaluator, ObjectTypeConf objectTypeConf) {
        this.handle         = handle;
        this.context        = context;
        this.objectTypeConf = objectTypeConf;
        if (handle.isEvent()) {
            scheduleExpiration(reteEvaluator, handle, context, objectTypeConf, reteEvaluator.getTimerService().getCurrentTime());
        }
    }

    public static void execute(InternalFactHandle handle, PropagationContext context, ReteEvaluator reteEvaluator, ObjectTypeConf objectTypeConf) {
        if (handle.isEvent()) {
            scheduleExpiration(reteEvaluator, handle, context, objectTypeConf, reteEvaluator.getTimerService().getCurrentTime());
        }
        propagate(handle, context, reteEvaluator, objectTypeConf);
    }

    private static void propagate(InternalFactHandle handle, PropagationContext context, ReteEvaluator reteEvaluator, ObjectTypeConf objectTypeConf) {
        if (objectTypeConf == null) {
            objectTypeConf = handle.getEntryPoint(reteEvaluator).getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf(handle.getEntryPointId(), handle.getObject());
        }
        for (ObjectTypeNode otn : objectTypeConf.getObjectTypeNodes()) {
            otn.propagateAssert(handle, context, reteEvaluator);
        }
        if (isOrphanHandle(handle, reteEvaluator)) {
            handle.setDisconnected(true);
            handle.getEntryPoint(reteEvaluator).getObjectStore().removeHandle(handle);
            if (handle instanceof DefaultEventHandle eventHandle) {
                eventHandle.unscheduleAllJobs(reteEvaluator);
            }
        }
    }

    private static boolean isOrphanHandle(InternalFactHandle handle, ReteEvaluator reteEvaluator) {
        return !handle.hasMatches() && !reteEvaluator.getKnowledgeBase().getKieBaseConfiguration().isMutabilityEnabled();
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        propagate(handle, context, reteEvaluator, objectTypeConf);
    }

    private static void scheduleExpiration(ReteEvaluator reteEvaluator, InternalFactHandle handle, PropagationContext context, ObjectTypeConf objectTypeConf, long insertionTime) {
        for (ObjectTypeNode otn : objectTypeConf.getObjectTypeNodes()) {
            long expirationOffset = objectTypeConf.isPrototype() ? ((PrototypeEventInstance) handle.getObject()).getExpiration() : otn.getExpirationOffset();
            scheduleExpiration(reteEvaluator, handle, context, otn, insertionTime, expirationOffset);
        }
        if (objectTypeConf.getConcreteObjectTypeNode() == null) {
            long expirationOffset = objectTypeConf.isPrototype() ? ((PrototypeEventInstance) handle.getObject()).getExpiration() : ((ClassObjectTypeConf) objectTypeConf).getExpirationOffset();
            scheduleExpiration(reteEvaluator, handle, context, null, insertionTime, expirationOffset);
        }
    }

    private static void scheduleExpiration(ReteEvaluator reteEvaluator, InternalFactHandle handle, PropagationContext context, ObjectTypeNode otn, long insertionTime, long expirationOffset) {
        if (expirationOffset == NEVER_EXPIRES || expirationOffset == Long.MAX_VALUE || context.getReaderContext() != null) {
            return;
        }
        DefaultEventHandle eventFactHandle = (DefaultEventHandle) handle;
        long               nextTimestamp   = getNextTimestamp(insertionTime, expirationOffset, eventFactHandle);
        WorkingMemoryReteExpireAction action = new WorkingMemoryReteExpireAction((DefaultEventHandle) handle, otn);
        if (nextTimestamp <= reteEvaluator.getTimerService().getCurrentTime()) {
            reteEvaluator.addPropagation(action);
        } else {
            JobContext jobctx = new ObjectTypeNode.ExpireJobContext(action, reteEvaluator);
            DefaultJobHandle jobHandle = (DefaultJobHandle) reteEvaluator.getTimerService()
                    .scheduleJob(job, jobctx, PointInTimeTrigger.createPointInTimeTrigger(nextTimestamp, null));
            jobctx.setJobHandle(jobHandle);
            eventFactHandle.addJob(jobHandle);
        }
    }

    private static long getNextTimestamp(long insertionTime, long expirationOffset, DefaultEventHandle eventFactHandle) {
        long effectiveEnd = eventFactHandle.getEndTimestamp() + expirationOffset;
        return Math.max(insertionTime, effectiveEnd >= 0 ? effectiveEnd : Long.MAX_VALUE);
    }

    @Override
    public String toString() {
        return "Insert of " + handle.getObject();
    }

    public InternalFactHandle getHandle() {
        return handle;
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
