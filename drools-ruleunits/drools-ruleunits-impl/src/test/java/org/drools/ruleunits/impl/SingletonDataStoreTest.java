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
package org.drools.ruleunits.impl;

import java.util.ArrayList;
import java.util.List;

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataProcessor;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.SingletonStore;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.FactHandle;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class SingletonDataStoreTest {

    @Test
    public void testCreate() {
        Probe<Integer> probe = new Probe<>();
        SingletonStore<Integer> integers = DataSource.createSingleton();
        integers.subscribe(probe);
        assertThat(probe.handle).isNull();
        assertThat(probe.value).isNull();
    }

    @Test
    public void testAdd() {
        Probe<Integer> probe = new Probe<>();
        SingletonStore<Integer> integers = DataSource.createSingleton();
        integers.subscribe(probe);
        integers.set(1);
        integers.set(2);
        integers.set(3);
        assertThat(probe.handle).isNotNull();
        assertThat(probe.value).isEqualTo(3);
        assertThat(probe.seen).isEqualTo(asList(1, 2, 3));
    }

    @Test
    public void testRemove() {
        Probe<Integer> probe = new Probe<>();
        SingletonStore<Integer> integers = DataSource.createSingleton();
        integers.subscribe(probe);
        integers.set(1);
        integers.set(2);
        integers.set(3);
        integers.clear();
        assertThat(probe.handle).isNull();
        assertThat(probe.value).isNull();
    }

    private static class Probe<T> implements DataProcessor<T> {

        DataHandle handle;
        T value;
        List<T> seen = new ArrayList<>();

        @Override
        public FactHandle insert(DataHandle handle, T object) {
            this.handle = handle;
            this.value = object;
            this.seen.add(object);
            return null;
        }

        @Override
        public void update(DataHandle handle, T object) {
            assertThat(this.handle).isSameAs(handle);
            if (object != this.value) {
                this.value = object;
                this.seen.add(object);
            }
        }

        @Override
        public void delete(DataHandle handle) {
            assertThat(this.handle).isSameAs(handle);

            this.handle = null;
            this.value = null;
        }
    }

    ;
}