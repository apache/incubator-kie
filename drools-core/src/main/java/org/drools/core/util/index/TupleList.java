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
package org.drools.core.util.index;

import java.io.Serializable;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.base.util.SingleLinkedEntry;
import org.drools.base.util.FastIterator;
import org.drools.base.util.LinkedList;

public class TupleList extends LinkedList<TupleImpl> implements TupleMemory, SingleLinkedEntry<TupleList>, Serializable {

    public static final long       serialVersionUID = 510l;

    private TupleList           next;

    public TupleList() {
    }

    public TupleList( TupleImpl first, TupleImpl last, int size ) {
        super(first, last, size);
    }

    @Override
    public void add(TupleImpl node) {
        super.add(node);
        node.setMemory(this);
    }

    @Override
    public void remove(TupleImpl node) {
        super.remove(node);
        node.clear();
    }

    @Override
    public TupleImpl getFirst(TupleImpl tuple) {
        return getFirst();
    }

    public TupleImpl get(final InternalFactHandle handle) {
        TupleImpl current = getFirst();
        while ( current != null ) {
            if ( handle == current.getFactHandle() ) {
                return current;
            }
            current = current.getNext();
        }
        return null;
    }

    public TupleImpl removeFirst() {
        TupleImpl node = super.removeFirst();
        node.clear();
        return node;
    }

    public TupleImpl removeLast() {
        TupleImpl node = super.removeLast();
        node.clear();
        return node;
    }

    @Override
    public IndexType getIndexType() {
        return TupleMemory.IndexType.NONE;
    }

    @Override
    public FastIterator<TupleImpl> fastIterator() {
        return LinkedList.fastIterator; // contains no state, so ok to be static
    }

    @Override
    public FastIterator<TupleImpl> fullFastIterator() {
        return LinkedList.fastIterator; // contains no state, so ok to be static
    }

    @Override
    public FastIterator<TupleImpl> fullFastIterator(TupleImpl tuple) {
        return LinkedList.fastIterator; // contains no state, so ok to be static
    }

    @Override
    public boolean isIndexed() {
        return false;
    }

    public TupleList getNext() {
        return this.next;
    }

    public void setNext(final TupleList next) {
        this.next = next;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        FastIterator<TupleImpl> it = super.fastIterator();
        for ( TupleImpl tuple = getFirst(); tuple != null; tuple = it.next(tuple) ) {
            builder.append(tuple).append("\n");
        }

        return builder.toString();
    }

    protected void copyStateInto(TupleList other) {
        super.copyStateInto(other);

        for ( TupleImpl current = getFirst(); current != null; current = current.getNext() ) {
            current.setMemory(other);
        }
    }
}
