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

import org.drools.base.util.Iterator;
import org.drools.base.util.SingleLinkedEntry;

/**
 * Fast re-usable iterator
 */
public class HashTableIterator
    implements
    Iterator,
    Externalizable {

    private static final long serialVersionUID = 510l;

    private AbstractHashTable   hashTable;
    private SingleLinkedEntry[] table;
    public  int                 row;
    private int                 length;
    private SingleLinkedEntry   entry;

    public HashTableIterator() {
    }

    public HashTableIterator(final AbstractHashTable hashTable) {
        this.hashTable = hashTable;
        reset();
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        hashTable = (AbstractHashTable) in.readObject();
        table = (SingleLinkedEntry[]) in.readObject();
        row = in.readInt();
        length = in.readInt();
        entry = (SingleLinkedEntry) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( hashTable );
        out.writeObject( table );
        out.writeInt( row );
        out.writeInt( length );
        out.writeObject( entry );
    }

    /* (non-Javadoc)
     * @see org.kie.util.Iterator#next()
     */
    public Object next() {
        if ( this.entry != null ) {
            this.entry = this.entry.getNext();
        }

        // if no entry keep skipping rows until we come to the end, or find one that is populated
        while ( this.entry == null && this.row < this.length ){
            this.entry = this.table[this.row];
            this.row++;
        }

        return this.entry;
    }


    /* (non-Javadoc)
     * @see org.kie.util.Iterator#reset()
     */
    public void reset() {
        this.table = this.hashTable.getTable();
        this.length = this.table.length;
        this.entry = null;
        this.row = 0;
    }
}
