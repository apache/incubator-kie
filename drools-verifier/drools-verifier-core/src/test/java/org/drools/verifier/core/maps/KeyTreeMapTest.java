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
package org.drools.verifier.core.maps;

import java.util.List;

import org.drools.verifier.core.AnalyzerConfigurationMock;
import org.drools.verifier.core.configuration.AnalyzerConfiguration;
import org.drools.verifier.core.index.keys.Key;
import org.drools.verifier.core.index.keys.UUIDKey;
import org.drools.verifier.core.index.keys.UpdatableKey;
import org.drools.verifier.core.index.keys.Value;
import org.drools.verifier.core.maps.util.HasKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeyTreeMapTest {

    private final KeyDefinition AGE = KeyDefinition.newKeyDefinition()
            .updatable()
            .withId("age")
            .build();
    private final KeyDefinition NAME = KeyDefinition.newKeyDefinition()
            .withId("name")
            .build();

    private KeyTreeMap<Person> map;
    private Person toni;
    private Person eder;
    private Person michael;

    private AnalyzerConfiguration configuration;

    @BeforeEach
    public void setUp() throws Exception {
        configuration = new AnalyzerConfigurationMock();

        map = new KeyTreeMap<>(AGE, NAME);

        toni = new Person("Toni", 20);
        eder = new Person("Eder", 20);
        michael = new Person("Michael", 30);

        put(toni);
        put(eder);
        put(michael);
    }

    @Test
    void testFindByUUID() throws Exception {
    	assertThat(map.get(UUIDKey.UNIQUE_UUID).keySet()).containsExactlyInAnyOrder(toni.uuidKey.getSingleValue(),
    			eder.uuidKey.getSingleValue(),
                michael.uuidKey.getSingleValue());
    }

    @Test
    void testReAdd() throws Exception {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            put(toni));
    }

    @Test
    void testFindByName() throws Exception {
        MultiMap<Value, Person, List<Person>> multiMap = map.get(KeyDefinition.newKeyDefinition()
                        .withId("name")
                        .build());
        
        assertThat(multiMap.keySet()).extracting(x -> x.getComparable()).containsExactlyInAnyOrder("Toni", "Eder", "Michael");
    }

    @Test
    void testFindByAge() throws Exception {
        final MultiMap<Value, Person, List<Person>> age = map.get(KeyDefinition.newKeyDefinition()
                .withId("age")
                .build());
        
        assertThat(age.keySet()).extracting(x -> x.getComparable()).containsExactly(20, 30);
		
        assertThat(age.get(new Value(20))).contains(toni, eder);
    }

    @Test
    void testUpdateAge() throws Exception {
        final MultiMapChangeHandler changeHandler = mock(MultiMapChangeHandler.class);
        map.get(AGE).addChangeListener(changeHandler);

        toni.setAge(10);

        final MultiMap<Value, Person, List<Person>> age = map.get(AGE);

        assertThat(age.get(new Value(20))).doesNotContain(toni);
        assertThat(age.get(new Value(10))).contains(toni);
    }

    @Test
    void testRetract() throws Exception {
        toni.uuidKey.retract();

        assertThat(map.get(UUIDKey.UNIQUE_UUID).keySet()).containsExactlyInAnyOrder(eder.uuidKey.getSingleValue(), michael.uuidKey.getSingleValue());
        
        MultiMap<Value, Person, List<Person>> nameMap = map.get(KeyDefinition.newKeyDefinition()
                        .withId("name")
                        .build());
        
        assertThat(nameMap.keySet()).extracting(x -> x.getComparable()).containsExactly("Eder", "Michael");
        
        MultiMap<Value, Person, List<Person>> ageMap = map.get(KeyDefinition.newKeyDefinition()
                        .withId("age")
                        .build());
        
        assertThat(ageMap.keySet()).extracting(x -> x.getComparable()).containsExactly(20, 30);
        
    }

    @Test
    void testRemoveWhenItemDoesNotExist() throws Exception {
        final UUIDKey uuidKey = mock(UUIDKey.class);
        when(uuidKey.getKeyDefinition()).thenReturn(UUIDKey.UNIQUE_UUID);
        when(uuidKey.getSingleValue()).thenReturn(new Value("DoesNotExist"));
        
        assertThat(map.remove(uuidKey)).isNull();
        assertThat(map.get(UUIDKey.UNIQUE_UUID).size()).isEqualTo(3);
    }

    private void put(final Person person) {
        map.put(person);
    }

    class Person implements HasKeys {

        final String name;
        private final UUIDKey uuidKey = configuration.getUUID(this);
        private UpdatableKey ageKey;

        public Person(final String name, final int age) {
            this.name = name;
            this.ageKey = new UpdatableKey(AGE, age);
        }

        @Override
        public Key[] keys() {
            return new Key[]{
                    uuidKey,
                    new Key(NAME, name),
                    ageKey
            };
        }

        public void setAge(final int age) {
            final UpdatableKey oldKey = ageKey;

            final UpdatableKey<Person> newKey = new UpdatableKey<>(AGE, age);
            ageKey = newKey;
            oldKey.update(newKey, this);
        }

        @Override
        public UUIDKey getUuidKey() {
            return uuidKey;
        }
    }
}