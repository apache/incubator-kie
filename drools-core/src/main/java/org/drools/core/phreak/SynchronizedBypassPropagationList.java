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

import java.util.concurrent.atomic.AtomicBoolean;

import org.drools.base.phreak.PropagationEntry;
import org.drools.core.common.ReteEvaluator;

public class SynchronizedBypassPropagationList extends SynchronizedPropagationList {

    private AtomicBoolean executing = new AtomicBoolean( false );

    public SynchronizedBypassPropagationList(ReteEvaluator reteEvaluator) {
        super(reteEvaluator);
    }

    @Override
    public void addEntry(final PropagationEntry<ReteEvaluator> propagationEntry) {
        reteEvaluator.getActivationsManager().executeTask( new ExecutableEntry() {
           @Override
           public void execute() {
               if (executing.compareAndSet( false, true )) {
                   try {
                       propagationEntry.execute( reteEvaluator );
                       reteEvaluator.onWorkingMemoryAction( propagationEntry );
                   } finally {
                       executing.set( false );
                       flush();
                   }
               } else {
                   doAdd();
               }
           }

           @Override
           public void enqueue() {
               doAdd();
           }

            private void doAdd() {
                internalAddEntry( propagationEntry );
           }
        });
        notifyWaitOnRest();
    }

    @Override
    public void flush() {
        if (!executing.get()) {
            PropagationEntry<ReteEvaluator> head = takeAll();
            while (head != null) {
                flush( head );
                head = takeAll();
            }
        }
    }

    @Override
    public void onEngineInactive() {
        flush();
    }
}
