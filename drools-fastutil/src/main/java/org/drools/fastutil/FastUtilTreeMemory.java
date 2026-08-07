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
package org.drools.fastutil;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleMemory;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.drools.base.util.FastIterator;
import org.drools.base.util.IndexedValueReader;
import org.drools.base.util.Iterator;
import org.drools.core.util.index.AbstractTupleIndexTree;
import org.drools.base.util.index.ConstraintTypeOperator;
import org.drools.core.util.index.TupleIndexRBTree;
import org.drools.core.util.index.TupleList;

public class FastUtilTreeMemory extends AbstractTupleIndexTree implements TupleMemory {

    private Object2ObjectRBTreeMap<Comparable, TupleList> tree;

    private TreeFastIterator fastIterator;

    private TreeFastFullIterator fullFastIterator;

    private TupleList nullTupleList;

    public static class HolderEntry implements Object2ObjectMap.Entry<Comparable, TupleList> {
        static HolderEntry INSTANCE = new HolderEntry();

        public static HolderEntry getInstance() {
            return INSTANCE;
        }

        private Comparable key;

        @Override
        public Comparable getKey() {
            return key;
        }

        public HolderEntry setKey(Comparable key) {
            this.key = key;
            return this;
        }

        @Override
        public TupleList getValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TupleList setValue(TupleList value) {
            throw new UnsupportedOperationException();
        }
    }

    public FastUtilTreeMemory(ConstraintTypeOperator constraintType, IndexedValueReader index, boolean left) {
        this.index = index;
        this.constraintType = constraintType;
        this.left = left;
        this.tree = new Object2ObjectRBTreeMap<>();

        fastIterator = new TreeFastIterator(this);
        fullFastIterator = new TreeFastFullIterator(this);
        nullTupleList = new TupleList();
    }

    @Override
    public TupleImpl getFirst(TupleImpl tuple) {
        return fastIterator.getFirst((TupleImpl) tuple);
    }

    @Override
    public void removeAdd(TupleImpl tuple) {
        IndexTupleList tupleList = (IndexTupleList) tuple.getMemory();
        tupleList.remove( tuple );

        // bucket is empty so remove.
        if ( tupleList.getFirst() == null && tupleList != nullTupleList) {
            tree.remove(tupleList.key());
        }

        TupleList entry = getOrCreate(tuple);
        entry.add(tuple);
    }

    @Override
    public void add(TupleImpl tuple) {
        TupleList entry = getOrCreate(tuple);
        entry.add(tuple);

        this.factSize++;
    }

    private TupleList getOrCreate(TupleImpl tuple) {
        Comparable key = TupleIndexRBTree.coerceType(index, !tree.isEmpty() ? tree.firstKey() : null, getIndexedValue(tuple, left));
        if (key == null) {
            return nullTupleList;
        }
        TupleList entry = tree.compute(key, (newKey, tupleList) -> {
           if (tupleList == null) {
               tupleList = new IndexTupleList(key);
           }

           return tupleList;
        });

        return entry;
    }

    @Override
    public void remove(TupleImpl tuple) {
        IndexTupleList memory = (IndexTupleList) tuple.getMemory();

        memory.remove( tuple );
        this.factSize--;

        if ( memory.getFirst() == null && memory != nullTupleList ) {
            tree.remove(memory.key());
        }
        tuple.clear();
    }

    @Override
    public boolean isIndexed() {
        return true;
    }

    @Override
    public int size() {
        return factSize;
    }

    @Override
    public FastIterator<TupleImpl> fastIterator() {
        return this.fastIterator;
    }

    @Override
    public FastIterator fullFastIterator() {
        this.fullFastIterator.reset();
        return this.fullFastIterator;
    }

    @Override
    public FastIterator fullFastIterator(TupleImpl tuple) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexType getIndexType() {
        return IndexType.COMPARISON;
    }

    @Override
    public void clear() {
        tree.clear();
        this.factSize = 0;
    }

    public static class TreeFastFullIterator implements FastIterator<TupleImpl> {
        FastUtilTreeMemory treeMemory;

        ObjectIterator<Object2ObjectMap.Entry<Comparable, TupleList>> it;

        Tuple current;

        Comparable key;

        public TreeFastFullIterator(FastUtilTreeMemory treeMemory) {
            this.treeMemory = treeMemory;
        }

        public void reset() {
            it =  treeMemory.tree.object2ObjectEntrySet().iterator();
        }

        public void resume(Tuple tuple) {
            it =  treeMemory.tree.object2ObjectEntrySet().iterator();
        }

        @Override
        public TupleImpl next(TupleImpl tuple) {
            TupleImpl next = null;
            if (tuple != null) {
                next = tuple.getNext();
            }

            if (next == null && it.hasNext()) {
                Object2ObjectMap.Entry<Comparable, TupleList> entry = it.next();
                TupleList list = entry.getValue();
                next = list.getFirst();
            }
            current = next;
            return next;
        }

        @Override
        public boolean isFullIterator() {
            return true;
        }
    }
    public static class TreeFastIterator implements FastIterator<TupleImpl> {
        FastUtilTreeMemory treeMemory;

        ObjectBidirectionalIterator<Object2ObjectMap.Entry<Comparable, TupleList>> it;

        Tuple current;

        Comparable key;

        ConstraintTypeOperator constraintType;

        public TreeFastIterator(FastUtilTreeMemory treeMemory) {
            this.treeMemory = treeMemory;
            constraintType = !treeMemory.left ? treeMemory.constraintType : treeMemory.constraintType.negate();
        }

        public TupleImpl getFirst(TupleImpl tuple) {
            key = TupleIndexRBTree.coerceType(treeMemory.index, !treeMemory.tree.isEmpty() ? treeMemory.tree.firstKey() : null, treeMemory.getIndexedValue(tuple, !treeMemory.left));

            if (key == null) {
                switch (constraintType) {
                    case EQUAL:
                    case GREATER_OR_EQUAL:
                    case LESS_OR_EQUAL:
                        return (TupleImpl) treeMemory.nullTupleList.getFirst();
                    default:
                        return null;
                }
            }

            HolderEntry from = HolderEntry.getInstance().setKey(key);

            if (constraintType == ConstraintTypeOperator.LESS_THAN ||
                constraintType == ConstraintTypeOperator.LESS_OR_EQUAL) {
                it =  treeMemory.tree.object2ObjectEntrySet().iterator();
            } else {
                it = treeMemory.tree.object2ObjectEntrySet().iterator(from);
            }

            TupleImpl first = null;

            // adjust for >=, as fastutils is it.next() is always returning the first that is >
            if (it.hasNext()) {
                switch (constraintType) {
                    case LESS_THAN: {
                        Object2ObjectMap.Entry<Comparable, TupleList> tempKey = it.next();
                        if (tempKey.getKey().compareTo(key) < 0) {
                            first = tempKey.getValue().getFirst();
                        } else {
                            key = null;
                            it = null;
                        }
                        break;
                    } case LESS_OR_EQUAL: {
                        Object2ObjectMap.Entry<Comparable, TupleList> tempKey = it.next();
                        if (tempKey.getKey().compareTo(key) <= 0) {
                            first = tempKey.getValue().getFirst();
                        } else {
                            key = null;
                            it = null;
                        }
                        break;
                    } case GREATER_OR_EQUAL: {
                        if (it.hasPrevious()) {
                            Object2ObjectMap.Entry<Comparable, TupleList> prev = it.previous();

                            if (prev.getKey().compareTo(key) >= 0) {
                                Object2ObjectMap.Entry<Comparable, TupleList> entry = prev; // skip the == bucket
                                first = entry.getValue().getFirst();
                            }
                            break; // deliberaly inside the if, as if there is no previous it should continue as normal
                        }
                    } default: {
                        Object2ObjectMap.Entry<Comparable, TupleList> entry = it.next(); // skip the == bucket
                        first = entry.getValue().getFirst();
                        break;
                    }
                }
            }

            current = first;

            return (TupleImpl) first;
        }

        @Override
        public TupleImpl next(TupleImpl tuple) {
            TupleImpl next = tuple.getNext();

            if (next == null && it.hasNext()) {
                Object2ObjectMap.Entry<Comparable, TupleList> entry = it.next();
                TupleList list = entry.getValue();

                // check if we need to finish now, of so return null;
                switch (constraintType) {
                    case LESS_THAN: {
                        if (entry.getKey().compareTo(key) < 0) {
                            next = (TupleImpl) list.getFirst();
                        } else {
                            it = null;
                            key = null;
                        }
                        break;
                    } case LESS_OR_EQUAL: {
                        if (entry.getKey().compareTo(key) <= 0) {
                            next = (TupleImpl) list.getFirst();
                        } else {
                            it = null;
                            key = null;
                        }
                        break;
                    } default:
                        next = (TupleImpl) list.getFirst();
                }
            } else if (next == null){
                it = null;
                key = null;
            }
            current = next;
            return next;
        }

        @Override
        public boolean isFullIterator() {
            return false;
        }
    }
}
