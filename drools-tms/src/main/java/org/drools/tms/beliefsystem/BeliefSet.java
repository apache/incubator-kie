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
package org.drools.tms.beliefsystem;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.base.util.FastIterator;
import org.drools.core.common.PropagationContext;

public interface BeliefSet<M extends ModedAssertion<M>> {
    BeliefSystem getBeliefSystem();
    
    InternalFactHandle getFactHandle();
    
    M getFirst();

    FastIterator fastIterator();

    void add(M node);
    void remove(M node);
    
    boolean isEmpty();
    int size();

    /**
     *  This will remove all entries and do clean up, like retract FHs.
     * @param propagationContext
     */
    void cancel(final PropagationContext propagationContext);

    /**
     * This will remove all entries, but not do cleanup, the FH is most likely needed else where
     * @param propagationContext
     */
    void clear(PropagationContext propagationContext);
    
    void setWorkingMemoryAction(WorkingMemoryAction wmAction);

    boolean isNegated();

    boolean isDecided();

    boolean isConflicting();

    boolean isPositive();

}
