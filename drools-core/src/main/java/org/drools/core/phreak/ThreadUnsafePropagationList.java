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

import java.util.Collections;
import java.util.Iterator;

import org.drools.base.phreak.PropagationEntry;
import org.drools.core.common.ReteEvaluator;

public class ThreadUnsafePropagationList implements PropagationList {

    private final ReteEvaluator reteEvaluator;

    public ThreadUnsafePropagationList( ReteEvaluator reteEvaluator ) {
        this.reteEvaluator = reteEvaluator;
    }

    @Override
    public void addEntry( PropagationEntry<ReteEvaluator> propagationEntry ) {
        propagationEntry.execute( reteEvaluator );
        reteEvaluator.onWorkingMemoryAction( propagationEntry );
    }

    @Override
    public PropagationEntry<ReteEvaluator> takeAll() {
        return null;
    }

    @Override
    public void flush() {
    }

    @Override
    public void flush( PropagationEntry<ReteEvaluator> currentHead ) {
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean hasEntriesDeferringExpiration() {
        return false;
    }

    @Override
    public Iterator<PropagationEntry<ReteEvaluator>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public void waitOnRest() {
    }

    @Override
    public void notifyWaitOnRest() {
    }

    @Override
    public void onEngineInactive() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void setFiringUntilHalt( boolean firingUntilHalt ) {
    }
}
