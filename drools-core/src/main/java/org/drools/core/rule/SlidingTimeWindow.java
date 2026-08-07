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
package org.drools.core.rule;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.PriorityQueue;

import org.drools.base.time.JobHandle;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PhreakPropagationContextFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.WindowNode;
import org.drools.core.reteoo.WindowNode.WindowMemory;
import org.drools.core.time.Job;
import org.drools.core.time.JobContext;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.PointInTimeTrigger;
import org.kie.api.runtime.rule.FactHandle;

public class SlidingTimeWindow
        implements
        Externalizable,
        BehaviorRuntime {

    protected long size;
    // stateless job
    private static final BehaviorJob job = new BehaviorJob();

    protected int nodeId;

    public SlidingTimeWindow() {
        this( 0 );
    }

    public SlidingTimeWindow(final long size) {
        super();
        this.size = size;
    }

    /**
     * @inheritDoc
     *
     * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
     */
    @Override
    public void readExternal(final ObjectInput in) throws IOException,
                                                          ClassNotFoundException {
        this.size = in.readLong();
        this.nodeId = in.readInt();
    }

    /**
     * @inheritDoc
     *
     * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
     */
    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeLong( this.size );
        out.writeInt( this.nodeId );
    }

    @Override
    public BehaviorType getType() {
        return BehaviorType.TIME_WINDOW;
    }

    public void setWindowNode(WindowNode windowNode) {
        this.nodeId = windowNode.getId();
    }

    /**
     * @return the size
     */
    public long getSize() {
        return size;
    }

    /**
     * @param size the size to set
     */
    public void setSize(final long size) {
        this.size = size;
    }

    @Override
    public BehaviorContext createContext() {
        return new SlidingTimeWindowContext();
    }

    @Override
    public boolean assertFact(final Object context,
                              final FactHandle fact,
                              final PropagationContext pctx,
                              final ReteEvaluator reteEvaluator) {
        final SlidingTimeWindowContext queue = (SlidingTimeWindowContext) context;
        final DefaultEventHandle handle = (DefaultEventHandle) fact;
        long currentTime = reteEvaluator.getTimerService().getCurrentTime();
        if ( isExpired( currentTime, handle ) ) {
            return false;
        }

        queue.add( handle );
        if ( handle.equals( queue.peek() ) ) {
            // update next expiration time
            updateNextExpiration( handle,
                                  reteEvaluator,
                                  queue,
                                  nodeId );
        }

        return true;
    }

    @Override
    public void retractFact(final Object context,
                            final FactHandle fact,
                            final PropagationContext pctx,
                            final ReteEvaluator reteEvaluator) {
        final SlidingTimeWindowContext queue = (SlidingTimeWindowContext) context;
        final DefaultEventHandle handle = (DefaultEventHandle) fact;
        final DefaultEventHandle peekEvent = queue.peek();
        if (peekEvent != null) {
            if (handle.equals(peekEvent)) {
                // it was the head of the queue
                queue.poll();
                // update next expiration time
                updateNextExpiration(queue.peek(), reteEvaluator, queue, nodeId);
            } else if (handle.compareTo(peekEvent) >= 0) {
                // if the event to be removed is older than the peek event we already know that it cannot be there,
                // so it is not necessary to try to remove it (which is an expensive operation)
                queue.remove(handle);
            }
        }
        if ( queue.isEmpty() && queue.getJobHandle() != null ) {
            reteEvaluator.getTimerService().removeJob( queue.getJobHandle() );
        }
    }

    @Override
    public void expireFacts(final Object context,
                            final PropagationContext pctx,
                            final ReteEvaluator reteEvaluator) {
        TimerService clock = reteEvaluator.getTimerService();
        long currentTime = clock.getCurrentTime();
        SlidingTimeWindowContext queue = (SlidingTimeWindowContext) context;

        DefaultEventHandle handle = queue.peek();
        while ( handle != null && isExpired( currentTime, handle ) ) {
            queue.remove();
            if( handle.isValid()) {
                // if not expired yet, expire it
                final PropagationContext expiresPctx = PhreakPropagationContextFactory.createPropagationContextForFact(reteEvaluator, handle, PropagationContext.Type.EXPIRATION);
                ObjectTypeNode.doRetractObject(handle, expiresPctx, reteEvaluator);
            }
            handle = queue.peek();
        }
        // update next expiration time
        updateNextExpiration( handle, reteEvaluator, queue, nodeId );
    }

    protected boolean isExpired(final long currentTime,
                                final DefaultEventHandle handle) {
        return handle.getStartTimestamp() + this.size <= currentTime;
    }

    protected void updateNextExpiration(final InternalFactHandle fact,
                                        final ReteEvaluator reteEvaluator,
                                        final BehaviorContext context,
                                        final int nodeId) {
        TimerService clock = reteEvaluator.getTimerService();
        if ( fact != null ) {
            long nextTimestamp = ((DefaultEventHandle) fact).getStartTimestamp() + getSize();
            if ( nextTimestamp < clock.getCurrentTime() ) {
                // Past and out-of-order events should not be insert,
                // but the engine silently accepts them anyway, resulting in possibly undesirable behaviors
                reteEvaluator.addPropagation(new BehaviorExpireWMAction(nodeId, this, context));
            } else {
                // if there exists already another job it meeans that the new one to be created
                // has to be triggered before the existing one and then we can remove the old one
                if ( context.getJobHandle() != null ) {
                    reteEvaluator.getTimerService().removeJob( context.getJobHandle() );
                }

                JobContext jobctx = new BehaviorJobContext( nodeId, reteEvaluator, this, context);
                JobHandle handle = clock.scheduleJob( job,
                                                      jobctx,
                                                      PointInTimeTrigger.createPointInTimeTrigger( nextTimestamp, null ) );
                jobctx.setJobHandle( handle );
            }
        }
    }

    @Override
    public long getExpirationOffset() {
        return this.size;
    }

    @Override
    public String toString() {
        return "SlidingTimeWindow( size=" + size + " )";
    }

    public static class SlidingTimeWindowContext
            implements
            BehaviorContext,
            Externalizable {

        private PriorityQueue<DefaultEventHandle> queue;
        private JobHandle                      jobHandle;

        public SlidingTimeWindowContext() {
            this.queue = new PriorityQueue<>(16); // arbitrary size... can we improve it?
        }

        @Override
        public JobHandle getJobHandle() {
            return this.jobHandle;
        }

        @Override
        public void setJobHandle(JobHandle jobHandle) {
            this.jobHandle = jobHandle;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void readExternal(ObjectInput in) throws IOException,
                                                        ClassNotFoundException {
            this.queue = (PriorityQueue<DefaultEventHandle>) in.readObject();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( this.queue );
        }

        public void add(DefaultEventHandle handle) {
            queue.add( handle );
        }

        public void remove(DefaultEventHandle handle) {
            queue.remove( handle );
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public DefaultEventHandle peek() {
            return queue.peek( );
        }

        public DefaultEventHandle poll() {
            return queue.poll( );
        }

        public DefaultEventHandle remove() {
            return queue.remove( );
        }

        @Override
        public Collection<DefaultEventHandle> getFactHandles() {
            return queue;
        }
    }

    public static class BehaviorJobContext
            implements
            JobContext,
            Externalizable {
        public ReteEvaluator         reteEvaluator;
        public int                   nodeId;
        public BehaviorRuntime       behavior;
        public BehaviorContext      behaviorContext;

        public BehaviorJobContext(int             nodeId,
                                  ReteEvaluator   reteEvaluator,
                                  BehaviorRuntime behavior,
                                  BehaviorContext behaviorContext) {
            super();
            this.nodeId = nodeId;
            this.reteEvaluator = reteEvaluator;
            this.behavior = behavior;
            this.behaviorContext = behaviorContext;
        }

        /**
         * Do not use this constructor! It should be used just by deserialization.
         */
        public BehaviorJobContext() {
        }

        @Override
        public JobHandle getJobHandle() {
            return behaviorContext.getJobHandle();
        }

        @Override
        public void setJobHandle(JobHandle jobHandle) {
            behaviorContext.setJobHandle( jobHandle );
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                                                        ClassNotFoundException {
            //this.behavior = (O)
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            // TODO Auto-generated method stub
        }

        @Override
        public ReteEvaluator getReteEvaluator() {
            return reteEvaluator;
        }
    }

    public static class BehaviorJob
            implements
            Job {

        @Override
        public void execute(JobContext ctx) {
            BehaviorJobContext context = (BehaviorJobContext) ctx;
            context.reteEvaluator.addPropagation( new BehaviorExpireWMAction( context.nodeId, context.behavior, context.behaviorContext ) );
        }

    }

    public static class BehaviorExpireWMAction
            extends AbstractPropagationEntry<ReteEvaluator>
            implements WorkingMemoryAction {
        protected BehaviorRuntime behavior;
        protected BehaviorContext context;
        protected int nodeId;

        protected BehaviorExpireWMAction() { }

        public BehaviorExpireWMAction(final int nodeId,
                                      BehaviorRuntime behavior,
                                      BehaviorContext context) {
            super();
            this.nodeId = nodeId;
            this.behavior = behavior;
            this.context = context;
        }

        public BehaviorExpireWMAction(MarshallerReaderContext inCtx) throws IOException {
            nodeId = inCtx.readInt();
            WindowNode windowNode = (WindowNode) inCtx.getSinks().get( nodeId );

            WindowMemory memory = inCtx.getWorkingMemory().getNodeMemory( windowNode );

            BehaviorContext[] behaviorContext = memory.behaviorContext;

            int i = inCtx.readInt();

            this.behavior = windowNode.getBehaviors()[i];
            this.context = behaviorContext[i];
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator) {
            this.behavior.expireFacts( context, null, reteEvaluator );
        }
    }
}
