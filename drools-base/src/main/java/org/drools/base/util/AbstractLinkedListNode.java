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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Provides a abstract base implementation that an object can extend so that it can be used in a LinkedList.
 *
 * @see LinkedList
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name="linked-list")
public abstract class AbstractLinkedListNode<T extends DoubleLinkedEntry<T>>
    implements
    DoubleLinkedEntry<T> {

    private static final long serialVersionUID = 510l;

    private T    previous;

    private T    next;

    /**
     * Empty Constructor
     */
    public AbstractLinkedListNode() {
    }

    /* (non-Javadoc)
     * @see org.kie.reteoo.LinkedListNode#remove()
     */
    public T getNext() {
        return this.next;
    }

    /* (non-Javadoc)
     * @see org.kie.reteoo.LinkedListNode#setNext(org.kie.reteoo.LinkedListNode)
     */
    public void setNext(final T next) {
        this.next = next;
    }

    /* (non-Javadoc)
     * @see org.kie.reteoo.LinkedListNode#getPrevious()
     */
    public T getPrevious() {
        return this.previous;
    }

    /* (non-Javadoc)
     * @see org.kie.reteoo.LinkedListNode#setPrevious(org.kie.reteoo.LinkedListNode)
     */
    public void setPrevious(final T previous) {
        this.previous = previous;
    }

    public void clear() {
        previous = null;
        next = null;
    }
}
