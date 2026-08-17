/**
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
package org.drools.base.phreak;

import org.drools.base.base.ValueResolver;

public interface PropagationEntry<T extends ValueResolver> {

    default void execute(T t) {
        internalExecute(t);
    }

    void internalExecute(T t);

    PropagationEntry<T> getNext();

    void setNext(PropagationEntry<T> next);

    boolean requiresImmediateFlushing();

    boolean isCalledFromRHS();

    boolean isPartitionSplittable();

    PropagationEntry<T> getSplitForPartition(int partitionNr);

    boolean defersExpiration();

}
