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

import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.AbstractHashTable.HashEntry;
import org.drools.core.util.AbstractHashTable.Index;
import org.drools.base.util.FastIterator;
import org.drools.base.util.Iterator;
import org.drools.base.util.LinkedList;
import org.drools.core.util.index.TupleList;

public class FastUtilHashTupleMemory implements TupleMemory {

    private int                                factSize;

    private FastUtilMergableHashSet<HashEntry> set;

    private transient FullFastIterator         fullFastIterator;

    private boolean                            left;

    private Index                              index;

    public static class HashStrategy implements Strategy<HashEntry> {
        public static HashStrategy INSTANCE = new HashStrategy();

        public static HashStrategy get() {
            return INSTANCE;
        }

        @Override
        public int hashCode(HashEntry a) {
            return a.hashCode();
        }

        @Override
        public boolean equals(HashEntry a, HashEntry b) {
            return a.equals(b != null ? ((IndexTupleList)b).hashEntry : null);
        }
    }

    public FastUtilHashTupleMemory(Index index,
                                   boolean left) {
        this.set = new FastUtilMergableHashSet<>(HashStrategy.get());

        this.left = left;

        fullFastIterator = new FullFastIterator(set);

        this.index = index;
    }

    @Override
    public TupleImpl getFirst(TupleImpl tuple) {
        TupleList bucket = get( tuple, !left );
        return bucket != null ? bucket.getFirst() : null;
    }

    private TupleList get(final TupleImpl tuple, boolean left) {
        HashEntry hashEntry = this.index.hashCodeOf(tuple, left);

        TupleList tupleList = (TupleList) set.get(hashEntry);

        return tupleList;
    }

    @Override
    public void removeAdd(TupleImpl tuple) {
        IndexTupleList tupleList = (IndexTupleList) tuple.getMemory();
        tupleList.remove( tuple );

        HashEntry hashEntry = this.index.hashCodeOf( tuple, left );
        if ( hashEntry.hashCode() == tupleList.hashCode() ) {
            // it's the same bucket, so re-use and return
            tupleList.add( tuple );
            return;
        }

        // bucket is empty so remove.
        if ( tupleList.getFirst() == null ) {
            set.remove(tupleList.hashEntry);
        }

        tupleList = getOrCreate(hashEntry);
        tupleList.add(tuple);
    }

    @Override
    public void add(TupleImpl tuple) {
        HashEntry hashEntry = this.index.hashCodeOf( tuple, left );

        final TupleList entry = getOrCreate(hashEntry);

        entry.add( tuple );
        this.factSize++;
    }

    private IndexTupleList getOrCreate(HashEntry hashEntry) {
        final IndexTupleList returned = (IndexTupleList) set.compute(hashEntry, (key, value) -> {
            if (value == null) {
                value = new IndexTupleList(key.clone());
            }
            return value;
        });
        return returned;
    }

    @Override
    public void remove(TupleImpl tuple) {
        IndexTupleList memory = (IndexTupleList) tuple.getMemory();

        memory.remove( tuple );
        this.factSize--;

        if ( memory.getFirst() == null ) {
            set.remove(memory.hashEntry());
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

//    @Override
//    public Iterator<TupleImpl> iterator() {
//        return new FullIterator( new FullFastIterator(set));
//    }

    @Override
    public FastIterator<TupleImpl> fastIterator() {
        return LinkedList.fastIterator;
    }

    @Override
    public FastIterator<TupleImpl> fullFastIterator() {
        fullFastIterator.reset();
        return fullFastIterator;
    }

    @Override
    public FastIterator<TupleImpl> fullFastIterator(TupleImpl tuple) {
        fullFastIterator.resume(tuple);
        return fullFastIterator;
    }

    @Override
    public Index getIndex() {
        return this.index;
    }

    @Override
    public IndexType getIndexType() {
        return IndexType.EQUAL;
    }

    @Override
    public void clear() {
        set.clear();
        this.factSize = 0;
    }

    public static class IndexTupleList extends TupleList implements HashEntry {
        private HashEntry hashEntry;

        public IndexTupleList(HashEntry hashEntry) {
            this.hashEntry = hashEntry;
        }

        public HashEntry hashEntry() {
            return hashEntry;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IndexTupleList that = (IndexTupleList) o;
            return hashEntry.equals(that.hashEntry);
        }

        @Override
        public int hashCode() {
            return hashEntry.hashCode();
        }

        @Override
        public HashEntry clone() {
            throw new UnsupportedOperationException();
        }
    }


    public static class FullIterator implements Iterator<TupleImpl> {
        private FullFastIterator fullFastIterator;
        private Tuple tuple;

        public FullIterator(FullFastIterator fullFastIterator) {
            this.fullFastIterator = fullFastIterator;
        }

        @Override
        public TupleImpl next() {
            this.tuple = fullFastIterator.next((TupleImpl) tuple);
            return (TupleImpl) this.tuple;
        }
    }

    public static class FullFastIterator implements FastIterator<TupleImpl> {

        private FastUtilMergableHashSet<HashEntry> set;

        private ObjectIterator<HashEntry>                     it;

        public FullFastIterator(FastUtilMergableHashSet<HashEntry> set) {
            this.set = set;
        }

        /**
         * This only seems to be used in tests, so is not performance sensitive
         * @param target
         */
        public void resume(TupleImpl target) {
            reset();

            TupleList targetMemory = target.getMemory(); // not ideal as this is a linear search to find the resume point
            while (it.hasNext()) {
                TupleList c = (TupleList) it.next();
                if (c == targetMemory) {
                    return;
                }
            }
        }

        public TupleImpl next(TupleImpl tuple) {
            TupleImpl next = null;
            if (tuple != null) {
                next = tuple.getNext();
                if (next != null) {
                    return next;
                }
            }

            if (it.hasNext()) {
                next = (TupleImpl) ((TupleList)it.next()).getFirst();
            }
            return next;
        }

        public boolean isFullIterator() {
            return true;
        }

        public void reset() {
            it = set.iterator();
        }
    }
}
