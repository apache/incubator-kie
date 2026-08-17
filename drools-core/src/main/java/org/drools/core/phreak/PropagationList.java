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

import java.util.Iterator;

import org.drools.base.phreak.PropagationEntry;
import org.drools.core.common.ReteEvaluator;

public interface PropagationList {
    void addEntry(PropagationEntry<ReteEvaluator> propagationEntry);

    PropagationEntry<ReteEvaluator> takeAll();

    void flush();
    void flush( PropagationEntry<ReteEvaluator> currentHead );

    void reset();

    boolean isEmpty();

    boolean hasEntriesDeferringExpiration();

    Iterator<PropagationEntry<ReteEvaluator>> iterator();

    void waitOnRest();

    void notifyWaitOnRest();

    void onEngineInactive();

    void dispose();

    void setFiringUntilHalt( boolean firingUntilHalt );
}
