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

import java.lang.reflect.Array;
import java.util.Arrays;

public class CircularArrayList<E> {
    private E[] array;
    private int head = 0;
    // windowSize is the logical max-kept capacity (as requested by the caller).
    // arrayCapacity is rounded up to the nearest power of two for fast modulo via mask.
    private final int windowSize;
    private final int arrayCapacity;
    private final int mask;
    private Class cls;

    public CircularArrayList(int capacity) {
        this(Object.class, capacity);
    }

    public CircularArrayList(Class cls, int capacity) {
        // Round up to the nearest power of two (same strategy as HashMap).
        int cap = capacity <= 1 ? 1 : Integer.highestOneBit(capacity - 1) << 1;
        this.windowSize    = capacity;
        this.arrayCapacity = cap;
        this.mask          = cap - 1;
        this.array         = (E[]) new Object[cap];
        this.cls           = cls;
    }

    public boolean set(int index, E e) {
        array[index & mask] = e;
        return true;
    }

    /**
     * Write {@code e} at the given logical {@code index} and advance {@code head}
     * to {@code index + 1} if it has not reached that point yet. This allows
     * writing to an arbitrary slot (e.g. by filterIndex) while keeping the
     * {@code get()} range check valid.
     */
    public void put(int index, E e) {
        array[index & mask] = e;
        if (index >= head) {
            head = index + 1;
        }
    }

    public boolean add(E e) {
            array[head++ & mask] = e;
            return true;
    }

    public E getHead() {
        return array[(head-1) & mask];
    }

    public E getHeadMinus(int i) {
        return array[(head-1-i) & mask];
    }

    public E get(int index) {
        if (index < 0 || index < head - windowSize || index >= head) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + windowSize);
        }

        return array[index & mask];
    }

    public int size() {
        return head;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public E[] toArray() {
        int headIndex = head & mask;
        E[] trg;
        if (head <= windowSize) {
            // Buffer has not yet wrapped around the logical window.
            trg = (E[]) Array.newInstance(cls, head);
            System.arraycopy(array, 0, trg, 0, head);
        } else {
            // Buffer is full: oldest entry starts at headIndex (mod arrayCapacity).
            trg = (E[]) Array.newInstance(cls, windowSize);
            // headIndex is where the *next* write goes, which is also the oldest slot.
            // We need to copy windowSize elements starting from that position.
            int oldestIndex = (head - windowSize) & mask;
            if (oldestIndex + windowSize <= arrayCapacity) {
                System.arraycopy(array, oldestIndex, trg, 0, windowSize);
            } else {
                int firstPart = arrayCapacity - oldestIndex;
                System.arraycopy(array, oldestIndex, trg, 0, firstPart);
                System.arraycopy(array, 0, trg, firstPart, windowSize - firstPart);
            }
        }

        return trg;
    }

    @Override
    public String toString() {
        return "CircularArrayList{" +
               "array=" + Arrays.toString(array) +
               ", head=" + head +
               ", windowSize=" + windowSize +
               '}';
    }
}
