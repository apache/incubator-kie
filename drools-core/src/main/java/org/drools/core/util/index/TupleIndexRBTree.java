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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.util.IndexedValueReader;
import org.drools.base.util.index.ConstraintTypeOperator;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.base.util.FastIterator;
import org.drools.base.util.Iterator;
import org.drools.core.util.TupleRBTree;
import org.drools.core.util.TupleRBTree.Boundary;
import org.drools.core.util.TupleRBTree.Node;
import org.drools.util.CoercionUtil;

public class TupleIndexRBTree extends AbstractTupleIndexTree implements Externalizable, TupleMemory {

    private TupleRBTree<Comparable<Comparable>> tree;

    public TupleIndexRBTree() {
        // constructor for serialisation
    }

    public TupleIndexRBTree(ConstraintTypeOperator constraintType, IndexedValueReader index, boolean left) {
        this.index = index;
        this.constraintType = constraintType;
        this.left = left;
        tree = new TupleRBTree<>();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( tree );
        out.writeObject( index );
        out.writeObject( constraintType );
        out.writeInt(factSize);
        out.writeBoolean( left );
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        tree = (TupleRBTree<Comparable<Comparable>>) in.readObject();
        index = (IndexedValueReader) in.readObject();
        constraintType = (ConstraintTypeOperator) in.readObject();
        factSize = in.readInt();
        left = in.readBoolean();
    }

    public void add(TupleImpl tuple) {
        Comparable key = getIndexedValue(tuple, left);
        TupleList list = tree.insert(key);
        list.add(tuple);
        factSize++;
    }

    public void remove(TupleImpl tuple) {
        TupleList list = tuple.getMemory();
        list.remove(tuple);
        if (list.getFirst() == null) {
            tree.delete(((Node<Comparable<Comparable>>)list).key);
        }
        factSize--;
    }

    public void removeAdd(TupleImpl tuple) {
        remove(tuple);
        add(tuple);
    }

    public int size() {
        return factSize;
    }


    public TupleImpl getFirst(TupleImpl rightTuple) {
        Comparable key = getIndexedValue( rightTuple, !left );
        return getNext(key, true);
    }

    public Iterator<TupleImpl> iterator() {
        TupleList list = tree.first();
        TupleImpl firstTuple = list != null ? list.getFirst() : null;
        return new FastIterator.IteratorAdapter(fastIterator(), firstTuple);
    }

    public FastIterator<TupleImpl> fastIterator() {
        return new TupleFastIterator();
    }

    public FastIterator<TupleImpl> fullFastIterator() {
        return new TupleFastIterator();
    }

    public FastIterator<TupleImpl> fullFastIterator(TupleImpl leftTuple) {
        FastIterator fastIterator = fullFastIterator();
        Comparable key = getLeftIndexedValue(leftTuple);
        fastIterator.next(getNext(key, true));
        return fastIterator;
    }

    private TupleImpl getNext(Comparable key, boolean first) {
        return left ? getNextLeft( key, first ) : getNextRight( key, first );
    }

    private TupleImpl getNextLeft(Comparable key, boolean first) {
        key = coerceType(index, tree.root != null ? tree.root.key : null, key);
        Node<Comparable<Comparable>> firstNode;
        switch (constraintType) {
            case LESS_THAN:
                firstNode = tree.findNearestNode(key, false, Boundary.LOWER);
                break;
            case LESS_OR_EQUAL:
                firstNode = tree.findNearestNode(key, first, Boundary.LOWER);
                break;
            case GREATER_THAN:
                firstNode = tree.findNearestNode(key, false, Boundary.UPPER);
                break;
            case GREATER_OR_EQUAL:
                firstNode = tree.findNearestNode(key, first, Boundary.UPPER);
                break;
            default:
                throw new UnsupportedOperationException("Cannot call remove constraint of type: " + constraintType);
        }
        return firstNode == null ? null : firstNode.getFirst();
    }

    private TupleImpl getNextRight(Comparable key, boolean first) {
        if (key == null) {
            return null;
        }
        key = coerceType(index, tree.root != null ? tree.root.key : null, key);
        Node<Comparable<Comparable>> firstNode;
        switch (constraintType) {
            case LESS_THAN:
                firstNode = tree.findNearestNode(key, false, Boundary.UPPER);
                break;
            case LESS_OR_EQUAL:
                firstNode = tree.findNearestNode(key, first, Boundary.UPPER);
                break;
            case GREATER_THAN:
                firstNode = tree.findNearestNode(key, false, Boundary.LOWER);
                break;
            case GREATER_OR_EQUAL:
                firstNode = tree.findNearestNode(key, first, Boundary.LOWER);
                break;
            default:
                throw new UnsupportedOperationException("Cannot call remove constraint of type: " + constraintType);
        }
        return firstNode == null ? null : firstNode.getFirst();
    }

    public static Comparable coerceType(IndexedValueReader index, Comparable treeRootKey, Comparable key) {
        // We don't do dynamic coercion other than Numbers. See IndexUtil.areRangeIndexCompatibleOperands().
        if (index.requiresCoercion() && key != null && treeRootKey != null && !key.getClass().equals(treeRootKey.getClass())) {
            if (treeRootKey instanceof Number && key instanceof Number) {
                key = (Comparable) CoercionUtil.coerceToNumber((Number) key, (Class<?>) treeRootKey.getClass());
            } else {
                throw new RuntimeException("Not possible to coerce [" + key + "] from class " + key.getClass() + " to class " + treeRootKey.getClass());
            }
        }
        return key;
    }

    public class TupleFastIterator implements FastIterator<TupleImpl> {
        public TupleImpl next(TupleImpl tuple) {
            if (tuple == null) {
                Node<Comparable<Comparable>> firstNode = tree.first();
                return firstNode == null ? null : firstNode.getFirst();
            }
            TupleImpl next = tuple.getNext();
            if (next != null) {
                return next;
            }
            Comparable key = getLeftIndexedValue(tuple);
            return getNext(key, false);
        }

        public boolean isFullIterator() {
            return false;
        }
    }

    public void clear() {
        tree = new TupleRBTree<>();
    }

    public IndexType getIndexType() {
        return IndexType.COMPARISON;
    }
}
