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
package org.drools.core.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import org.drools.base.util.IndexedValueReader;
import org.drools.base.util.Iterator;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.util.index.TupleList;

public abstract class AbstractHashTable
    implements
    Externalizable {
    static final int           MAX_CAPACITY = 1 << 30;

    public static final int    PRIME            = 31;

    protected int              size;
    protected int              threshold;
    protected float            loadFactor;

    protected TupleList[]      table;

    private HashTableIterator  iterator;

    public AbstractHashTable() {
        this( 16,
              0.75f );
    }

    public AbstractHashTable(final int capacity,
                             final float loadFactor) {
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);
        this.table = new TupleList[capacity];
    }

    public AbstractHashTable(final TupleList[] table) {
        this( 0.75f,
              table );
    }

    public AbstractHashTable(final float loadFactor,
                             final TupleList[] table) {
        this.loadFactor = loadFactor;
        this.threshold = (int) (table.length * loadFactor);
        this.table = table;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        size = in.readInt();
        threshold = in.readInt();
        loadFactor = in.readFloat();
        table = (TupleList[]) in.readObject();
        iterator = (HashTableIterator) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt( size );
        out.writeInt( threshold );
        out.writeFloat( loadFactor );
        out.writeObject( table );
        out.writeObject( iterator );
    }

    public Iterator iterator() {
        if ( this.iterator == null ) {
            this.iterator = new HashTableIterator( this );
        } else {
            this.iterator.reset();
        }
        return this.iterator;
    }

    public Iterator newIterator() {
        return new HashTableIterator( this );
    }

    public void ensureCapacity(int itemsToBeAdded) {
        int newCapacity = this.size + itemsToBeAdded;
        if (newCapacity > this.threshold) {
            int newSize = this.table.length * 2;
            while (newSize < newCapacity) {
                newSize *= 2;
            }
            resize(newSize);
        }
    }

    protected void resize(final int newCapacity) {
        final TupleList[] oldTable = this.table;
        final int oldCapacity = oldTable.length;
        if ( oldCapacity == AbstractHashTable.MAX_CAPACITY ) {
            this.threshold = Integer.MAX_VALUE;
            return;
        }

        final TupleList[] newTable = new TupleList[newCapacity];

        for ( int i = 0; i < this.table.length; i++ ) {
            TupleList entry = this.table[i];
            if ( entry == null ) {
                continue;
            }
            this.table[i] = null;
            while ( entry != null ) {
                TupleList next = entry.getNext();
                                
                // we must use getResizeHashcode as some sub classes cache the hashcode and some don't
                // otherwise we end up rehashing a cached hashcode that has already been rehashed.
                final int index = indexOf(  getResizeHashcode( entry ),
                                            newTable.length );
                
                entry.setNext( newTable[index] );
                newTable[index] = entry;

                entry = next;
            }
        }

        this.table = newTable;
        this.threshold = (int) (newCapacity * this.loadFactor);
    }
    
    public abstract int getResizeHashcode(TupleList entry);

    public <T extends TupleImpl> TupleList[] getTable() {
        return this.table;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }
    
    private static int rehash(int hash) {
        hash ^= (hash >>> 20) ^ (hash >>> 12);
        return hash ^ (hash >>> 7) ^ (hash >>> 4);
    }     

    protected static int indexOf(final int hashCode,
                          final int dataSize) {
        return hashCode & (dataSize - 1);
    }
    
    @Override
    public String toString() {
        StringBuilder sbuilder = new StringBuilder();
        Iterator it = newIterator();
        boolean isFirst = true;
        for (TupleList entry = ( TupleList ) it.next(); entry != null; entry = ( TupleList ) it.next() ) {
            sbuilder.append( entry );
            if ( !isFirst ) {
                sbuilder.append( ", " );
            }
            isFirst = false;
        }
        
        return sbuilder.toString();
    }

    public interface Index extends Externalizable {
        IndexedValueReader getFieldIndex(int index);
        HashEntry hashCodeOf(TupleImpl tuple, boolean left);
    }

    public static class SingleIndex implements Index {

        private static final long    serialVersionUID = 510l;

        private IndexedValueReader index;

        private int startResult;

        private final SingleHashEntry hashEntry = new SingleHashEntry();

        public SingleIndex() {

        }

        public SingleIndex(final IndexedValueReader[] indexes,
                           final int startResult) {
            this.startResult = startResult;
            this.index = indexes[0];
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            index = (IndexedValueReader) in.readObject();
            startResult = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( index );
            out.writeInt( startResult );
        }

        @Override
        public IndexedValueReader getFieldIndex(int index) {
            if ( index > 0 ) {
                throw new IllegalArgumentException( "IndexUtil position " + index + " does not exist" );
            }
            return this.index;
        }

        @Override
        public HashEntry hashCodeOf(TupleImpl tuple, boolean left) {
            return hashEntry.set(startResult, index.indexedValueOf( tuple, left ) );
        }
    }

    public static class IndexTupleList extends TupleList implements HashEntry {
        private HashEntry hashEntry;
        private Index index;
        private int hashCode;

        public IndexTupleList( Index index, HashEntry hashEntry ) {
            this.index = index;
            this.hashEntry = hashEntry;
            this.hashCode = hashEntry.hashCode();
        }

        @Override
        public boolean equals(final Object object) {
            if (!(object instanceof IndexTupleList)) {
                return false;
            }
            final IndexTupleList other = (IndexTupleList) object;
            return this.hashCode == other.hashCode && this.index == other.index;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        protected void copyStateInto(TupleList other) {
            super.copyStateInto(other);
            ( (IndexTupleList) other ).hashEntry = hashEntry;
            ( (IndexTupleList) other ).index = index;
            ( (IndexTupleList) other ).hashCode = hashCode;
        }

        public HashEntry getHashEntry() {
            return hashEntry;
        }

        @Override
        public HashEntry clone() {
            throw new UnsupportedOperationException();
        }
    }

    public static class DoubleCompositeIndex implements Index {

        private static final long serialVersionUID = 510l;

        private IndexedValueReader index1;
        private IndexedValueReader index2;

        private int startResult;

        private final DoubleHashEntry hashEntry = new DoubleHashEntry();

        public DoubleCompositeIndex() {

        }

        public DoubleCompositeIndex(final IndexedValueReader[] indexes,
                                    final int startResult) {
            this.startResult = startResult;

            this.index1 = indexes[0];
            this.index2 = indexes[1];
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            index1 = (IndexedValueReader) in.readObject();
            index2 = (IndexedValueReader) in.readObject();
            startResult = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( index1 );
            out.writeObject( index2 );
            out.writeInt( startResult );
        }

        @Override
        public IndexedValueReader getFieldIndex(int index) {
            switch ( index ) {
                case 0 :
                    return index1;
                case 1 :
                    return index2;
                default :
                    throw new IllegalArgumentException( "IndexUtil position " + index + " does not exist" );
            }
        }

        @Override
        public HashEntry hashCodeOf(TupleImpl tuple, boolean left) {
            return hashEntry.set(startResult, index1.indexedValueOf( tuple, left ), index2.indexedValueOf( tuple, left ) );
        }
    }

    public static class TripleCompositeIndex implements Index {

        private static final long serialVersionUID = 510l;

        private IndexedValueReader index1;
        private IndexedValueReader index2;
        private IndexedValueReader index3;

        private int startResult;

        private final TripleHashEntry hashEntry = new TripleHashEntry();

        public TripleCompositeIndex() {

        }

        public TripleCompositeIndex(final IndexedValueReader[] indexes,
                                    final int startResult) {
            this.startResult = startResult;

            this.index1 = indexes[0];
            this.index2 = indexes[1];
            this.index3 = indexes[2];
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            index1 = (IndexedValueReader) in.readObject();
            index2 = (IndexedValueReader) in.readObject();
            index3 = (IndexedValueReader) in.readObject();
            startResult = in.readInt();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( index1 );
            out.writeObject( index2 );
            out.writeObject( index3 );
            out.writeInt( startResult );
        }

        @Override
        public IndexedValueReader getFieldIndex(int index) {
            switch ( index ) {
                case 0 :
                    return index1;
                case 1 :
                    return index2;
                case 2 :
                    return index3;
                default :
                    throw new IllegalArgumentException( "IndexUtil position " + index + " does not exist" );
            }
        }

        @Override
        public HashEntry hashCodeOf(TupleImpl tuple, boolean left) {
            return hashEntry.set(startResult, index1.indexedValueOf( tuple, left ), index2.indexedValueOf( tuple, left ), index3.indexedValueOf( tuple, left ) );
        }
    }

    public void clear() {
        this.table = new TupleList[Math.min( this.table.length,
                                         16 )];
        this.threshold = (int) (this.table.length * this.loadFactor);
        this.size = 0;
        this.iterator = null;
    }

    public interface HashEntry {
        HashEntry clone();
    }

    public static class SingleHashEntry implements HashEntry {

        private int hashCode;
        private Object obj1;

        public SingleHashEntry() {
        }

        public SingleHashEntry(int hashSeed, Object obj1) {
            set(hashSeed, obj1);
        }

        public HashEntry set(int hashSeed, Object obj1) {
            this.obj1 = obj1;
            this.hashCode = rehash( PRIME * hashSeed + Objects.hashCode( obj1 ) );
            return this;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) {
                return true;
            }
            if ( o == null || getClass() != o.getClass() ) {
                return false;
            }
            SingleHashEntry that = ( SingleHashEntry ) o;
            return hashCode == that.hashCode && Objects.equals( obj1, that.obj1 );
        }

        public HashEntry clone() {
            SingleHashEntry singleEntry = new SingleHashEntry();
            singleEntry.hashCode = hashCode;
            singleEntry.obj1 = obj1;
            return singleEntry;
        }

        @Override
        public String toString() {
            return "SingleHashEntry{" +
                   "hashCode=" + hashCode +
                   ", obj1=" + obj1 +
                   '}';
        }
    }

    public static class DoubleHashEntry implements HashEntry {

        private int hashCode;
        private Object obj1;
        private Object obj2;

        public DoubleHashEntry() {

        }

        public HashEntry set(int hashSeed, Object obj1, Object obj2) {
            this.obj1 = obj1;
            this.obj2 = obj2;
            this.hashCode = hashCodeOf(hashSeed, obj1, obj2);
            return this;
        }

        private int hashCodeOf(int hashSeed, Object obj1, Object obj2) {
            int hashCode = hashSeed;
            hashCode = PRIME * hashCode + Objects.hashCode( obj1 );
            hashCode = PRIME * hashCode + Objects.hashCode( obj2 );
            return rehash( hashCode );
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) {
                return true;
            }
            if ( o == null || getClass() != o.getClass() ) {
                return false;
            }
            DoubleHashEntry that = ( DoubleHashEntry ) o;
            return hashCode == that.hashCode && Objects.equals( obj1, that.obj1 ) && Objects.equals( obj2, that.obj2 );
        }

        public HashEntry clone() {
            DoubleHashEntry doubleEntry = new DoubleHashEntry();
            doubleEntry.hashCode = hashCode;
            doubleEntry.obj1 = obj1;
            doubleEntry.obj2 = obj2;
            return doubleEntry;
        }

        @Override
        public String toString() {
            return "DoubleHashEntry{" +
                   "hashCode=" + hashCode +
                   ", obj1=" + obj1 +
                   ", obj2=" + obj2 +
                   '}';
        }
    }

    public static class TripleHashEntry implements HashEntry {

        private int hashCode;
        private Object obj1;
        private Object obj2;
        private Object obj3;

        public TripleHashEntry() {

        }

        public HashEntry set(int hashSeed, Object obj1, Object obj2, Object obj3) {
            this.obj1 = obj1;
            this.obj2 = obj2;
            this.obj3 = obj3;
            this.hashCode = hashCodeOf(hashSeed, obj1, obj2, obj3);
            return this;
        }

        private int hashCodeOf(int hashSeed, Object obj1, Object obj2, Object obj3) {
            int hashCode = hashSeed;
            hashCode = PRIME * hashCode + Objects.hashCode( obj1 );
            hashCode = PRIME * hashCode + Objects.hashCode( obj2 );
            hashCode = PRIME * hashCode + Objects.hashCode( obj3 );
            return rehash( hashCode );
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) {
                return true;
            }
            if ( o == null || getClass() != o.getClass() ) {
                return false;
            }
            TripleHashEntry that = ( TripleHashEntry ) o;
            return hashCode == that.hashCode && Objects.equals( obj1, that.obj1 ) && Objects.equals( obj2, that.obj2 ) && Objects.equals( obj3, that.obj3 );
        }

        public HashEntry clone() {
            TripleHashEntry tripleEntry = new TripleHashEntry();
            tripleEntry.hashCode = hashCode;
            tripleEntry.obj1 = obj1;
            tripleEntry.obj2 = obj2;
            tripleEntry.obj3 = obj3;
            return tripleEntry;
        }

        @Override
        public String toString() {
            return "TripleHashEntry{" +
                   "hashCode=" + hashCode +
                   ", obj1=" + obj1 +
                   ", obj2=" + obj2 +
                   ", obj3=" + obj3 +
                   '}';
        }
    }
}
