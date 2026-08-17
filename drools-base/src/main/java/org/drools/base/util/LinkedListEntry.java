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
package org.drools.base.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * The idea behind <code>LinkedListNodeWrapper</code> is to be able to add
 * the same <code>LinkedListNode</code> to multiple <code>LinkedList</code>s
 * where the node can have different previous and next nodes in each list.
 */
public class LinkedListEntry<T1 extends DoubleLinkedEntry<T1>, T2> extends AbstractLinkedListNode<T1> implements Externalizable {

    private static final long serialVersionUID = 510l;
    private T2 object;

    public LinkedListEntry() {
    }

    public LinkedListEntry(final T2 object) {
        this.object = object;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        object = (T2) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(object);
    }

    public T2 getObject() {
        return this.object;
    }

    public void setObject(T2 object) {
        this.object = object;
    }

    public int hashCode() {
        return this.object.hashCode();
    }

    public boolean equals(final Object other) {
        return this.object.equals(other);
    }

}
