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

public interface FastIterator<T> {
    public class NullFastIterator<T> implements FastIterator<T> {
        public static final NullFastIterator INSTANCE = new NullFastIterator();

        @Override public T next(T object) {
            return null;
        }

        @Override public boolean isFullIterator() {
            return true;
        }
    }

    T next(T object);
    
    boolean isFullIterator();

    public class IteratorAdapter<T> implements Iterator<T> {
        private final FastIterator<T> fastIterator;
        private T current = null;
        private boolean firstConsumed = false;

        public IteratorAdapter(FastIterator<T> fastIterator, T first) {
            this.fastIterator = fastIterator;
            current = first;
        }

        public T next() {
            if (!firstConsumed) {
                firstConsumed = true;
                return current;
            }
            current = fastIterator.next(current);
            return current;
        }
    }
}
