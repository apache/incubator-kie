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
package org.drools.mvel.integrationtests.phreak.sequencing;

import org.drools.base.util.CircularArrayList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CircularArrayListTest {

    @Test
    public void testAddingWithWrap() {
        CircularArrayList<Integer> list = new CircularArrayList<>(10);

        for (int i = 0; i < 15; i++) {
            list.add(i);
        }

        assertThat(list.size()).isEqualTo(15);
        assertThat(list.get(list.size()-1)).isEqualTo(14);
        assertThat(list.get(14)).isEqualTo(14);
        assertThat(list.getHead()).isEqualTo(14);
        assertThat(list.getHeadMinus(3)).isEqualTo(11);

        assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> list.get(0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> list.get(4)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(list.get(5)).isEqualTo(5);
    }

    @Test
    public void testNegativeIndexBeforeBufferFills() {
        // When head < windowSize, head - windowSize is negative.
        // A negative index must still be rejected even though it satisfies
        // index >= (head - windowSize) when that difference is negative.
        CircularArrayList<Integer> list = new CircularArrayList<>(10);
        list.add(0);
        list.add(1);
        list.add(2);

        assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> list.get(-7)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    public void testToArray() {
        CircularArrayList<Object> list = new CircularArrayList<>(10);
        assertThat(list.toArray().length).isEqualTo(0);
        list.add(0);
        assertThat(list.toArray()).isEqualTo(new Object[] {0});
        list.add(1);
        assertThat(list.toArray()).isEqualTo(new Object[] {0, 1});
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        list.add(7);
        list.add(8);
        assertThat(list.toArray()).isEqualTo(new Object[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
        list.add(9);
        assertThat(list.toArray()).isEqualTo(new Object[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        list.add(10);
        assertThat(list.toArray()).isEqualTo(new Object[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        list.add(11);
        list.add(12);
        list.add(13);
        list.add(14);
        list.add(15);
        assertThat(list.toArray()).isEqualTo(new Object[] {6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
    }

}
