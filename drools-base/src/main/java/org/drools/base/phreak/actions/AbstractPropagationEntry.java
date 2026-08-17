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
package org.drools.base.phreak.actions;

import org.drools.base.base.ValueResolver;
import org.drools.base.phreak.PropagationEntry;

public abstract class AbstractPropagationEntry<T extends ValueResolver> implements PropagationEntry<T> {
    protected PropagationEntry<T> next;

    public void setNext(PropagationEntry<T> next) {
        this.next = next;
    }

    public PropagationEntry<T> getNext() {
        return next;
    }

    @Override
    public boolean requiresImmediateFlushing() {
        return false;
    }

    @Override
    public boolean isCalledFromRHS() {
        return false;
    }

    @Override
    public boolean isPartitionSplittable() {
        return false;
    }

    @Override
    public boolean defersExpiration() {
        return false;
    }

    @Override
    public PropagationEntry<T> getSplitForPartition(int partitionNr) {
        throw new UnsupportedOperationException();
    }
}
