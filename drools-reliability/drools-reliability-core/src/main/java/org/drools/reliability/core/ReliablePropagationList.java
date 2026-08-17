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
package org.drools.reliability.core;

import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.Storage;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.SynchronizedPropagationList;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ReliablePropagationList extends SynchronizedPropagationList implements Externalizable {

    public static final String PROPAGATION_LIST = "PropagationList";

    public ReliablePropagationList(ReteEvaluator reteEvaluator) {
        super(reteEvaluator);
    }

    public ReliablePropagationList(){super();}

    public void setReteEvaluator(ReteEvaluator reteEvaluator){this.reteEvaluator=reteEvaluator;}

    public ReliablePropagationList(ReteEvaluator reteEvaluator, ReliablePropagationList originalList) {
        super(reteEvaluator);
        this.head = originalList.head;
        this.tail = originalList.tail;
    }

    @Override
    public synchronized PropagationEntry<ReteEvaluator> takeAll() {
        PropagationEntry<ReteEvaluator> p = super.takeAll();
        Storage<String, Object> componentsStorage = StorageManagerFactory.get().getStorageManager().getOrCreateStorageForSession(this.reteEvaluator, "components");
        componentsStorage.put(PROPAGATION_LIST, this);
        return p;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(head);
        out.writeObject(tail);
        out.writeBoolean(disposed);
        out.writeBoolean(hasEntriesDeferringExpiration);
        out.writeBoolean(firingUntilHalt);
   }
    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.head = (PropagationEntry<ReteEvaluator>) in.readObject();
        this.tail = (PropagationEntry<ReteEvaluator>) in.readObject();
        this.disposed = in.readBoolean();
        this.hasEntriesDeferringExpiration = in.readBoolean();
        this.firingUntilHalt = in.readBoolean();
    }
}
