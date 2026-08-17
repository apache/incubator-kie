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
import java.util.Collection;
import java.util.NoSuchElementException;


/**
 * This is a simple linked implementation. Each node must implement </code>LinkedListNode<code> so that it references
 * the node before and after it. This way a node can be removed without having to scan the list to find it. This class
 * does not provide an Iterator implementation as its designed for efficiency and not genericity. There are a number of
 * ways to iterate the list.
 * <p>
 * Simple iterator:
 * <pre>
 * for ( LinkedListNode node = list.getFirst(); node != null; node =  node.remove() ) {
 * }
 * </pre>
 *
 * Iterator that pops the first entry:
 * <pre>
 * for ( LinkedListNode node = list.removeFirst(); node != null; node = list.removeFirst() ) {
 * }
 * </pre>
 */
public class LinkedList<T extends DoubleLinkedEntry<T>>
    implements
    Externalizable {
    private static final long        serialVersionUID = 510l;

    private T                        firstNode;
    private T                        lastNode;
    private int                      size;
    
    public static final FastIterator fastIterator = new LinkedListFastIterator(); // contains no state, so ok to be static

    /**
     * Construct an empty <code>LinkedList</code>
     */
    public LinkedList() {
    }
    
    public LinkedList(final T node) {
        this.firstNode = node;
        this.lastNode = node;
        this.size++;
    }

    public LinkedList(T firstNode, T lastNode, int size) {
        this.firstNode = firstNode;
        this.lastNode  = lastNode;
        this.size      = size;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        firstNode   = (T)in.readObject();
        lastNode    = (T)in.readObject();
        size        = in.readInt();
        
        T current = firstNode;
        T previous = null;

        while ( current != lastNode) {
            T next = (T) in.readObject();
            current.setPrevious(previous);
            current.setNext(next);
            previous = current;
            current = next;
        }     
        
        // current equals last Node, so set previous (this avoids the null writting in stream
        if (current != null) {
            current.setPrevious( previous );
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(firstNode);
        out.writeObject(lastNode);
        out.writeInt(size);
        
        if ( firstNode == lastNode ) {
            // no other nodes
            return;
        }        
        for (T node = firstNode; node != null; node = node.getNext()) {
            out.writeObject(node.getNext());
        }          
    }
    /**
     * Add a <code>LinkedListNode</code> to the list. If the <code>LinkedList</code> is empty then the first and
     * last nodes are set to the added node.
     *
     * @param node
     *      The <code>LinkedListNode</code> to be added
     */
    public void add(final T node) {
        if ( this.firstNode == null ) {
            this.firstNode = node;
            this.lastNode = node;
        } else {
            this.lastNode.setNext( node );
            node.setPrevious( this.lastNode );
            this.lastNode = node;
        }
        this.size++;
    }

    /**
     * Add a <code>LinkedListNode</code> to the end of the list. If the <code>LinkedList</code> is empty then the first and
     * last nodes are set to the added node.
     *
     * @param node
     *      The <code>LinkedListNode</code> to be added
     */
    public void addLast(final T node) {
        if ( this.firstNode == null ) {
            this.firstNode = node;
            this.lastNode = node;
        } else {
            T currentLast = this.lastNode;
            currentLast.setNext( node );
            node.setPrevious( currentLast );
            this.lastNode = node;            
        }
        this.size++;
    }

    public void addFirst(final T node) {
        if ( this.firstNode == null ) {
            this.firstNode = node;
            this.lastNode = node;
        } else {
            T currentFirst = this.firstNode;
            currentFirst.setPrevious( node );
            node.setNext( currentFirst );
            this.firstNode = node;
        }
        this.size++;
    }

    /**
     * Removes a <code>LinkedListNode</code> from the list. This works by attach the previous reference to the child reference.
     * When the node to be removed is the first node it calls <code>removeFirst()</code>. When the node to be removed is the last node
     * it calls <code>removeLast()</code>.
     *
     * @param node
     *      The <code>LinkedListNode</code> to be removed.
     */
    public void remove(final T node) {
        if ( this.firstNode == node ) {
            removeFirst();
        } else if ( this.lastNode == node ) {
            removeLast();
        } else {
            node.getPrevious().setNext( node.getNext() );
            node.getNext().setPrevious( node.getPrevious() );
            this.size--;
            node.setPrevious( null );
            node.setNext( null );
        }
    }

    /**
     * Remove the first node from the list. The next node then becomes the first node. If this is the last
     * node then both first and last node references are set to null.
     *
     * @return
     *      The first <code>LinkedListNode</code>.
     */
    public T removeFirst() {
        if ( this.firstNode == null ) {
            return null;
        }
        final T node = this.firstNode;
        this.firstNode = node.getNext();
        node.setNext( null );
        if ( this.firstNode != null ) {
            this.firstNode.setPrevious( null );
        } else {
            this.lastNode = null;
        }
        this.size--;
        return node;
    }

    /**
     * Remove the last node from the list. The previous node then becomes the last node. If this is the last
     * node then both first and last node references are set to null.
     *
     * @return
     *      The first <code>LinkedListNode</code>.
     */
    public T removeLast() {
        if ( this.lastNode == null ) {
            return null;
        }
        final T node = this.lastNode;
        this.lastNode = node.getPrevious();
        node.setPrevious( null );
        if ( this.lastNode != null ) {
            this.lastNode.setNext( null );
        } else {
            this.firstNode = null;
        }
        this.size--;
        return node;
    }

    public void insertAfter(final T existingNode,
                            final T newNode) {
        if ( newNode.getPrevious() != null || newNode.getNext() != null ) {
            //do nothing if this node is already inserted somewhere
            return;
        }

        if ( existingNode == null ) {
            if ( this.isEmpty() ) {
                this.firstNode = newNode;
                this.lastNode = newNode;
            } else {
                // if existing node is null, then insert it as a first node
                final T node = this.firstNode;
                node.setPrevious( newNode );
                newNode.setNext( node );
                this.firstNode = newNode;
            }
        } else if ( existingNode == this.lastNode ) {
            existingNode.setNext( newNode );
            newNode.setPrevious( existingNode );
            this.lastNode = newNode;
        } else {
            (existingNode.getNext()).setPrevious( newNode );
            newNode.setNext( existingNode.getNext() );
            existingNode.setNext( newNode );
            newNode.setPrevious( existingNode );
        }
        this.size++;
    }

    public void removeAdd(final T node) {
        if (this.lastNode == node) {
            return;
        }

        T previous = node.getPrevious();
        T next = node.getNext();
        if (previous == null) {
            next.setPrevious( null );
            this.firstNode = next;
        } else {
            previous.setNext( next );
            next.setPrevious( previous );
        }

        this.lastNode.setNext(node);
        node.setPrevious( this.lastNode );
        node.setNext( null );
        this.lastNode = node;
    }

    public boolean contains(T node) {
        for (T currentNode = firstNode; currentNode != null; currentNode = currentNode.getNext()) {
            if (currentNode == node) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the first node in the list
     * @return
     *      The first <code>LinkedListNode</code>.
     */
    public final T getFirst() {
        return this.firstNode;
    }

    /**
     * Return the last node in the list
     * @return
     *      The last <code>LinkedListNode</code>.
     */
    public final T getLast() {
        return this.lastNode;
    }
    
    public T get(int i) {
        T current = getFirst();
        for ( int j = 0; j < i; j++ ) {
            current = current.getNext();
        }
        return current;
    }

    public T get(final T node) {
        T current = getFirst();
        while ( current != null ) {
            if ( node.equals( current ) ) {
                return current;
            }
            current = current.getNext();
        }
        return null;
    }

    /**
     * @return
     *      boolean value indicating the empty status of the list
     */
    public final boolean isEmpty() {
        return (this.firstNode == null);
    }

    /**
     * Iterates the list removing all the nodes until there are no more nodes to remove.
     */
    public void clear() {
//        while ( removeFirst() != null ) {
//        }
        this.firstNode = null;
        this.lastNode = null;
        size = 0;
    }

    /**
     * @return
     *     return size of the list as an int
     */
    public final int size() {
        return this.size;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        for ( T node = this.firstNode; node != null; node = node.getNext() ) {
            result = PRIME * result + node.hashCode();
        }
        return result;
    }

    public boolean equals(final Object object) {
        if ( object == this ) {
            return true;
        }

        if ( !(object instanceof LinkedList) ) {
            return false;
        }

        final LinkedList<T> other = (LinkedList<T>) object;

        if ( this.size() != other.size() ) {
            return false;
        }

        for ( T thisNode = this.firstNode, otherNode = other.firstNode; thisNode != null && otherNode != null; thisNode = thisNode.getNext(), otherNode = otherNode.getNext() ) {
            if ( !thisNode.equals( otherNode ) ) {
                return false;
            }
        }
        return true;
    }
    
    public FastIterator<T> fastIterator() {
        return fastIterator;
    }

    // All the tuples except for TMS are AbstractTuple
    public static class LinkedListFastIterator<K extends DoubleLinkedEntry<K>> implements FastIterator<K> {

        public K next(K object) {
            return object.getNext();
        }
        
        public boolean isFullIterator() {
            return false;
        }
    }

    // Special case of iterator that uses K extends Entry<K> to support TMS custom objects
    public static class TMSLinkedListFastIterator<K extends SingleLinkedEntry<K>> implements FastIterator<K> {
        public K next(K object) {
            return object.getNext();
        }

        public boolean isFullIterator() {
            return false;
        }
    }

    public java.util.Iterator<T> javaUtilIterator() {
        return new JavaUtilIterator<>( this );
    }

    /**
     * Returns a list iterator
     * @return
     */
    public static class LinkedListIterator<T extends DoubleLinkedEntry<T>>
        implements
        Iterator<T>,
        Externalizable {
        private LinkedList<T>     list;
        private T current;

        public void reset(final LinkedList<T> list) {
            this.list = list;
            this.current = this.list.firstNode;
        }

        public T next() {
            if ( this.current == null ) {
                return null;
            }
            final T node = this.current;
            this.current = this.current.getNext();
            return node;
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            list    = (LinkedList<T>)in.readObject();
            current = (T)in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(list);
            out.writeObject(current);
        }

    }

    public static class JavaUtilIterator<T extends DoubleLinkedEntry<T>>
        implements
        java.util.Iterator<T>,
        Externalizable {
        
        private LinkedList<T>     list;
        private T currentNode;
        private T nextNode;
        private boolean        immutable;

        public JavaUtilIterator() {

        }

        public JavaUtilIterator(final LinkedList<T> list) {
            this( list, true );
        }

        public JavaUtilIterator(final LinkedList<T> list,
                                final boolean immutable) {
            this.list = list;
            this.nextNode = this.list.getFirst();
            this.immutable = immutable;
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            list    = (LinkedList<T>)in.readObject();
            currentNode = (T)in.readObject();
            nextNode = (T)in.readObject();
            immutable   = in.readBoolean();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(list);
            out.writeObject(currentNode);
            out.writeObject(nextNode);
            out.writeBoolean(immutable);
        }

        public boolean hasNext() {
            return (this.nextNode != null);
        }

        public T next() {
            this.currentNode = this.nextNode;
            if ( this.currentNode != null ) {
                this.nextNode = this.currentNode.getNext();
            } else {
                throw new NoSuchElementException( "No more elements to return" );
            }
            return this.currentNode;
        }

        public void remove() {
            if ( this.immutable ) {
                throw new UnsupportedOperationException( "This  Iterator is immutable, you cannot call remove()" );
            }

            if ( this.currentNode != null ) {
                this.list.remove( this.currentNode );
                this.currentNode = null;
            } else {
                throw new IllegalStateException( "No item to remove. Call next() before calling remove()." );
            }
        }
    }

    protected void copyStateInto(LinkedList<T> other) {
        other.firstNode = firstNode;
        other.lastNode = lastNode;
        other.size = size;
    }

    public <I> void addAllToCollection(Collection<I> c) {
        for (T item = getFirst(); item != null; item = item.getNext()) {
            c.add((I) item);
        }
    }
}
