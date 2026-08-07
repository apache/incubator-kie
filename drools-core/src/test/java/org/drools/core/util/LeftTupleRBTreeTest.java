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

import java.util.Random;

import org.drools.base.util.Iterator;
import org.drools.core.util.TupleRBTree.Node;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class LeftTupleRBTreeTest {

    @Test
    public void testIterator() {
        final int ITEMS = 10000;
        TupleRBTree<Integer> tree = new TupleRBTree<Integer>();
        Random random = new Random(0);
        for (int i = 0; i < ITEMS; i++) {
            int key = random.nextInt();
            tree.insert( key );
        }

        int i = 0;
        Iterator<Node<Integer>> fastIterator = tree.iterator();
        int lastKey = Integer.MIN_VALUE;
        for (Node<Integer> node = fastIterator.next(); node != null; node = fastIterator.next()) {
            int currentKey = node.key;
            if (currentKey < lastKey) {
                fail(currentKey + " should be greater than " + lastKey);
            }
            lastKey = currentKey;
            i++;
        }

        assertThat(i).isEqualTo(ITEMS);
    }

    @Test
    public void testRange() {
        // TupleRBTree.range() is not actually used by TupleIndexRBTree but fixing it to avoid future trouble
        TupleRBTree<Integer> tree = new TupleRBTree<Integer>();
        tree.insert(10);
        tree.insert(20);
        tree.insert(25);
        tree.insert(15);
        tree.insert(5);
        
        Iterator<Node<Integer>> fastIterator = tree.range(2, true, 15, false);
        Node<Integer> node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(5);
        node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(10);
        node = fastIterator.next();
        assertThat(node).isNull();

        fastIterator = tree.range(2, true, 5, false);
        node = fastIterator.next();
        assertThat(node).isNull();

        fastIterator = tree.range(25, false, 35, true);
        node = fastIterator.next();
        assertThat(node).isNull();

        fastIterator = tree.range(6, false, 9, false);
        node = fastIterator.next();
        assertThat(node).isNull();

        fastIterator = tree.range(5, false, 35, false);
        node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(10);
        node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(15);
        node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(20);
        node = fastIterator.next();
        assertThat((int) node.key).isEqualTo(25);
        node = fastIterator.next();
        assertThat(node).isNull();

    }
}
