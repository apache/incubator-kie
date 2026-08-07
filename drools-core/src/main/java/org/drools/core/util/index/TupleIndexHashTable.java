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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.AbstractHashTable;
import org.drools.base.util.FastIterator;
import org.drools.base.util.Iterator;
import org.drools.base.util.LinkedList;

public class TupleIndexHashTable extends AbstractHashTable implements TupleMemory {

    private static final long                         serialVersionUID = 510l;

    private transient FieldIndexHashTableFullIterator tupleValueFullIterator;

    private transient FullFastIterator                fullFastIterator;

    private int                                       factSize;

    private Index                                     index;

    private boolean                                   left;

    public TupleIndexHashTable() {
        // constructor for serialisation
    }

    public TupleIndexHashTable(Index index, boolean left) {
        this( 128, 0.75f, index, left );
    }

    public TupleIndexHashTable( int capacity,
                                float loadFactor,
                                Index index,
                                boolean left ) {
        super( capacity,
               loadFactor );

        this.left = left;

        this.index = index;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        super.readExternal( in );
        factSize = in.readInt();
        index = (Index) in.readObject();
        left = in.readBoolean();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal( out );
        out.writeInt( factSize );
        out.writeObject( index );
        out.writeBoolean( left );
    }

    public void init(TupleList[] table, int size, int factSize) {
        this.table = table;
        this.size = size;
        this.factSize = factSize;
    }

    public Iterator<TupleImpl> iterator() {
        if ( this.tupleValueFullIterator == null ) {
            this.tupleValueFullIterator = new FieldIndexHashTableFullIterator( this );
        } else {
            this.tupleValueFullIterator.reset();
        }
        return this.tupleValueFullIterator;
    }

    public FastIterator<TupleImpl>  fastIterator() {
        return LinkedList.fastIterator;
    }

    public FastIterator<TupleImpl>  fullFastIterator() {
        if ( fullFastIterator == null ) {
            fullFastIterator = new FullFastIterator( this.table );
        } else {
            fullFastIterator.reset(this.table);
        }
        return fullFastIterator;
    }


    public FastIterator<TupleImpl> fullFastIterator(TupleImpl tuple) {
        fullFastIterator.resume(tuple.getMemory(), this.table);
        return fullFastIterator;
    }

    public static class FullFastIterator implements FastIterator<TupleImpl> {
        private TupleList[]     table;
        private int         row;

        public FullFastIterator(TupleList[] table) {
            this.table = table;
            this.row = 0;
        }

        public void resume(TupleList target, TupleList[] table) {
            this.table = table;
            row = indexOf( target.hashCode(),
                           this.table.length );
            row++; // row always points to the row after the current list
        }

        public TupleImpl next(TupleImpl tuple) {
            TupleList list = null;
            if ( tuple != null ) {
                list = tuple.getMemory(); // assumes you do not pass in a null RightTuple
            }

            int length = table.length;

            while ( this.row <= length ) {
                // check if there is a current bucket
                while ( list == null ) {
                    if ( this.row < length ) {
                        // iterate while there is no current bucket, trying each array position
                        list = this.table[this.row];
                        this.row++;
                    } else {
                        // we've scanned the whole table and nothing is left, so return null
                        return null;
                    }

                    if ( list != null ) {
                        // we have a bucket so assign the frist LeftTuple and return
                        tuple = (TupleImpl) list.getFirst();
                        return tuple;
                    }
                }

                tuple = tuple.getNext();
                if ( tuple != null ) {
                    // we have a next tuple so return
                    return tuple;
                } else {
                    list = list.getNext();
                    // try the next bucket if we have a shared array position
                    if ( list != null ) {
                        // if we have another bucket, assign the first LeftTuple and return
                        tuple = (TupleImpl) list.getFirst();
                        return tuple;
                    }
                }
            }
            return null;
        }

        public boolean isFullIterator() {
            return true;
        }

        public void reset(TupleList[] table) {
            this.table = table;
            this.row = 0;
        }

    }

    public TupleImpl getFirst(final TupleImpl tuple) {
        TupleList bucket = get( tuple, !left );
        return bucket != null ? bucket.getFirst() : null;
    }

    public boolean isIndexed() {
        return true;
    }

    public Index getIndex() {
        return this.index;
    }

    @Override
    public int getResizeHashcode(TupleList entry) {
        // TupleList is always LeftTupleList which caches the hashcode, so just return it
        return  entry.hashCode();
    }

    public static class FieldIndexHashTableFullIterator
        implements
        Iterator<TupleImpl> {
        private final AbstractHashTable hashTable;
        private TupleList[]             table;
        private int                     row;
        private int                     length;
        private TupleList               list;
        private TupleImpl        tuple;

        public FieldIndexHashTableFullIterator(final AbstractHashTable hashTable) {
            this.hashTable = hashTable;
            reset();
        }

        public TupleImpl next() {
            while ( this.row <= this.length ) {
                // check if there is a current bucket
                while ( this.list == null ) {
                    if ( this.row < length ) {
                        // iterate while there is no current bucket, trying each array position
                        this.list = (TupleList) this.table[this.row];
                        this.row++;
                    } else {
                        // we've scanned the whole table and nothing is left, so return null
                        return null;
                    }

                    if ( this.list != null ) {
                        // we have a bucket so assign the first LeftTuple and return
                        this.tuple = this.list.getFirst( );
                        return this.tuple;
                    }
                }

                this.tuple = this.tuple.getNext();
                if ( this.tuple != null ) {
                    // we have a next tuple so return
                    return this.tuple;
                } else {
                    this.list = this.list.getNext();
                    // try the next bucket if we have a shared array position
                    if ( this.list != null ) {
                        // if we have another bucket, assign the first LeftTuple and return
                        this.tuple = this.list.getFirst( );
                        return this.tuple;
                    }
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException( "FieldIndexHashTableFullIterator does not support remove()." );
        }

        /* (non-Javadoc)
         * @see org.kie.util.Iterator#reset()
         */
        public void reset() {
            this.table = this.hashTable.getTable();
            this.length = this.table.length;
            this.row = 0;
            this.list = null;
            this.tuple = null;
        }
    }

    public void removeAdd(TupleImpl tuple) {
        HashEntry hashEntry;
        try {
            hashEntry = this.index.hashCodeOf( tuple, left );
        } catch (UnsupportedOperationException e) {
            return;
        }

        TupleList memory = tuple.getMemory();
        memory.remove( tuple );

        if ( hashEntry.hashCode() == memory.hashCode() ) {
            // it's the same bucket, so re-use and return
            memory.add( tuple );
            return;
        }

        // bucket is empty so remove.
        this.factSize--;
        if ( memory.getFirst() == null ) {
            final int index = indexOf( memory.hashCode(),
                                       this.table.length );
            TupleList previous = null;
            TupleList current = this.table[index];
            while ( current != memory ) {
                previous = current;
                current = current.getNext();
            }

            if ( previous != null ) {
                previous.setNext( current.getNext() );
            } else {
                this.table[index] = current.getNext();
            }
            this.size--;
        }

        add( tuple );
    }

    public void add(final TupleImpl tuple) {
        TupleList entry = getOrCreate( tuple );
        if (entry != null) {
            entry.add(tuple);
            this.factSize++;
        }
    }

    public void remove(final TupleImpl tuple) {
        TupleList memory = tuple.getMemory();
        memory.remove( tuple );
        this.factSize--;
        if ( memory.getFirst() == null ) {
            final int index = indexOf( memory.hashCode(),
                                       this.table.length );
            TupleList previous = null;
            TupleList current = this.table[index];
            while ( current != memory ) {
                previous = current;
                current = current.getNext();
            }

            if ( previous != null ) {
                previous.setNext( current.getNext() );
            } else {
                this.table[index] = current.getNext();
            }
            this.size--;
        }
        tuple.clear();
    }

    /**
     * We use this method to aviod to table lookups for the same hashcode; which is what we would have to do if we did
     * a get and then a create if the value is null.
     */
    private TupleList getOrCreate(final TupleImpl tuple) {
        HashEntry hashEntry;
        try {
            hashEntry = this.index.hashCodeOf(tuple, left);
        } catch (UnsupportedOperationException e) {
            return null;
        }

        int index = indexOf( hashEntry.hashCode(), this.table.length );
        TupleList entry = this.table[index];

        // search to find an existing entry
        while ( entry != null ) {
            if ( matches( entry, hashEntry ) ) {
                return entry;
            }
            entry = entry.getNext();
        }

        // entry does not exist, so create
        entry = new IndexTupleList( this.index, hashEntry.clone() );
        entry.setNext( this.table[index] );
        this.table[index] = entry;

        if ( this.size++ >= this.threshold ) {
            resize( 2 * this.table.length );
        }
        return entry;
    }

    private TupleList get(final TupleImpl tuple, boolean isLeftTuple) {
        HashEntry hashEntry;
        try {
            hashEntry = this.index.hashCodeOf(tuple, isLeftTuple);
        } catch (UnsupportedOperationException e) {
            return null;
        }

        int index = indexOf( hashEntry.hashCode(), this.table.length );
        TupleList entry = this.table[index];

        while ( entry != null ) {
            if ( matches( entry, hashEntry ) ) {
                return entry;
            }
            entry = entry.getNext();
        }

        return null;
    }

    private boolean matches( TupleList list, HashEntry hashEntry ) {
        return list.hashCode() == hashEntry.hashCode() && hashEntry.equals( (( IndexTupleList ) list).getHashEntry() );
    }

    public int size() {
        return this.factSize;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<TupleImpl> it = iterator();
        for ( TupleImpl leftTuple = it.next(); leftTuple != null; leftTuple = it.next() ) {
            builder.append(leftTuple).append("\n");
        }

        return builder.toString();
    }

    public void clear() {
        super.clear();
        this.factSize = 0;
        this.fullFastIterator = null;
        this.tupleValueFullIterator = null;
    }

    public IndexType getIndexType() {
        return IndexType.EQUAL;
    }
}
