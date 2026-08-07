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
package org.drools.core.common;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.util.LinkedList;

/**
 * Upon instantiation the EqualityKey caches the first Object's hashCode
 * this can never change. The EqualityKey has an internal datastructure
 * which references all the handles which are equal. It also records
 * Whether the referenced facts are JUSTIFIED or STATED
 */
public abstract class EqualityKey extends LinkedList<DefaultFactHandle>
        implements Externalizable {
    public final static int    STATED    = 1;
    public final static int    JUSTIFIED = 2;

    /** This is cached in the constructor from the first added Object */
    private int          hashCode;

    /** Tracks whether this Fact is Stated or Justified */
    private int          status;
    
    public EqualityKey() {

    }

    public EqualityKey(final InternalFactHandle handle) {
        super( ( DefaultFactHandle ) handle );
        this.hashCode = handle.getObjectHashCode();
    }

    public EqualityKey(final InternalFactHandle handle,
                       final int status) {
        super( ( DefaultFactHandle ) handle );
        this.hashCode = handle.getObjectHashCode();
        this.status = status;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        hashCode    = in.readInt();
        status      = in.readInt();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeInt(hashCode);
        out.writeInt(status);
    }

    public abstract InternalFactHandle getLogicalFactHandle();

    public abstract void setLogicalFactHandle(InternalFactHandle logicalFactHandle);

    public InternalFactHandle getFactHandle() {
        return getFirst();
    }

    public void addFactHandle(final InternalFactHandle handle) {
        add( ( DefaultFactHandle ) handle );
    }

    public void removeFactHandle(final InternalFactHandle handle) {
        remove( ( DefaultFactHandle ) handle );
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return this.status;
    }  

    /**
     * @param status the status to set
     */
    public void setStatus(final int status) {
        this.status = status;
    }

    public String toString() {
        String str = null;
        switch ( this.status ) {
            case 1 :
                str = "STATED";
                break;
            case 2 :
                str = "JUSTIFIED";
                break;
        }
        return "[FactStatus status=" + str + "]";
    }

    /**
     * Returns the cached hashCode
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return this.hashCode;
    }

    /**
     * Equality for the EqualityKey means two things. It returns
     * true if the object is also an EqualityKey the of the same
     * the same identity as this. It also returns true if the object
     * is equal to the head FactHandle's referenced Object.
     */
    public boolean equals(final Object object) {
        if ( object == null ) {
            return false;
        }

        if ( object instanceof EqualityKey ) {
            return this == object;
        }

        return this.getFirst().getObject().equals( object );
    }

}
