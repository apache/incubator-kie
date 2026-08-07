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

import org.drools.base.util.AbstractLinkedListNode;
import org.drools.base.util.DoubleLinkedEntry;
import org.drools.base.util.LinkedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class LinkedListTest {

    LinkedList        list  = null;
    DoubleLinkedEntry node1 = null;
    DoubleLinkedEntry node2 = null;
    DoubleLinkedEntry node3 = null;

    @BeforeEach
    public void setUp() throws Exception {
        this.list = new LinkedList();
        this.node1 = new AbstractBaseLinkedListNodeMock();
        this.node2 = new AbstractBaseLinkedListNodeMock();
        this.node3 = new AbstractBaseLinkedListNodeMock();
    }

    private static class AbstractBaseLinkedListNodeMock
            extends AbstractLinkedListNode<AbstractBaseLinkedListNodeMock> {

    }

    /*
     * Test method for 'org.kie.util.LinkedList.add(LinkedListNode)'
     */
    @Test
    public void testAdd() {
        this.list.add( this.node1 );
        assertThat(this.node1.getPrevious()).as("Node1 previous should be null").isNull();
        assertThat(this.node1.getNext()).as("Node1 next should be null").isNull();
        assertThat(this.node1).as("First node should be node1").isSameAs(this.list.getFirst());
        assertThat(this.node1).as("Last node should be node1").isSameAs(this.list.getLast());

        this.list.add( this.node2 );
        assertThat(this.node2).as("node1 next should be node2").isSameAs(this.node1.getNext());
        assertThat(this.node1).as("node2 previous should be node1").isSameAs(this.node2.getPrevious());
        assertThat(this.node1).as("First node should be node1").isSameAs(this.list.getFirst());
        assertThat(this.node2).as("Last node should be node2").isSameAs(this.list.getLast());

        this.list.add( this.node3 );
        assertThat(this.node3).as("node2 next should be node3").isSameAs(this.node2.getNext());
        assertThat(this.node2).as("node3 previous should be node2").isSameAs(this.node3.getPrevious());
        assertThat(this.list.size()).as("LinkedList should have 3 nodes").isEqualTo(3);
        assertThat(this.node1).as("First node should be node1").isSameAs(this.list.getFirst());
        assertThat(this.node3).as("Last node should be node3").isSameAs(this.list.getLast());
    }

    /*
     * Test method for 'org.kie.util.LinkedList.remove(LinkedListNode)'
     */
    @Test
    public void testRemove() {
        this.list.add( this.node1 );
        this.list.add( this.node2 );
        this.list.add( this.node3 );

        assertThat(this.node1).as("node2 previous should be node1").isSameAs(this.node2.getPrevious());
        assertThat(this.node3).as("node2 next should be node3").isSameAs(this.node2.getNext());

        this.list.remove( this.node2 );
        assertThat(this.node2.getPrevious()).as("Node2 previous should be null").isNull();
        assertThat(this.node2.getNext()).as("Node2 next should be null").isNull();
        assertThat(this.node1.getPrevious()).as("Node1 previous should be null").isNull();
        assertThat(this.node3).as("node1 next should be node3").isSameAs(this.node1.getNext());

        this.list.remove( this.node1 );
        assertThat(this.node1.getPrevious()).as("Node1 previous should be null").isNull();
        assertThat(this.node1.getNext()).as("Node1 next should be null").isNull();
        assertThat(this.node3.getPrevious()).as("Node3 previous should be null").isNull();
        assertThat(this.node3.getNext()).as("Node3 next should be null").isNull();

        this.list.remove( this.node3 );
        assertThat(this.node3.getPrevious()).as("Node3 previous should be null").isNull();
        assertThat(this.node3.getNext()).as("Node3 next should be null").isNull();
    }

    /*
     * Test method for 'org.kie.util.LinkedList.getFirst()'
     */
    @Test
    public void testGetFirst() {
        assertThat(this.list.getFirst()).as("Empty list should return null on getFirst()").isNull();
        
        this.list.add( this.node1 );
        assertThat(this.node1).as("First node should be node1").isSameAs(this.list.getFirst());
        
        this.list.add( this.node2 );
        assertThat(this.node1).as("List should return node1 on getFirst()").isSameAs(this.list.getFirst());

        this.list.add( this.node3 );
        assertThat(this.node1).as("List should return node1 on getFirst()").isSameAs(this.list.getFirst());
    }

    /*
     * Test method for 'org.kie.util.LinkedList.getLast()'
     */
    @Test
    public void testGetLast() {
        assertThat(this.list.getLast()).as("Empty list should return null on getLast()").isNull();
        this.list.add( this.node1 );
        assertThat(this.node1).as("Last node should be node1").isSameAs(this.list.getLast());
        this.list.add( this.node2 );
        assertThat(this.node2).as("Last node should be node2").isSameAs(this.list.getLast());
        this.list.add( this.node3 );
        assertThat(this.node3).as("Last node should be node3").isSameAs(this.list.getLast());
    }

    /*
     * Test method for 'org.kie.util.LinkedList.removeFirst()'
     */
    @Test
    public void testRemoveFirst() {
        this.list.add( this.node1 );
        this.list.add( this.node2 );
        this.list.add( this.node3 );

        assertThat(this.node1).as("First node should be node1").isSameAs(this.list.getFirst());
        this.list.removeFirst();
        assertThat(this.node2).as("List should return node2 on getFirst()").isSameAs(this.list.getFirst());

        this.list.removeFirst();
        assertThat(this.node3).as("List should return node3 on getFirst()").isSameAs(this.list.getFirst());

        this.list.removeFirst();
        assertThat(this.list.getFirst()).as("Empty list should return null on getFirst()").isNull();
    }

    /*
     * Test method for 'org.kie.util.LinkedList.removeLast()'
     */
    @Test
    public void testRemoveLast() {
        this.list.add( this.node1 );
        this.list.add( this.node2 );
        this.list.add( this.node3 );

        assertThat(this.node3).as("Last node should be node3").isSameAs(this.list.getLast());
        
        this.list.removeLast();
        assertThat(this.node2).as("Last node should be node2").isSameAs(this.list.getLast());
        
        this.list.removeLast();
        assertThat(this.node1).as("Last node should be node1").isSameAs(this.list.getLast());

        this.list.removeLast();
        assertThat(this.list.getLast()).as("Empty list should return null on getLast()").isNull();
    }

    /*
     * Test method for 'org.kie.util.LinkedList.isEmpty()'
     */
    @Test
    public void testIsEmpty() {
        assertThat(this.list.isEmpty()).as("Empty list should return true on isEmpty()").isTrue();
        
        this.list.add( this.node1 );
        assertThat(this.list.isEmpty()).as("Not empty list should return false on isEmpty()").isFalse();
    }

    /*
     * Test method for 'org.kie.util.LinkedList.clear()'
     */
    @Test
    public void testClear() {
        this.list.add( this.node1 );
        this.list.add( this.node2 );
        this.list.add( this.node3 );

        assertThat(this.list.size()).as("List size should be 3").isEqualTo(3);
        this.list.clear();
        assertThat(this.list.size()).as("Empty list should have size 0").isEqualTo(0);
    }

    /*
     * Test method for 'org.kie.util.LinkedList.size()'
     */
    @Test
    public void testSize() {
        this.list.add( this.node1 );
        assertThat(this.list.size()).as("LinkedList should have 1 node").isEqualTo(1);

        this.list.add( this.node2 );
        assertThat(this.list.size()).as("LinkedList should have 2 node").isEqualTo(2);

        this.list.add( this.node3 );
        assertThat(this.list.size()).as("LinkedList should have 3 node").isEqualTo(3);
    }

    @Test
    public void testInsertAfter() {
        try {
            this.list.insertAfter( null,
                                   this.node1 );
        } catch (NullPointerException e) {
            e.printStackTrace();
            fail("Should NOT raise NPE!");
        }
    }

}
