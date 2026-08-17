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
package org.drools.core.time.impl;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Stack;

import org.drools.base.time.JobHandle;
import org.drools.base.time.Trigger;
import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.time.Job;
import org.drools.core.time.JobContext;
import org.drools.core.time.TimerService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDKTimerServiceTest {
    
    @Test
    public void testSingleExecutionJob() throws Exception {
        SessionConfiguration config = RuleBaseFactory.newKnowledgeSessionConfiguration().as(SessionConfiguration.KEY);
        config.setClockType(ClockType.REALTIME_CLOCK);
        TimerService timeService = config.createTimerService();
        Trigger trigger = new DelayedTrigger( 100 );
        HelloWorldJobContext ctx = new HelloWorldJobContext( "hello world", timeService);
        timeService.scheduleJob( new HelloWorldJob(), ctx,  trigger);
        Thread.sleep( 500 );
        timeService.shutdown();
        assertThat(ctx.getList()).hasSize(1);
    }
    
    @Test
    public void testRepeatedExecutionJob() throws Exception {
        SessionConfiguration config = RuleBaseFactory.newKnowledgeSessionConfiguration().as(SessionConfiguration.KEY);
        config.setClockType(ClockType.REALTIME_CLOCK);
        TimerService timeService = config.createTimerService();
        Trigger trigger = new DelayedTrigger(  new long[] { 100, 100, 100} );
        HelloWorldJobContext ctx = new HelloWorldJobContext( "hello world", timeService);
        timeService.scheduleJob( new HelloWorldJob(), ctx,  trigger);
        Thread.sleep( 500 );
        timeService.shutdown();
        assertThat(ctx.getList()).hasSize(3);
    }
        
    
    @Test
    public void testRepeatedExecutionJobWithRemove() throws Exception {
        SessionConfiguration config = RuleBaseFactory.newKnowledgeSessionConfiguration().as(SessionConfiguration.KEY);
        config.setClockType(ClockType.REALTIME_CLOCK);
        TimerService timeService = config.createTimerService();
        Trigger trigger = new DelayedTrigger(  new long[] {100, 100, 100, 100, 100, 100, 100, 100} ); 
        HelloWorldJobContext ctx = new HelloWorldJobContext( "hello world", timeService);
        ctx.setLimit( 3 );
        timeService.scheduleJob( new HelloWorldJob(), ctx,  trigger);
        Thread.sleep( 1000 );
        timeService.shutdown();
        assertThat(ctx.getList()).hasSize(5);
    }

    public static class HelloWorldJob implements Job {
        public void execute(JobContext c) {
            HelloWorldJobContext ctx = (HelloWorldJobContext) c;
            int counter = ctx.increaseCounter();
            if ( counter > 3 ) {
                ctx.timeService.removeJob( ctx.getJobHandle() );
            }
            ctx.getList().add( ((HelloWorldJobContext)ctx).getMessage() + " : " + counter);
        }
    }

    public static class HelloWorldJobContext implements JobContext {
        private String message;
        private  TimerService timeService;
        private JobHandle jobHandle;

        private List list;

        private int counter;
        private int limit;

        public HelloWorldJobContext(String message, TimerService timeService) {
            this.message = message;
            this.timeService = timeService;
            this.list = new ArrayList();
        }

        public String getMessage() {
            return this.message;
        }

        public int increaseCounter() {
            return this.counter++;
        }

        public JobHandle getJobHandle() {
            return this.jobHandle;
        }

        public void setJobHandle(JobHandle jobHandle) {
            this.jobHandle = jobHandle;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public List getList() {
            return list;
        }

        @Override
        public ReteEvaluator getReteEvaluator() {
            return (ReteEvaluator) Proxy.newProxyInstance( InternalWorkingMemory.class.getClassLoader(),
                                                                   new Class[]{InternalWorkingMemory.class},
                                                                   new InvocationHandler() {
                                                                       @Override
                                                                       public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
                                                                           if (method.getName().equals( "addPropagation" )) {
                                                                               ( (PropagationEntry) args[0] ).execute( (InternalWorkingMemory)null );
                                                                           }
                                                                           return null;
                                                                       }
                                                                   } );
        }
    }

    public static class DelayedTrigger implements Trigger {
        private Stack<Date> stack;

        public DelayedTrigger(long delay) {
            this( new long[] { delay } );
        }

        public DelayedTrigger(long[] delay) {
            this.stack = new Stack<Date>();
            for( int i = delay.length-1; i >= 0; i-- ) {
                this.stack.push( new Date( new Date().getTime() + delay[i] ) );
            }
        }

        public Date hasNextFireTime() {
            return this.stack.isEmpty() ? null : this.stack.peek();
        }
        
        public Date nextFireTime() {
            return this.stack.isEmpty() ? null : this.stack.pop();
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            // FIXME : not safe, since timestamps will be wrong
            this.stack = (Stack<Date>) in.readObject();
            
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            // FIXME : not safe, since timestamps will be wrong
            out.writeObject( stack );
        }

    }
}
