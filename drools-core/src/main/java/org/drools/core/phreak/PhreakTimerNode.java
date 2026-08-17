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

import java.util.Date;
import java.util.List;

import org.drools.base.time.JobHandle;
import org.drools.base.time.Trigger;
import org.drools.base.time.impl.Timer;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.marshalling.TupleKey;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.TimerNode.TimerNodeMemory;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.time.Job;
import org.drools.core.time.JobContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.DefaultJobHandle;
import org.drools.core.util.index.TupleList;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.conf.TimedRuleExecutionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.PhreakNodeOperations.deleteChildLeftTuple;
import static org.drools.core.phreak.PhreakNodeOperations.normalizeStagedTuples;

public class PhreakTimerNode {
    private static final Logger log = LoggerFactory.getLogger( PhreakTimerNode.class );

    private final ReteEvaluator reteEvaluator;

    public PhreakTimerNode(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
    }

    public void doNode(ActivationsManager activationsManager,
                       SegmentCursor sc, 
                       TimerNode timerNode,
                       TimerNodeMemory tm,
                       LeftTupleSink sink,
                       TupleSets srcLeftTuples,
                       TupleSets stagedLeftTuples,
                       TupleSets trgLeftTuples) {

        if ( srcLeftTuples.getDeleteFirst() != null ) {
            doLeftDeletes( timerNode, tm, sc.getPathMemory(), sink, srcLeftTuples, trgLeftTuples, stagedLeftTuples );
        }

        if ( srcLeftTuples.getUpdateFirst() != null ) {
            doLeftUpdates( timerNode, tm, sc.getPathMemory(), sc.getCurrentSegment(), sink, activationsManager, srcLeftTuples, trgLeftTuples, stagedLeftTuples );
        }

        if ( srcLeftTuples.getInsertFirst() != null ) {
            doLeftInserts( timerNode, tm, sc.getPathMemory(), sc.getCurrentSegment(), sink, activationsManager, srcLeftTuples, trgLeftTuples );
        }

        doPropagateChildLeftTuples( tm, sink, trgLeftTuples, stagedLeftTuples );

        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(TimerNode timerNode,
                              TimerNodeMemory tm,
                              PathMemory pmem,
                              SegmentMemory smem,
                              LeftTupleSink sink,
                              ActivationsManager activationsManager,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples) {
        Timer timer = timerNode.getTimer();
        TimerService timerService = reteEvaluator.getTimerService();
        long timestamp = timerService.getCurrentTime();
        String[] calendarNames = timerNode.getCalendarNames();
        Calendars calendars = reteEvaluator.getCalendars();

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            scheduleLeftTuple( timerNode, tm, pmem, smem, sink, activationsManager, timer, timerService, timestamp, calendarNames, calendars, leftTuple, trgLeftTuples, null );

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doLeftUpdates(TimerNode timerNode,
                              TimerNodeMemory tm,
                              PathMemory pmem,
                              SegmentMemory smem,
                              LeftTupleSink sink,
                              ActivationsManager activationsManager,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        Timer timer = timerNode.getTimer();

        // Variables may have changed for ExpressionIntervalTimer, so it must be rescheduled
        TimerService timerService = reteEvaluator.getTimerService();
        long timestamp = timerService.getCurrentTime();
        String[] calendarNames = timerNode.getCalendarNames();
        Calendars calendars = reteEvaluator.getCalendars();

        for ( TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            DefaultJobHandle jobHandle = (DefaultJobHandle) leftTuple.getContextObject();
            if ( jobHandle != null ) {
                // jobHandle can be null, if the time fired straight away, and never ended up scheduling a job
                timerService.removeJob(jobHandle);
            }
            scheduleLeftTuple( timerNode, tm, pmem, smem, sink, activationsManager, timer, timerService, timestamp, calendarNames, calendars, leftTuple, trgLeftTuples, stagedLeftTuples );

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doLeftDeletes(TimerNode timerNode,
                              TimerNodeMemory tm,
                              PathMemory pmem,
                              LeftTupleSink sink,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        TimerService timerService = reteEvaluator.getTimerService();

        TupleList leftTuples = tm.getInsertOrUpdateLeftTuples();
        TupleList deletes = tm.getDeleteLeftTuples();
        if ( !deletes.isEmpty() ) {
            for ( TupleImpl leftTuple = deletes.getFirst(); leftTuple != null; ) {
                TupleImpl next = leftTuple.getNext();
                srcLeftTuples.addDelete( leftTuple );
                if ( log.isTraceEnabled() ) {
                    log.trace( "Timer Add Postponed Delete {}", leftTuple );
                }
                leftTuple.clear();
                leftTuple = next;
            }
            deletes.clear();
        }
        for ( TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            PropagationContext pctx = leftTuple.findMostRecentPropagationContext();

            Object obj = leftTuple.getContextObject();
            if (obj instanceof DefaultJobHandle) {
                timerService.removeJob( (DefaultJobHandle) obj );
            } else if (obj instanceof TupleKey && pctx.getReaderContext() != null) {
                pctx.getReaderContext().removeTimerNodeScheduler( timerNode.getId(), (TupleKey) obj );
            }

            if ( leftTuple.getMemory() != null ) {
                leftTuples.remove( leftTuple ); // it gets removed either way.
                if ( ((InternalFactHandle)pctx.getFactHandle()).isExpired() ) {
                    // a expire clashes with insert or update, allow it to propagate once, will handle the expire the second time around
                    doPropagateChildLeftTuple( sink, trgLeftTuples, stagedLeftTuples, leftTuple );
                    tm.getDeleteLeftTuples().add( leftTuple );
                    pmem.doLinkRule( ); // make sure it's dirty, so it'll evaluate again
                    if ( log.isTraceEnabled() ) {
                        log.trace( "Timer Postponed Delete {}", leftTuple );
                    }
                }
            }

            if ( leftTuple.getMemory() == null ) {
                // if it's != null, then it's already been postponed, and the existing child propagated
                TupleImpl childLeftTuple = leftTuple.getFirstChild(); // only has one child
                if ( childLeftTuple != null ) {
                    childLeftTuple.setPropagationContext( leftTuple.getPropagationContext() );
                    deleteChildLeftTuple(childLeftTuple, trgLeftTuples, stagedLeftTuples);
                    if ( log.isTraceEnabled() ) {
                        log.trace( "Timer Delete {}", leftTuple );
                    }
                }
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    private void scheduleLeftTuple(final TimerNode timerNode,
                                   final TimerNodeMemory tm,
                                   final PathMemory pmem,
                                   final SegmentMemory smem,
                                   final LeftTupleSink sink,
                                   final ActivationsManager activationsManager,
                                   final Timer timer,
                                   final TimerService timerService,
                                   final long timestamp,
                                   final String[] calendarNames,
                                   final Calendars calendars,
                                   final TupleImpl leftTuple,
                                   final TupleSets trgLeftTuples,
                                   final TupleSets stagedLeftTuples) {
        if ( leftTuple.getPropagationContext().getReaderContext() == null ) {
            final Trigger trigger = createTrigger( timerNode, timer, timestamp, calendarNames, calendars, leftTuple );

            // regular propagation
            scheduleTimer( timerNode, tm, smem, sink, timerService, timestamp, leftTuple, trgLeftTuples, stagedLeftTuples, trigger );
        } else {
            // de-serializing, so we need to correlate timers before scheduling them
            Scheduler scheduler = new Scheduler() {
                @Override
                public void schedule( Trigger t ) {
                    scheduleTimer( timerNode, tm, smem, sink, timerService, timestamp, leftTuple, trgLeftTuples, stagedLeftTuples, t );
                    reteEvaluator.getRuleNetworkEvaluator().evaluate(pmem, activationsManager, sink, tm, trgLeftTuples);
                }
                @Override
                public Trigger getTrigger() {
                    return createTrigger( timerNode, timer, timestamp, calendarNames, calendars, leftTuple );
                }
            };
            TupleKey key = TupleKey.createTupleKey( leftTuple );
            leftTuple.getPropagationContext().getReaderContext().addTimerNodeScheduler( timerNode.getId(), key, scheduler );
            leftTuple.setContextObject( key );
        }
    }

    private Trigger createTrigger(final TimerNode timerNode,
                                  final Timer timer,
                                  final long timestamp,
                                  final String[] calendarNames,
                                  final Calendars calendars,
                                  final TupleImpl leftTuple) {
        Object obj = leftTuple.getContextObject();
        DefaultJobHandle jobHandle = obj instanceof DefaultJobHandle ? (DefaultJobHandle) obj : null;
        return timer.createTrigger( timestamp, leftTuple, jobHandle, calendarNames, calendars, timerNode.getStartEndDeclarations(), reteEvaluator );
    }

    public interface Scheduler {
        void schedule( Trigger t );
        Trigger getTrigger();
    }

    private void scheduleTimer(TimerNode timerNode,
                               TimerNodeMemory tm,
                               SegmentMemory smem,
                               LeftTupleSink sink,
                               TimerService timerService,
                               long timestamp,
                               TupleImpl leftTuple,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples,
                               Trigger trigger) {
        if ( trigger.hasNextFireTime() == null ) {
            return;
        }

        if ( trigger.hasNextFireTime().getTime() <= timestamp ) {
            // first execution is straight away, so void Scheduling
            if ( log.isTraceEnabled() ) {
                log.trace( "Timer Fire Now {}", leftTuple );
            }

            TupleImpl childLeftTuple = doPropagateChildLeftTuple(sink, trgLeftTuples, stagedLeftTuples, leftTuple );
            if (childLeftTuple.getStagedType() != LeftTuple.NONE) {
                // Flag the newly created childLeftTuple to avoid a reevaluation in case it gets
                // rescheduled before the end of this doNode loop
                childLeftTuple.setContextObject( Boolean.TRUE );
            }

            trigger.nextFireTime();

            Date nextFireTime = trigger.hasNextFireTime();
            if ( nextFireTime != null && nextFireTime.getTime() <= timestamp ) {
                throw new IllegalStateException( "Trigger.nextFireTime is not increasing" );
            }
        }

        if ( trigger.hasNextFireTime() != null ) {
            // can be null, if the system timestamp has surpassed when this was suppose to fire
            TimerNodeJob job = new TimerNodeJob();
            TimerNodeJobContext jobCtx = new TimerNodeJobContext( timerNode.getId(), trigger, leftTuple, tm, sink, smem.getPathMemories(), reteEvaluator );

            DefaultJobHandle jobHandle = (DefaultJobHandle) timerService.scheduleJob( job, jobCtx, trigger );
            leftTuple.setContextObject( jobHandle );

            if ( log.isTraceEnabled() ) {
                log.trace( "Timer Scheduled {}", leftTuple );
            }
        }
    }

    private static void doPropagateChildLeftTuples(TimerNodeMemory tm,
                                                   LeftTupleSink sink,
                                                   TupleSets trgLeftTuples,
                                                   TupleSets stagedLeftTuples) {
        TupleList leftTuples = tm.getInsertOrUpdateLeftTuples();
        for ( TupleImpl leftTuple = leftTuples.getFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getNext();

            doPropagateChildLeftTuple( sink, trgLeftTuples, stagedLeftTuples, leftTuple );

            leftTuple.clear();
            leftTuple = next;
        }
        // doLeftDeletes handles deletes, directly into the trgLeftTuples
        if ( tm.getDeleteLeftTuples().isEmpty() ) {
            // dirty bit can only be reset when there are no InsertOUdate LeftTuples and no Delete staged LeftTuples.
            tm.setNodeCleanWithoutNotify();
        }
        leftTuples.clear();
    }

    private static TupleImpl doPropagateChildLeftTuple(LeftTupleSink sink,
                                                       TupleSets trgLeftTuples,
                                                       TupleSets stagedLeftTuples,
                                                       TupleImpl leftTuple) {
        TupleImpl childLeftTuple = leftTuple.getFirstChild();
        if ( childLeftTuple == null ) {
            childLeftTuple = TupleFactory.createLeftTuple(leftTuple, sink, leftTuple.getPropagationContext(), true);
            trgLeftTuples.addInsert(childLeftTuple);
            if ( log.isTraceEnabled() ) {
                log.trace( "Timer Insert {}", childLeftTuple );
            }
        } else if (childLeftTuple.getContextObject() == Boolean.TRUE) {
            // This childLeftTuple has been created in this doNode loop, just skip it
            childLeftTuple.setContextObject( null );
        } else {
            normalizeStagedTuples(stagedLeftTuples, childLeftTuple);
            trgLeftTuples.addUpdate( childLeftTuple );
            if ( log.isTraceEnabled() ) {
                log.trace( "Timer Update {}", childLeftTuple );
            }
        }
        return childLeftTuple;
    }

    public static class TimerNodeJob
            implements
            Job {
        public void execute(JobContext ctx) {
            TimerNodeJobContext timerJobCtx = (TimerNodeJobContext) ctx;
            ReteEvaluator reteEvaluator = timerJobCtx.getReteEvaluator();
            reteEvaluator.addPropagation( new TimerAction( timerJobCtx ) );
        }
    }

    public static class TimerAction
            extends AbstractPropagationEntry<ReteEvaluator>
            implements WorkingMemoryAction {

        private final TimerNodeJobContext timerJobCtx;

        private TimerAction( TimerNodeJobContext timerJobCtx ) {
            this.timerJobCtx = timerJobCtx;
        }

        @Override
        public boolean requiresImmediateFlushing() {
            return timerJobCtx.getReteEvaluator().getRuleSessionConfiguration().getTimedRuleExecutionFilter() != null;
        }

        @Override
        public void internalExecute(final ReteEvaluator reteEvaluator) {
            execute( reteEvaluator, false );
        }

        public void execute( final ReteEvaluator reteEvaluator, boolean needEvaluation ) {
            TupleList leftTuples = timerJobCtx.getTimerNodeMemory().getInsertOrUpdateLeftTuples();
            TupleImpl lt = timerJobCtx.getTuple();

            if ( log.isTraceEnabled() ) {
                log.trace( "Timer Executor {} {}", timerJobCtx.getTrigger(), lt );
            }

            if ( timerJobCtx.getJobHandle().isCancel() ) {
                // this is to force a sync point, as during update propagate it can cancel the FH
                // we cannot have an update processed at the same timer is firing
                return;
            }
            if ( lt.getMemory() == null ) {
                // don't add it, if it's already added, which could happen with interval or cron timers
                leftTuples.add( lt );
            }

            timerJobCtx.getTimerNodeMemory().setNodeDirtyWithoutNotify();

            TimedRuleExecutionFilter filter = reteEvaluator.getRuleSessionConfiguration().getTimedRuleExecutionFilter();
            needEvaluation &= filter != null;

            for (final PathMemory pmem : timerJobCtx.getPathMemories()) {
                if (pmem.getPathEndNode().getAssociatedTerminalsSize() == 0) {
                    // if the corresponding rule has been removed avoid to link and notify this pmem
                    continue;
                }
                ActivationsManager activationsManager = pmem.getActualActivationsManager( );
                pmem.doLinkRule( );

                if (needEvaluation && filter.accept(new Rule[]{pmem.getRule()})) {
                    evaluateAndFireRule( pmem, reteEvaluator, activationsManager );
                }
            }
        }

        private void evaluateAndFireRule(PathMemory pmem, ReteEvaluator reteEvaluator, ActivationsManager activationsManager) {
            RuleExecutor ruleExecutor = pmem.getRuleAgendaItem().getRuleExecutor();
            ruleExecutor.evaluateNetworkIfDirty(reteEvaluator, activationsManager);
            ruleExecutor.fire( reteEvaluator, activationsManager );
        }
    }

    public static class TimerNodeJobContext
            implements
            JobContext {
        private       JobHandle             jobHandle;
        private final Trigger               trigger;

        private final TupleImpl             tuple;
        private final int                   timerNodeId;
        private final TimerNodeMemory       tm;

        private final LeftTupleSink         sink;
        private final List<PathMemory>      pmems;
        private final ReteEvaluator         reteEvaluator;

        public TimerNodeJobContext(int timerNodeId,
                                   Trigger trigger,
                                   TupleImpl tuple,
                                   TimerNodeMemory tm,
                                   LeftTupleSink sink,
                                   List<PathMemory> pmems,
                                   ReteEvaluator reteEvaluator) {
            this.timerNodeId = timerNodeId;
            this.trigger = trigger;
            this.tuple = tuple;
            this.sink = sink;
            this.pmems = pmems;
            this.tm = tm;
            this.reteEvaluator = reteEvaluator;
        }

        public JobHandle getJobHandle() {
            return this.jobHandle;
        }

        @Override
        public ReteEvaluator getReteEvaluator() {
            return reteEvaluator;
        }

        public void setJobHandle(JobHandle jobHandle) {
            this.jobHandle = jobHandle;
        }

        public LeftTupleSink getSink() {
            return sink;
        }

        public TupleImpl getTuple() {
            return tuple;
        }

        public TimerNodeMemory getTimerNodeMemory() {
            return tm;
        }

        public List<PathMemory> getPathMemories() {
            return pmems;
        }

        public Trigger getTrigger() {
            return trigger;
        }

        public int getTimerNodeId() {
            return timerNodeId;
        }
    }
}
