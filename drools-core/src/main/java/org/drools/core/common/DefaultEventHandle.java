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
package org.drools.core.common;

import org.drools.base.rule.EntryPointId;
import org.drools.base.time.JobHandle;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.DefaultJobHandle;
import org.drools.base.util.LinkedList;
import org.kie.api.runtime.rule.EventHandle;

public class DefaultEventHandle extends DefaultFactHandle implements EventHandle, Comparable<DefaultEventHandle> {

    private static final long serialVersionUID = 510l;

    static final String EVENT_FORMAT_VERSION = "5";

    private long              startTimestamp;
    private long              duration;
    private boolean           expired;
    private boolean           pendingRemoveFromStore;
    private int               otnCount;

    private DefaultEventHandle linkedFactHandle;

    private final transient LinkedList<DefaultJobHandle> jobs = new LinkedList<>();

    public DefaultEventHandle() {
        super();
        this.startTimestamp = 0;
        this.duration = 0;
    }

    public DefaultEventHandle(long id, EntryPointId entryPointId) {
        super(id, null);
        this.startTimestamp = 0;
        this.duration = 0;
        this.entryPointId = entryPointId;
    }

    /**
     * Creates a new event fact handle.
     *
     * @param id this event fact handle ID
     * @param object the event object encapsulated in this event fact handle
     * @param recency the recency of this event fact handle
     * @param timestamp the timestamp of the occurrence of this event
     * @param duration the duration of this event. May be 0 (zero) in case this is a primitive event.
     */
    public DefaultEventHandle(long id,
                              Object object,
                              long recency,
                              long timestamp,
                              long duration,
                              WorkingMemoryEntryPoint wmEntryPoint) {
        super( id, object, recency, wmEntryPoint );
        this.startTimestamp = timestamp;
        this.duration = duration;
    }

    protected DefaultEventHandle(long id,
                                 int identityHashCode,
                                 Object object,
                                 long recency,
                                 long timestamp,
                                 long duration,
                                 EntryPointId entryPointId) {

        super( id, identityHashCode, object, recency, entryPointId );
        this.startTimestamp = timestamp;
        this.duration = duration;
    }

    protected String getFormatVersion() {
        return EVENT_FORMAT_VERSION;
    }

    /**
     * @see Object
     */
    public String toString() {
        return toExternalForm();
    }

    /**
     * Always returns true, since the EventFactHandle is
     * only used for Events, and not for regular Facts
     */
    public boolean isEvent() {
        return true;
    }

    /**
     * Returns the timestamp of the occurrence of this event.
     * @return
     */
    public long getStartTimestamp() {
        return startTimestamp;
    }

    /**
     * Returns the duration of this event. In case this is a primitive event,
     * returns 0 (zero).
     *
     * @return
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Returns the end timestamp for this event. This is the same as:
     *
     * startTimestamp + duration
     *
     * @return
     */
    public long getEndTimestamp() {
        return this.startTimestamp + this.duration;
    }

    public DefaultEventHandle getLinkedFactHandle() {
        return linkedFactHandle;
    }

    @Override
    public void invalidate() {
        if ( linkedFactHandle != null ) {
            linkedFactHandle.invalidate();
        }  else {
            super.invalidate();
        }
    }

    @Override
    public boolean isValid() {
        if ( linkedFactHandle != null ) {
            return linkedFactHandle.isValid();
        }  else {
            return super.isValid();
        }
    }

    @Override
    public boolean isExpired() {
        if ( linkedFactHandle != null ) {
            return linkedFactHandle.isExpired();
        }  else {
            return expired;
        }
    }

    public void setExpired(boolean expired) {
        if ( linkedFactHandle != null ) {
            linkedFactHandle.setExpired(expired);
        }  else {
            this.expired = expired;
        }
    }

    public boolean isPendingRemoveFromStore() {
        if ( linkedFactHandle != null ) {
            return linkedFactHandle.isPendingRemoveFromStore();
        }  else {
            return pendingRemoveFromStore;
        }
    }

    public void setPendingRemoveFromStore(boolean pendingRemove) {
        if ( linkedFactHandle != null ) {
            linkedFactHandle.setPendingRemoveFromStore(pendingRemove);
        }  else {
            this.pendingRemoveFromStore = pendingRemove;
        }
    }

    public void increaseOtnCount() {
        otnCount++;
    }

    public void decreaseOtnCount() {
        otnCount--;
    }

    public int getOtnCount() {
        return otnCount;
    }

    public void setOtnCount( int otnCount ) {
        this.otnCount = otnCount;
    }

    public DefaultEventHandle clone() {
        DefaultEventHandle clone = new DefaultEventHandle(getId(),
                                                          getIdentityHashCode(),
                                                          getObject(),
                                                          getRecency(),
                                                          getStartTimestamp(),
                                                          getDuration(),
                                                          getEntryPointId() );
        clone.setOtnCount( getOtnCount() );
        clone.setExpired( isExpired() );
        clone.setEqualityKey( getEqualityKey() );
        clone.linkedTuples = this.linkedTuples.clone();
        clone.setObjectHashCode(getObjectHashCode());
        clone.wmEntryPoint = this.wmEntryPoint;
        return clone;
    }

    private DefaultEventHandle cloneWithoutTuples() {
        DefaultEventHandle clone = new DefaultEventHandle(getId(),
                                                          getIdentityHashCode(),
                                                          getObject(),
                                                          getRecency(),
                                                          getStartTimestamp(),
                                                          getDuration(),
                                                          getEntryPointId() );
        clone.setOtnCount( getOtnCount() );
        clone.setExpired( isExpired() );
        clone.setEqualityKey( getEqualityKey() );
        clone.linkedTuples = this.linkedTuples.cloneEmpty();
        clone.setObjectHashCode(getObjectHashCode());
        clone.wmEntryPoint = this.wmEntryPoint;
        return clone;
    }

    public DefaultEventHandle cloneAndLink() {
        DefaultEventHandle clone = cloneWithoutTuples();
        clone.linkedFactHandle = this;
        return clone;
    }

    public void quickCloneUpdate(DefaultFactHandle clone) {
        clone.setObject( getObject() );
        clone.setRecency( getRecency() );
        clone.setEqualityKey( getEqualityKey() );

        clone.setObjectHashCode( getObjectHashCode() );
        clone.setIdentityHashCode( getIdentityHashCode() );
        clone.setTraitType( getTraitType() );
        clone.setDisconnected( isDisconnected() );
        clone.setNegated( isNegated() );
    }

    public int compareTo(DefaultEventHandle e) {
        return (getStartTimestamp() < e.getStartTimestamp()) ? -1 : (getStartTimestamp() == e.getStartTimestamp() ? 0 : 1);
    }

    public void addJob(DefaultJobHandle job) {
        synchronized (jobs) {
            jobs.add(job);
        }
    }

    public void removeJob(DefaultJobHandle job) {
        synchronized (jobs) {
            // the job could have been already removed if the event has been just retracted
            // and then the unscheduleAllJobs method has been invoked concurrently
            if (jobs.contains(job)) {
                jobs.remove(job);
            }
        }
    }

    public void unscheduleAllJobs(ReteEvaluator reteEvaluator) {
        if (!jobs.isEmpty()) {
            synchronized (jobs) {
                TimerService clock = reteEvaluator.getTimerService();
                while ( !jobs.isEmpty() ) {
                    JobHandle job = jobs.removeFirst();
                    clock.removeJob(job);
                }
            }
        }
    }
}
