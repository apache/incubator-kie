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
package org.drools.fastutil;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.base.extractors.BaseObjectClassFieldReader;
import org.drools.base.rule.accessor.RightTupleValueExtractor;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.base.reteoo.BaseTuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.accessor.TupleValueExtractor;
import org.drools.base.util.FastIterator;
import org.drools.base.util.IndexedValueReader;
import org.drools.fastutil.FastUtilTreeMemory.TreeFastIterator;
import org.junit.jupiter.api.Test;
import org.drools.base.util.index.ConstraintTypeOperator;

import static org.assertj.core.api.Assertions.assertThat;

public class FastUtilTreeMemoryTest {

    @Test
    public void testMocks() {
        TupleValueExtractor leftValueExtractor = getTupleValueExtractor();
        TupleValueExtractor rightValueExtractor = getRightExtractor();

        TupleImpl tuple10 = getLeftTuple(10);

        IndexedValueReader fieldIndex = new IndexedValueReader(leftValueExtractor, rightValueExtractor);
        assertThat(fieldIndex.getLeftExtractor().getValue(tuple10)).isEqualTo(10);
    }

    @Test
    public void testGreaterThan() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.GREATER_THAN, fieldIndex, !true);

        TupleImpl tuple10 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40 = getLeftTuple(40);

        treeMemory.add(tuple10);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40);

        // check gets next after 10
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatEquals(it, 10, 20); // needs to skip the 10

        // checks key after last
        it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatIsNull(it, 50); // doesn't exist

        // checks key before first
        it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 5, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(it.next((TupleImpl) tuple)).isNull();
    }

    @Test
    public void testGreaterOrEqual() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.GREATER_OR_EQUAL, fieldIndex, !true);

        TupleImpl tuple10 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40 = getLeftTuple(40);

        treeMemory.add(tuple10);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40);

        // check gets 10
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatEquals(it, 10, 10);

        // checks key after last
        it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatIsNull(it, 50); // doesn't exist

        // checks key before first
        it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 5, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(it.next((TupleImpl) tuple)).isNull();
    }

    @Test
    public void testLessOrEqual() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.LESS_OR_EQUAL, fieldIndex, !true);

        TupleImpl tuple10 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40 = getLeftTuple(40);

        treeMemory.add(tuple10);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40);

        // check key before first
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatIsNull(it, 5);

        // check gets 10, but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 10, 10);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // check gets 10 and 20, but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 20, 10);
        tuple = assertThatEquals(tuple, it, 20);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // check gets 10, 20 and 30 but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 30, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        assertThat(it.next((TupleImpl) tuple)).isNull();


        // check gets 10, 20 and 30 but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 35, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // check key after last
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 45, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(it.next((TupleImpl) tuple)).isNull();
    }

    @Test
    public void testLessThan() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.LESS_THAN, fieldIndex, !true);

        TupleImpl tuple10 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40 = getLeftTuple(40);

        treeMemory.add(tuple10);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40);

        // check key before first
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        assertThatIsNull(it, 5);

        // check still nothing
        it =  (TreeFastIterator) treeMemory.fastIterator();
        assertThatIsNull(it, 10);

        // check gets 10 but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 20, 10);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // check gets 10 and 20  but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 30, 10);
        tuple = assertThatEquals(tuple, it, 20);
        assertThat(it.next((TupleImpl) tuple)).isNull();


        // check gets 10, 20 and 30 but nothing more
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 35, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // check key after last
        it = (TreeFastIterator) treeMemory.fastIterator();
        tuple = assertThatEquals(it, 45, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(it.next((TupleImpl) tuple)).isNull();
    }

    @Test
    public void testSharedFirstBucket() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.GREATER_THAN, fieldIndex, !true);

        TupleImpl tuple10_1 = getLeftTuple(10);
        TupleImpl tuple10_2 = getLeftTuple(10);
        TupleImpl tuple10_3 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40 = getLeftTuple(40);

        treeMemory.add(tuple10_1);
        treeMemory.add(tuple10_2);
        treeMemory.add(tuple10_3);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40);

        // checks key before first
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 5, 10);
        assertThat(tuple).isSameAs(tuple10_1);
        tuple = assertThatEquals(tuple, it, 10);
        assertThat(tuple).isSameAs(tuple10_2);
        tuple = assertThatEquals(tuple, it, 10);
        assertThat(tuple).isSameAs(tuple10_3);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(it.next((TupleImpl) tuple)).isNull();

        // Check full iterator
        FastIterator fullit = treeMemory.fullFastIterator();
        tuple = assertThatEquals(fullit,10);
        assertThat(tuple).isSameAs(tuple10_1);
        tuple = assertThatEquals(tuple, fullit, 10);
        assertThat(tuple).isSameAs(tuple10_2);
        tuple = assertThatEquals(tuple, fullit, 10);
        assertThat(tuple).isSameAs(tuple10_3);
        tuple = assertThatEquals(tuple, fullit, 20);
        tuple = assertThatEquals(tuple, fullit, 30);
        tuple = assertThatEquals(tuple, fullit, 40);
        assertThat(fullit.next(tuple)).isNull();
    }

    @Test
    public void testSharedLastBucket() {
        IndexedValueReader fieldIndex = new IndexedValueReader(getTupleValueExtractor(), getRightExtractor());
        FastUtilTreeMemory treeMemory = new FastUtilTreeMemory(ConstraintTypeOperator.GREATER_THAN, fieldIndex, !true);

        TupleImpl tuple10 = getLeftTuple(10);
        TupleImpl tuple20 = getLeftTuple(20);
        TupleImpl tuple30 = getLeftTuple(30);
        TupleImpl tuple40_1 = getLeftTuple(40);
        TupleImpl tuple40_2 = getLeftTuple(40);
        TupleImpl tuple40_3 = getLeftTuple(40);

        treeMemory.add(tuple10);
        treeMemory.add(tuple20);
        treeMemory.add(tuple30);
        treeMemory.add(tuple40_1);
        treeMemory.add(tuple40_2);
        treeMemory.add(tuple40_3);

        // checks key before first
        TreeFastIterator it = (TreeFastIterator) treeMemory.fastIterator();
        TupleImpl tuple = assertThatEquals(it, 5, 10);
        tuple = assertThatEquals(tuple, it, 20);
        tuple = assertThatEquals(tuple, it, 30);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(tuple).isSameAs(tuple40_1);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(tuple).isSameAs(tuple40_2);
        tuple = assertThatEquals(tuple, it, 40);
        assertThat(tuple).isSameAs(tuple40_3);
        assertThat(it.next((TupleImpl) tuple)).isNull();


        // check full iterator
        FastIterator fullit = treeMemory.fullFastIterator();
        tuple = assertThatEquals(fullit, 10);
        tuple = assertThatEquals(tuple, fullit, 20);
        tuple = assertThatEquals(tuple, fullit, 30);
        tuple = assertThatEquals(tuple, fullit, 40);
        assertThat(tuple).isSameAs(tuple40_1);
        tuple = assertThatEquals(tuple, fullit, 40);
        assertThat(tuple).isSameAs(tuple40_2);
        tuple = assertThatEquals(tuple, fullit, 40);
        assertThat(tuple).isSameAs(tuple40_3);
        assertThat(fullit.next(tuple)).isNull();
    }

    private static TupleImpl assertThatEquals(TupleImpl tuple, TreeFastIterator it, int expected) {
        tuple = it.next(tuple);
        return assertThatEquals(tuple, expected);
    }

    private static TupleImpl assertThatEquals(TupleImpl tuple, FastIterator<TupleImpl> it, int expected) {
        tuple = it.next(tuple);
        return assertThatEquals(tuple, expected);
    }

    private static TupleImpl assertThatEquals(TreeFastIterator it, int first, int expected) {
        TupleImpl tuple =  it.getFirst((TupleImpl) getLeftTuple(first));
        return assertThatEquals(tuple, expected);
    }

    private static TupleImpl assertThatEquals(FastIterator it, int expected) {
        TupleImpl tuple =  (TupleImpl) it.next(null);
        return assertThatEquals(tuple, expected);
    }

    private static void assertThatIsNull(TreeFastIterator it, int first) {
        TupleImpl tuple =  it.getFirst((TupleImpl) getLeftTuple(first));
        assertThat(tuple).isNull();
    }

    private static TupleImpl assertThatEquals(TupleImpl tuple, int expected) {
        assertThat(tuple.getFactHandle().getObject()).isEqualTo(expected);
        return tuple;
    }

    public static InternalFactHandle getFactHandle(int number) {
        return new DefaultFactHandle(number);
    }

    public static TupleImpl getLeftTuple(int number) {
        TupleImpl tuple = new LeftTuple();
        tuple.setFactHandle(getFactHandle(number));
        return tuple;
    };

    public static TupleValueExtractor getTupleValueExtractor() {
        TupleValueExtractor extractor = new TupleValueExtractor() {
            @Override
            public ValueType getValueType() {
                return null;
            }

            @Override
            public Object getValue(ValueResolver valueResolver, BaseTuple tuple) {
                return tuple.getFactHandle().getObject();
            }

            @Override
            public TupleValueExtractor clone() {
                return null;
            }
        };

        return extractor;
    }

    public static TupleValueExtractor getRightExtractor() {
        ReadAccessor readAccessor = new BaseObjectClassFieldReader() {
            @Override
            public Object getValue(ValueResolver valueResolver, Object object) {
                return object;
            }
        };

        RightTupleValueExtractor rightTupleValueExtractor = new RightTupleValueExtractor(readAccessor);

        return rightTupleValueExtractor;
    }
}
