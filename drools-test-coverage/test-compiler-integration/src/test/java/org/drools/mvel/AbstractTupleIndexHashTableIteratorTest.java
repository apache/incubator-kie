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
package org.drools.mvel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.drools.compiler.builder.impl.EvaluatorRegistry;
import org.drools.core.base.ClassFieldAccessorCache;
import org.drools.base.base.ClassObjectType;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.constraint.BetaConstraint;
import org.drools.base.util.SingleLinkedEntry;
import org.drools.core.util.index.IndexMemory;
import org.drools.core.util.index.TupleIndexHashTable;
import org.drools.drl.parser.impl.Operator;
import org.drools.model.functions.Predicate2;
import org.drools.model.index.BetaIndexImpl;
import org.drools.modelcompiler.util.EvaluationUtil;
import org.drools.mvel.accessors.ClassFieldAccessorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTupleIndexHashTableIteratorTest {

    public static EvaluatorRegistry registry = new EvaluatorRegistry();

    private IndexMemory.EqualityMemoryType originalMemoryImpl;

    @BeforeEach
    public void before() {
        try {
            originalMemoryImpl = IndexMemory.getEqualityMemoryType();
            IndexMemory.setEqualityMemoryType(IndexMemory.EqualityMemoryType.INTERNAL);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }


    @AfterEach
    public void after() {
        try {
            IndexMemory.setEqualityMemoryType(originalMemoryImpl);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static Stream<Boolean> parameters() {
        return Stream.of(false, true);
    }

    protected static BetaConstraint createFooThisEqualsDBetaConstraint(boolean useLambdaConstraint) {
        if (useLambdaConstraint) {
            return createFooThisEqualsDBetaConstraintWithLambdaConstraint();
        } else {
            return createFooThisEqualsDBetaConstraintWithMvelConstraint();
        }
    }

    private static BetaConstraint createFooThisEqualsDBetaConstraintWithLambdaConstraint() {
        Pattern pattern = new Pattern(0, new ClassObjectType(Foo.class));
        Pattern varPattern = new Pattern(1, new ClassObjectType(Foo.class));
        Predicate2<Foo, Foo> predicate = new Predicate2.Impl<Foo, Foo>((_this, d) -> EvaluationUtil.areNullSafeEquals(_this, d));
        BetaIndexImpl<Foo, Foo, Foo> index = new BetaIndexImpl<Foo, Foo, Foo>(Foo.class, org.drools.model.Index.ConstraintType.EQUAL, 1, _this -> _this, d -> d, Foo.class);
        return LambdaConstraintTestUtil.createLambdaConstraint2(Foo.class, Foo.class, pattern, varPattern, "d", predicate, index);
    }

    private static BetaConstraint createFooThisEqualsDBetaConstraintWithMvelConstraint() {
        ClassFieldAccessorStore store = new ClassFieldAccessorStore();
        store.setClassFieldAccessorCache(new ClassFieldAccessorCache(Thread.currentThread().getContextClassLoader()));
        store.setEagerWire(true);
        ReadAccessor extractor = store.getReader(Foo.class, "this");
        Declaration declaration = new Declaration("d", extractor, new Pattern(0, new ClassObjectType(Foo.class)));
        String expression = "this " + Operator.BuiltInOperator.EQUAL.getOperator().getOperatorString() + " d";
        return new MVELConstraintTestUtil(expression, declaration, extractor);
    }

    protected List createTableIndexListForAssertion(TupleIndexHashTable hashTable) {
        SingleLinkedEntry[] table = hashTable.getTable();
        List                list  = new ArrayList();
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null) {
                List entries = new ArrayList();
                entries.add(i);
                SingleLinkedEntry entry = table[i];
                while (entry != null) {
                    entries.add(entry);
                    entry = entry.getNext();
                }
                list.add(entries.toArray());
            }
        }
        return list;
    }

    protected void assertTableIndex(List list, int index, int expectedTableIndex, int expectedSizeOfEntries) {
        Object[] entries = (Object[]) list.get(index);
        assertThat(entries[0]).isEqualTo(expectedTableIndex);
        assertThat(entries.length).isEqualTo(expectedSizeOfEntries);
    }

    public static class Foo {

        private String val;
        private int hashCode;

        public Foo(String val, int hashCode) {
            this.val = val;
            this.hashCode = hashCode;
        }

        public String getVal() {
            return val;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Foo other = (Foo) obj;
            if (hashCode != other.hashCode)
                return false;
            if (val == null) {
                if (other.val != null)
                    return false;
            } else if (!val.equals(other.val))
                return false;
            return true;
        }

    }

}
