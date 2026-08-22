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
package org.drools.mvel.evaluators;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import org.drools.mvel.field.FieldFactory;
import org.junit.jupiter.api.Test;
import org.drools.base.base.ValueType;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.compiler.builder.impl.EvaluatorRegistry;
import org.drools.kiesession.entrypoints.DisconnectedWorkingMemoryEntryPoint;
import org.drools.core.common.DefaultEventHandle;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test coverage for the temporal evaluators.
 */
public class TemporalEvaluatorFactoryTest {

    private EvaluatorRegistry registry = new EvaluatorRegistry();

    @Test
    public void testAfter() {
        registry.addEvaluatorDefinition( AfterEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        1,
                                                        2,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ));
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        4,
                                                        3,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          5,
                                                          2,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                {drool, "after", foo, Boolean.TRUE}, 
                {drool, "after", bar, Boolean.FALSE}, 
                {bar, "after", foo, Boolean.TRUE}, 
                {bar, "after", drool, Boolean.FALSE}, 
                {foo, "after", drool, Boolean.FALSE},
                {foo, "after", bar, Boolean.FALSE}, 
                {foo, "not after", bar, Boolean.TRUE}, 
                {foo, "not after", drool, Boolean.TRUE}, 
                {bar, "not after", drool, Boolean.TRUE}, 
                {bar, "not after", foo, Boolean.FALSE},
                {drool, "not after", foo, Boolean.FALSE}, 
                {drool, "not after", bar, Boolean.TRUE}, 
                {bar, "after[1]", foo, Boolean.TRUE}, 
                {bar, "after[0]", foo, Boolean.TRUE}, 
                {bar, "after[-3]", drool, Boolean.TRUE},
                {bar, "after[-4]", drool, Boolean.TRUE}, 
                {drool, "after[2]", foo, Boolean.TRUE}, 
                {drool, "after[1]", foo, Boolean.TRUE}, 
                {drool, "after[-2]", bar, Boolean.TRUE}, 
                {drool, "after[-3]", bar, Boolean.TRUE},
                {foo, "after[-6]", drool, Boolean.TRUE}, 
                {foo, "after[-7]", drool, Boolean.TRUE}, 
                {foo, "after[-6]", bar, Boolean.TRUE}, 
                {foo, "after[-7]", bar, Boolean.TRUE}, 
                {bar, "not after[1]", foo, Boolean.FALSE},
                {bar, "not after[0]", foo, Boolean.FALSE}, 
                {bar, "not after[-3]", drool, Boolean.FALSE}, 
                {bar, "not after[-4]", drool, Boolean.FALSE}, 
                {drool, "not after[2]", foo, Boolean.FALSE}, 
                {drool, "not after[1]", foo, Boolean.FALSE},
                {drool, "not after[-2]", bar, Boolean.FALSE}, 
                {drool, "not after[-3]", bar, Boolean.FALSE}, 
                {foo, "not after[-6]", drool, Boolean.FALSE}, 
                {foo, "not after[-7]", drool, Boolean.FALSE}, 
                {foo, "not after[-6]", bar, Boolean.FALSE},
                {foo, "not after[-7]", bar, Boolean.FALSE}, 
                {drool, "after[1,4]", foo, Boolean.TRUE}, 
                {drool, "after[3,6]", foo, Boolean.FALSE}, 
                {drool, "after[-3,1]", bar, Boolean.TRUE},
                {drool, "after[-1,3]", bar, Boolean.FALSE},
                {bar, "after[1,5]", foo, Boolean.TRUE}, 
                {bar, "after[2,5]", foo, Boolean.FALSE}, 
                {bar, "after[-3,0]", drool, Boolean.TRUE}, 
                {bar, "after[-2,1]", drool, Boolean.FALSE}, 
                {foo, "after[-7,-3]", bar, Boolean.TRUE},
                {foo, "after[-5,-1]", bar, Boolean.FALSE}, 
                {foo, "after[-6,-5]", drool, Boolean.TRUE},
                {foo, "after[-5,-4]", drool, Boolean.FALSE}, 
                {drool, "not after[1,4]", foo, Boolean.FALSE}, 
                {drool, "not after[3,6]", foo, Boolean.TRUE},
                {drool, "not after[-3,1]", bar, Boolean.FALSE}, 
                {drool, "not after[-1,3]", bar, Boolean.TRUE}, 
                {bar, "not after[1,5]", foo, Boolean.FALSE}, 
                {bar, "not after[2,5]", foo, Boolean.TRUE}, 
                {bar, "not after[-3,0]", drool, Boolean.FALSE},
                {bar, "not after[-2,1]", drool, Boolean.TRUE}, 
                {foo, "not after[-7,-3]", bar, Boolean.FALSE}, 
                {foo, "not after[-5,-1]", bar, Boolean.TRUE}, 
                {foo, "not after[-6,-5]", drool, Boolean.FALSE},
                {foo, "not after[-5,-4]", drool, Boolean.TRUE},};

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testBefore() {
        registry.addEvaluatorDefinition( BeforeEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        1,
                                                        2,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        2,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          5,
                                                          3,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                {foo, "before", drool, Boolean.TRUE}, 
                {foo, "before", bar, Boolean.FALSE}, 
                {drool, "before", foo, Boolean.FALSE}, 
                {drool, "before", bar, Boolean.FALSE}, 
                {bar, "before", drool, Boolean.TRUE},
                {bar, "before", foo, Boolean.FALSE},
                {foo, "not before", drool, Boolean.FALSE},
                {foo, "not before", bar, Boolean.TRUE}, 
                {drool, "not before", foo, Boolean.TRUE},
                {drool, "not before", bar, Boolean.TRUE},
                {bar, "not before", drool, Boolean.FALSE},
                {bar, "not before", foo, Boolean.TRUE},
                {foo, "before[2]", drool, Boolean.TRUE}, 
                {foo, "before[3]", drool, Boolean.FALSE},
                {foo, "before[-1]", bar, Boolean.TRUE},
                {foo, "before[-2]", bar, Boolean.TRUE}, 
                {bar, "before[1]", drool, Boolean.TRUE}, 
                {bar, "before[2]", drool, Boolean.FALSE},
                {bar, "before[-3]", foo, Boolean.TRUE},
                {bar, "before[-2]", foo, Boolean.FALSE},
                {drool, "before[-6]", bar, Boolean.TRUE},
                {drool, "before[-5]", bar, Boolean.FALSE}, 
                {drool, "before[-7]", foo, Boolean.TRUE},
                {drool, "before[-8]", foo, Boolean.TRUE}, 
                {foo, "not before[2]", drool, Boolean.FALSE},
                {foo, "not before[3]", drool, Boolean.TRUE}, 
                {foo, "not before[-1]", bar, Boolean.FALSE},
                {foo, "not before[-2]", bar, Boolean.FALSE}, 
                {bar, "not before[1]", drool, Boolean.FALSE},
                {bar, "not before[2]", drool, Boolean.TRUE},
                {bar, "not before[-3]", foo, Boolean.FALSE}, 
                {bar, "not before[-2]", foo, Boolean.TRUE}, 
                {drool, "not before[-6]", bar, Boolean.FALSE}, 
                {drool, "not before[-5]", bar, Boolean.TRUE}, 
                {drool, "not before[-7]", foo, Boolean.FALSE},
                {drool, "not before[-8]", foo, Boolean.FALSE},
                {foo, "before[2,4]", drool, Boolean.TRUE},
                {foo, "before[3,4]", drool, Boolean.FALSE}, 
                {foo, "before[-1,1]", bar, Boolean.TRUE},
                {foo, "before[0,-2]", bar, Boolean.TRUE},
                {bar, "before[0,4]", drool, Boolean.TRUE}, 
                {bar, "before[2,4]", drool, Boolean.FALSE}, 
                {bar, "before[-4,0]", foo, Boolean.TRUE}, 
                {bar, "before[-2,0]", foo, Boolean.FALSE},
                {drool, "before[-6,-3]", bar, Boolean.TRUE},
                {drool, "before[-5,-3]", bar, Boolean.FALSE}, 
                {drool, "before[-7,-4]", foo, Boolean.TRUE}, 
                {drool, "before[-6,-4]", foo, Boolean.FALSE}, 
                {foo, "not before[2,4]", drool, Boolean.FALSE}, 
                {foo, "not before[3,4]", drool, Boolean.TRUE},
                {foo, "not before[-1,1]", bar, Boolean.FALSE}, 
                {foo, "not before[0,-2]", bar, Boolean.FALSE}, 
                {bar, "not before[0,4]", drool, Boolean.FALSE}, 
                {bar, "not before[2,4]", drool, Boolean.TRUE},
                {bar, "not before[-4,0]", foo, Boolean.FALSE}, 
                {bar, "not before[-2,0]", foo, Boolean.TRUE}, 
                {drool, "not before[-6,-3]", bar, Boolean.FALSE}, 
                {drool, "not before[-5,-3]", bar, Boolean.TRUE},
                {drool, "not before[-7,-4]", foo, Boolean.FALSE}, 
                {drool, "not before[-6,-4]", foo, Boolean.TRUE}};

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testCoincides() {
        registry.addEvaluatorDefinition( CoincidesEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        3,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        3,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          2,
                                                          2,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         1,
                                                         2,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                {foo, "coincides", bar, Boolean.TRUE}, 
                {foo, "coincides", drool, Boolean.FALSE}, 
                {foo, "coincides", mole, Boolean.FALSE}, 
                {drool, "coincides", mole, Boolean.FALSE}, 
                {foo, "not coincides", bar, Boolean.FALSE},
                {foo, "not coincides", drool, Boolean.TRUE}, 
                {foo, "not coincides", mole, Boolean.TRUE}, 
                {drool, "not coincides", mole, Boolean.TRUE}, 
                {foo, "coincides[1]", bar, Boolean.TRUE}, 
                {foo, "coincides[1]", drool, Boolean.TRUE},
                {foo, "coincides[2]", mole, Boolean.TRUE}, 
                {foo, "coincides[1]", mole, Boolean.FALSE}, 
                {drool, "coincides[1]", mole, Boolean.TRUE},
                {foo, "not coincides[1]", bar, Boolean.FALSE}, 
                {foo, "not coincides[1]", drool, Boolean.FALSE},
                {foo, "not coincides[2]", mole, Boolean.FALSE}, 
                {foo, "not coincides[1]", mole, Boolean.TRUE}, 
                {drool, "not coincides[1]", mole, Boolean.FALSE}, 
                {foo, "coincides[1,2]", bar, Boolean.TRUE},
                {foo, "coincides[0,1]", drool, Boolean.TRUE},
                {foo, "coincides[1,0]", drool, Boolean.FALSE}, 
                {foo, "coincides[1,2]", mole, Boolean.TRUE}, 
                {foo, "coincides[1,1]", mole, Boolean.FALSE}, 
                {drool, "coincides[1,1]", mole, Boolean.TRUE},
                {drool, "coincides[0,1]", mole, Boolean.FALSE},
                {foo, "not coincides[1,2]", bar, Boolean.FALSE}, 
                {foo, "not coincides[0,1]", drool, Boolean.FALSE},
                {foo, "not coincides[1,0]", drool, Boolean.TRUE},
                {foo, "not coincides[1,2]", mole, Boolean.FALSE},
                {foo, "not coincides[1,1]", mole, Boolean.TRUE}, 
                {drool, "not coincides[1,1]", mole, Boolean.FALSE}, 
                {drool, "not coincides[0,1]", mole, Boolean.TRUE}};

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testDuring() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        4,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          1,
                                                          5,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         7,
                                                         6,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {foo, "during", bar, Boolean.FALSE}, 
                 {foo, "during", drool, Boolean.FALSE}, 
                 {foo, "during", mole, Boolean.FALSE}, 
                 {bar, "during", foo, Boolean.TRUE}, 
                 {bar, "during", drool, Boolean.FALSE}, 
                 {bar, "during", mole, Boolean.FALSE}, 
                 {foo, "not during", bar, Boolean.TRUE}, 
                 {foo, "not during", drool, Boolean.TRUE}, 
                 {foo, "not during", mole, Boolean.TRUE}, 
                 {bar, "not during", foo, Boolean.FALSE}, 
                 {bar, "not during", drool, Boolean.TRUE}, 
                 {bar, "not during", mole, Boolean.TRUE}, 

                 {bar, "during[2]", foo, Boolean.TRUE}, 
                 {bar, "during[3]", foo, Boolean.TRUE}, 
                 {bar, "during[1]", foo, Boolean.FALSE},
                 {bar, "not during[2]", foo, Boolean.FALSE}, 
                 {bar, "not during[3]", foo, Boolean.FALSE}, 
                 {bar, "not during[1]", foo, Boolean.TRUE},
                 
                 {bar, "during[1, 2]", foo, Boolean.TRUE}, 
                 {bar, "during[2, 3]", foo, Boolean.FALSE}, 
                 {bar, "during[3, 3]", foo, Boolean.FALSE},
                 {bar, "not during[1, 2]", foo, Boolean.FALSE}, 
                 {bar, "not during[2, 3]", foo, Boolean.TRUE}, 
                 {bar, "not during[3, 3]", foo, Boolean.TRUE},
                 
                 {bar, "during[2, 2, 1, 1]", foo, Boolean.TRUE}, 
                 {bar, "during[1, 5, 1, 3]", foo, Boolean.TRUE}, 
                 {bar, "during[0, 1, 0, 3]", foo, Boolean.FALSE},
                 {bar, "not during[2, 2, 1, 1]", foo, Boolean.FALSE}, 
                 {bar, "not during[1, 5, 1, 3]", foo, Boolean.FALSE}, 
                 {bar, "not during[0, 1, 0, 3]", foo, Boolean.TRUE}
                 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testIncludes() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        4,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          1,
                                                          5,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         7,
                                                         6,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {bar, "includes", foo, Boolean.FALSE}, 
                 {drool, "includes", foo, Boolean.FALSE}, 
                 {mole, "includes", foo, Boolean.FALSE}, 
                 {foo, "includes", bar, Boolean.TRUE}, 
                 {drool, "includes", bar, Boolean.FALSE}, 
                 {mole, "includes", bar, Boolean.FALSE}, 
                 {bar, "not includes", foo, Boolean.TRUE}, 
                 {drool, "not includes", foo, Boolean.TRUE}, 
                 {mole, "not includes", foo, Boolean.TRUE}, 
                 {foo, "not includes", bar, Boolean.FALSE}, 
                 {drool, "not includes", bar, Boolean.TRUE}, 
                 {mole, "not includes", bar, Boolean.TRUE}, 

                 {foo, "includes[2]", bar, Boolean.TRUE}, 
                 {foo, "includes[3]", bar, Boolean.TRUE}, 
                 {foo, "includes[1]", bar, Boolean.FALSE},
                 {foo, "not includes[2]", bar, Boolean.FALSE}, 
                 {foo, "not includes[3]", bar, Boolean.FALSE}, 
                 {foo, "not includes[1]", bar, Boolean.TRUE},
                 
                 {foo, "includes[1, 2]", bar, Boolean.TRUE}, 
                 {foo, "includes[2, 3]", bar, Boolean.FALSE}, 
                 {foo, "includes[3, 3]", bar, Boolean.FALSE},
                 {foo, "not includes[1, 2]", bar, Boolean.FALSE}, 
                 {foo, "not includes[2, 3]", bar, Boolean.TRUE}, 
                 {foo, "not includes[3, 3]", bar, Boolean.TRUE},
                 
                 {foo, "includes[2, 2, 1, 1]", bar, Boolean.TRUE}, 
                 {foo, "includes[1, 5, 1, 3]", bar, Boolean.TRUE}, 
                 {foo, "includes[0, 1, 0, 3]", bar, Boolean.FALSE},
                 {foo, "not includes[2, 2, 1, 1]", bar, Boolean.FALSE}, 
                 {foo, "not includes[1, 5, 1, 3]", bar, Boolean.FALSE}, 
                 {foo, "not includes[0, 1, 0, 3]", bar, Boolean.TRUE}
                 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testFinishes() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        5,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          2,
                                                          10,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         7,
                                                         6,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {bar,   "finishes", foo, Boolean.TRUE}, 
                 {drool, "finishes", foo, Boolean.FALSE}, 
                 {mole,  "finishes", foo, Boolean.FALSE}, 
                 {foo,   "finishes", bar, Boolean.FALSE},
                 
                 {bar,   "not finishes", foo, Boolean.FALSE}, 
                 {drool, "not finishes", foo, Boolean.TRUE}, 
                 {mole,  "not finishes", foo, Boolean.TRUE}, 
                 {foo,   "not finishes", bar, Boolean.TRUE},
                 
                 {bar,   "finishes[1]", foo, Boolean.TRUE}, 
                 {drool, "finishes[1]", foo, Boolean.FALSE}, 
                 {mole,  "finishes[1]", foo, Boolean.TRUE}, 
                 {foo,   "finishes[1]", bar, Boolean.FALSE},
                 
                 {bar,   "not finishes[1]", foo, Boolean.FALSE}, 
                 {drool, "not finishes[1]", foo, Boolean.TRUE}, 
                 {mole,  "not finishes[1]", foo, Boolean.FALSE}, 
                 {foo,   "not finishes[1]", bar, Boolean.TRUE},
                 
                 {mole,  "finishes[3]", foo, Boolean.TRUE}, 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testFinishedBy() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        5,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          2,
                                                          10,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         7,
                                                         6,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {foo, "finishedby", bar, Boolean.TRUE}, 
                 {foo, "finishedby", drool, Boolean.FALSE}, 
                 {foo, "finishedby", mole, Boolean.FALSE}, 
                 {bar, "finishedby", foo, Boolean.FALSE},
                 
                 {foo, "not finishedby", bar, Boolean.FALSE}, 
                 {foo, "not finishedby", drool, Boolean.TRUE}, 
                 {foo, "not finishedby", mole, Boolean.TRUE}, 
                 {bar, "not finishedby", foo, Boolean.TRUE},
                 
                 {foo, "finishedby[1]", bar, Boolean.TRUE}, 
                 {foo, "finishedby[1]", drool, Boolean.FALSE}, 
                 {foo, "finishedby[1]", mole, Boolean.TRUE}, 
                 {bar, "finishedby[1]", foo, Boolean.FALSE},
                 
                 {foo, "not finishedby[1]", bar, Boolean.FALSE}, 
                 {foo, "not finishedby[1]", drool, Boolean.TRUE}, 
                 {foo, "not finishedby[1]", mole, Boolean.FALSE}, 
                 {bar, "not finishedby[1]", foo, Boolean.TRUE},
                 
                 {foo, "finishedby[3]", mole, Boolean.TRUE}, 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testStarts() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          2,
                                                          10,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         1,
                                                         4,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {bar,   "starts", foo, Boolean.TRUE}, 
                 {drool, "starts", foo, Boolean.FALSE}, 
                 {mole,  "starts", foo, Boolean.FALSE}, 
                 {foo,   "starts", bar, Boolean.FALSE},
                 
                 {bar,   "not starts", foo, Boolean.FALSE}, 
                 {drool, "not starts", foo, Boolean.TRUE}, 
                 {mole,  "not starts", foo, Boolean.TRUE}, 
                 {foo,   "not starts", bar, Boolean.TRUE},
                 
                 {bar,   "starts[1]", foo, Boolean.TRUE}, 
                 {drool, "starts[1]", foo, Boolean.FALSE}, 
                 {mole,  "starts[1]", foo, Boolean.TRUE}, 
                 {foo,   "starts[1]", bar, Boolean.FALSE},
                 
                 {bar,   "not starts[1]", foo, Boolean.FALSE}, 
                 {drool, "not starts[1]", foo, Boolean.TRUE}, 
                 {mole,  "not starts[1]", foo, Boolean.FALSE}, 
                 {foo,   "not starts[1]", bar, Boolean.TRUE},
                 
                 {mole,  "starts[3]", foo, Boolean.TRUE}, 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testStartedBy() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        10,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          2,
                                                          10,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         1,
                                                         6,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
                 {foo, "startedby", bar, Boolean.TRUE}, 
                 {foo, "startedby", drool, Boolean.FALSE}, 
                 {foo, "startedby", mole, Boolean.FALSE}, 
                 {bar, "startedby", foo, Boolean.FALSE},
                 
                 {foo, "not startedby", bar, Boolean.FALSE}, 
                 {foo, "not startedby", drool, Boolean.TRUE}, 
                 {foo, "not startedby", mole, Boolean.TRUE}, 
                 {bar, "not startedby", foo, Boolean.TRUE},
                 
                 {foo, "startedby[1]", bar, Boolean.TRUE}, 
                 {foo, "startedby[1]", drool, Boolean.FALSE}, 
                 {foo, "startedby[1]", mole, Boolean.TRUE}, 
                 {bar, "startedby[1]", foo, Boolean.FALSE},
                 
                 {foo, "not startedby[1]", bar, Boolean.FALSE}, 
                 {foo, "not startedby[1]", drool, Boolean.TRUE}, 
                 {foo, "not startedby[1]", mole, Boolean.FALSE}, 
                 {bar, "not startedby[1]", foo, Boolean.TRUE},
                 
                 {foo, "startedby[3]", mole, Boolean.TRUE}, 
                };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testMeets() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        10,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          8,
                                                          5,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         11,
                                                         4,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
             {foo,   "meets", bar, Boolean.TRUE}, 
             {foo,   "meets", drool, Boolean.FALSE}, 
             {foo,   "meets", mole, Boolean.FALSE}, 
             
             {foo,   "not meets", bar, Boolean.FALSE}, 
             {foo,   "not meets", drool, Boolean.TRUE}, 
             {foo,   "not meets", mole, Boolean.TRUE}, 
             
             {foo,   "meets[1]", bar, Boolean.TRUE}, 
             {foo,   "meets[1]", drool, Boolean.FALSE}, 
             {foo,   "meets[1]", mole, Boolean.TRUE}, 
             {foo,   "meets[2]", drool, Boolean.TRUE}, 
             
             {foo,   "not meets[1]", bar, Boolean.FALSE}, 
             {foo,   "not meets[1]", drool, Boolean.TRUE}, 
             {foo,   "not meets[1]", mole, Boolean.FALSE}, 
             {foo,   "not meets[2]", drool, Boolean.FALSE}
            };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testMetBy() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        10,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          5,
                                                          3,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         4,
                                                         7,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
             {foo,   "metby", bar, Boolean.TRUE}, 
             {foo,   "metby", drool, Boolean.FALSE}, 
             {foo,   "metby", mole, Boolean.FALSE}, 
             
             {foo,   "not metby", bar, Boolean.FALSE}, 
             {foo,   "not metby", drool, Boolean.TRUE}, 
             {foo,   "not metby", mole, Boolean.TRUE}, 
             
             {foo,   "metby[1]", bar, Boolean.TRUE}, 
             {foo,   "metby[1]", drool, Boolean.FALSE}, 
             {foo,   "metby[1]", mole, Boolean.TRUE}, 
             {foo,   "metby[2]", drool, Boolean.TRUE}, 
             
             {foo,   "not metby[1]", bar, Boolean.FALSE}, 
             {foo,   "not metby[1]", drool, Boolean.TRUE}, 
             {foo,   "not metby[1]", mole, Boolean.FALSE}, 
             {foo,   "not metby[2]", drool, Boolean.FALSE}
            };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testOverlaps() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        2,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        7,
                                                        7,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          11,
                                                          5,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         5,
                                                         5,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
             {foo,   "overlaps", bar, Boolean.TRUE}, 
             {foo,   "overlaps", drool, Boolean.FALSE}, 
             {foo,   "overlaps", mole, Boolean.FALSE}, 
             
             {foo,   "not overlaps", bar, Boolean.FALSE}, 
             {foo,   "not overlaps", drool, Boolean.TRUE}, 
             {foo,   "not overlaps", mole, Boolean.TRUE}, 
             
             {foo,   "overlaps[3]", bar, Boolean.TRUE}, 
             {foo,   "overlaps[3]", drool, Boolean.FALSE}, 
             {foo,   "overlaps[3]", mole, Boolean.FALSE}, 
             {foo,   "overlaps[2]", bar, Boolean.FALSE}, 
             {foo,   "overlaps[6]", mole, Boolean.FALSE}, 
             
             {foo,   "not overlaps[3]", bar, Boolean.FALSE}, 
             {foo,   "not overlaps[3]", drool, Boolean.TRUE}, 
             {foo,   "not overlaps[3]", mole, Boolean.TRUE}, 
             {foo,   "not overlaps[2]", bar, Boolean.TRUE}, 
             {foo,   "not overlaps[6]", mole, Boolean.TRUE},
             
             {foo,   "overlaps[1,3]", bar, Boolean.TRUE}, 
             {foo,   "overlaps[1,3]", drool, Boolean.FALSE}, 
             {foo,   "overlaps[1,3]", mole, Boolean.FALSE}, 
             {foo,   "overlaps[4,6]", bar, Boolean.FALSE}, 
             {foo,   "overlaps[1,8]", mole, Boolean.FALSE}, 
             
             {foo,   "not overlaps[1,3]", bar, Boolean.FALSE}, 
             {foo,   "not overlaps[1,3]", drool, Boolean.TRUE}, 
             {foo,   "not overlaps[1,3]", mole, Boolean.TRUE}, 
             {foo,   "not overlaps[4,6]", bar, Boolean.TRUE}, 
             {foo,   "not overlaps[1,8]", mole, Boolean.TRUE}
            };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    @Test
    public void testOverlapedBy() {
        registry.addEvaluatorDefinition( DuringEvaluatorDefinition.class.getName() );

        DefaultEventHandle foo = new DefaultEventHandle(1,
                                                        "foo",
                                                        1,
                                                        7,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle bar = new DefaultEventHandle(2,
                                                        "bar",
                                                        1,
                                                        2,
                                                        8,
                                                        new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle drool = new DefaultEventHandle(1,
                                                          "drool",
                                                          1,
                                                          11,
                                                          5,
                                                          new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );
        DefaultEventHandle mole = new DefaultEventHandle(1,
                                                         "mole",
                                                         1,
                                                         7,
                                                         3,
                                                         new DisconnectedWorkingMemoryEntryPoint( "DEFAULT" ) );

        final Object[][] data = {
             {foo,   "overlappedby", bar, Boolean.TRUE}, 
             {foo,   "overlappedby", drool, Boolean.FALSE}, 
             {foo,   "overlappedby", mole, Boolean.FALSE}, 
             
             {foo,   "not overlappedby", bar, Boolean.FALSE}, 
             {foo,   "not overlappedby", drool, Boolean.TRUE}, 
             {foo,   "not overlappedby", mole, Boolean.TRUE}, 
             
             {foo,   "overlappedby[3]", bar, Boolean.TRUE}, 
             {foo,   "overlappedby[3]", drool, Boolean.FALSE}, 
             {foo,   "overlappedby[3]", mole, Boolean.FALSE}, 
             {foo,   "overlappedby[2]", bar, Boolean.FALSE}, 
             {foo,   "overlappedby[6]", mole, Boolean.FALSE}, 
             
             {foo,   "not overlappedby[3]", bar, Boolean.FALSE}, 
             {foo,   "not overlappedby[3]", drool, Boolean.TRUE}, 
             {foo,   "not overlappedby[3]", mole, Boolean.TRUE}, 
             {foo,   "not overlappedby[2]", bar, Boolean.TRUE}, 
             {foo,   "not overlappedby[6]", mole, Boolean.TRUE},
             
             {foo,   "overlappedby[1,3]", bar, Boolean.TRUE}, 
             {foo,   "overlappedby[1,3]", drool, Boolean.FALSE}, 
             {foo,   "overlappedby[1,3]", mole, Boolean.FALSE}, 
             {foo,   "overlappedby[4,6]", bar, Boolean.FALSE}, 
             {foo,   "overlappedby[1,8]", mole, Boolean.FALSE}, 
             
             {foo,   "not overlappedby[1,3]", bar, Boolean.FALSE}, 
             {foo,   "not overlappedby[1,3]", drool, Boolean.TRUE}, 
             {foo,   "not overlappedby[1,3]", mole, Boolean.TRUE}, 
             {foo,   "not overlappedby[4,6]", bar, Boolean.TRUE}, 
             {foo,   "not overlappedby[1,8]", mole, Boolean.TRUE}
            };

        runEvaluatorTest( data,
                          ValueType.OBJECT_TYPE );
    }

    private void runEvaluatorTest(final Object[][] data,
                                  final ValueType valueType) {
        final ReadAccessor extractor = new MockExtractor();
        for ( int i = 0; i < data.length; i++ ) {
            final Object[] row = data[i];
            boolean isNegated = ((String) row[1]).startsWith( "not " );
            // System.out.println((String) row[1]);
            String evaluatorStr = isNegated ? ((String) row[1]).substring( 4 ) : (String) row[1];
            boolean isConstrained = evaluatorStr.endsWith( "]" );
            String parameters = null;
            if ( isConstrained ) {
                parameters = evaluatorStr.split( "\\[" )[1];
                evaluatorStr = evaluatorStr.split( "\\[" )[0];
                parameters = parameters.split( "\\]" )[0];
            }
            EvaluatorDefinition evalDef = registry.getEvaluatorDefinition( evaluatorStr );
            assertThat(evalDef).isNotNull();
            final MvelEvaluator evaluator = (MvelEvaluator) evalDef.getEvaluator( valueType, evaluatorStr, isNegated, parameters );

            checkEvaluatorMethodWith2Extractors( valueType,
                                                 extractor,
                                                 row,
                                                 evaluator );
            checkEvaluatorMethodCachedRight( valueType,
                                             extractor,
                                             row,
                                             evaluator );
            checkEvaluatorMethodCachedLeft( valueType,
                                            extractor,
                                            row,
                                            evaluator );
            checkEvaluatorMethodWithFieldValue( valueType,
                                                extractor,
                                                row,
                                                evaluator );

            assertThat(evaluator.getValueType()).isEqualTo(valueType);

        }
    }

    private void checkEvaluatorMethodWith2Extractors(final ValueType valueType,
                                                     final ReadAccessor extractor,
                                                     final Object[] row,
                                                     final MvelEvaluator evaluator) {
        final boolean result = evaluator.evaluate( null,
                                                   extractor,
                                                   (DefaultEventHandle) row[0],
                                                   extractor,
                                                   (DefaultEventHandle) row[2]);
        final String message = "The evaluator type: [" + valueType + "] with 2 extractors incorrectly returned " + result + " for [" + row[0] + " " + row[1] + " " + row[2] + "]. It was asserted to return " + row[3];

        if ( row[3] == Boolean.TRUE ) {
            assertThat(result).as(message).isTrue();
        } else {
            assertThat(result).as(message).isFalse();
        }
    }

    private void checkEvaluatorMethodCachedRight(final ValueType valueType,
                                                 final ReadAccessor extractor,
                                                 final Object[] row,
                                                 final MvelEvaluator evaluator) {
        final VariableRestriction.VariableContextEntry context = this.getContextEntry( evaluator,
                                                                   extractor,
                                                                   valueType,
                                                                   row,
                                                                   false );
        final boolean result = evaluator.evaluateCachedRight( null,
                                                              context,
                                                              (DefaultEventHandle) row[2]);
        final String message = "The evaluator type: [" + valueType + "] with CachedRight incorrectly returned " + result + " for [" + row[0] + " " + row[1] + " " + row[2] + "]. It was asserted to return " + row[3];

        if ( row[3] == Boolean.TRUE ) {
            assertThat(result).as(message).isTrue();
        } else {
            assertThat(result).as(message).isFalse();
        }
    }

    private void checkEvaluatorMethodCachedLeft(final ValueType valueType,
                                                final ReadAccessor extractor,
                                                final Object[] row,
                                                final MvelEvaluator evaluator) {
        final VariableRestriction.VariableContextEntry context = this.getContextEntry( evaluator,
                                                                   extractor,
                                                                   valueType,
                                                                   row,
                                                                   true );
        final boolean result = evaluator.evaluateCachedLeft( null,
                                                             context,
                                                             (DefaultEventHandle) row[0]);
        final String message = "The evaluator type: [" + valueType + "] with CachedLeft incorrectly returned " + result + " for [" + row[0] + " " + row[1] + " " + row[2] + "]. It was asserted to return " + row[3];

        if ( row[3] == Boolean.TRUE ) {
            assertThat(result).as(message).isTrue();
        } else {
            assertThat(result).as(message).isFalse();
        }
    }

    private void checkEvaluatorMethodWithFieldValue(final ValueType valueType,
                                                    final ReadAccessor extractor,
                                                    final Object[] row,
                                                    final Evaluator evaluator) {
        final FieldValue value = FieldFactory.getInstance().getFieldValue( row[2] );
        RuntimeException exc = null;
        try {
            evaluator.evaluate( null,
                                extractor,
                                (DefaultEventHandle) row[0],
                                value );
        } catch ( RuntimeException e ) {
            exc = e;
        }
        assertThat(exc).isNotNull();
    }

    private VariableRestriction.VariableContextEntry getContextEntry(final Evaluator evaluator,
                                                                     final ReadAccessor extractor,
                                                                     final ValueType valueType,
                                                                     final Object[] row,
                                                                     final boolean left) {
        final Declaration declaration = new Declaration( "test",
                                                         extractor,
                                                         null );
        final ValueType coerced = evaluator.getCoercedValueType();

        if ( evaluator.isTemporal() ) {
            if ( evaluator instanceof BeforeEvaluatorDefinition.BeforeEvaluator || evaluator instanceof MeetsEvaluatorDefinition.MeetsEvaluator) {
                VariableRestriction.LeftStartRightEndContextEntry context = new VariableRestriction.LeftStartRightEndContextEntry( extractor,
                                                                                           declaration,
                                                                                           evaluator );
                if (left) {
                    context.timestamp = ((DefaultEventHandle) row[2]).getStartTimestamp();
                } else {
                    context.timestamp = ((DefaultEventHandle) row[0]).getEndTimestamp();
                }
                return context;
            }

            if ( evaluator instanceof AfterEvaluatorDefinition.AfterEvaluator || evaluator instanceof MetByEvaluatorDefinition.MetByEvaluator) {
                VariableRestriction.LeftEndRightStartContextEntry context = new VariableRestriction.LeftEndRightStartContextEntry( extractor,
                                                                                           declaration,
                                                                                           evaluator );
                if (left) {
                    context.timestamp = ((DefaultEventHandle) row[2]).getEndTimestamp();
                } else {
                    context.timestamp = ((DefaultEventHandle) row[0]).getStartTimestamp();
                }
                return context;
            }

            // else
            VariableRestriction.TemporalVariableContextEntry context = new VariableRestriction.TemporalVariableContextEntry( extractor,
                                                                                     declaration,
                                                                                     evaluator );
            if (left) {
                context.startTS = ((DefaultEventHandle) row[2]).getStartTimestamp();
                context.endTS = ((DefaultEventHandle) row[2]).getEndTimestamp();
            } else {
                context.startTS = ((DefaultEventHandle) row[0]).getStartTimestamp();
                context.endTS = ((DefaultEventHandle) row[0]).getEndTimestamp();
            }
            return context;
        }

        if ( coerced.isIntegerNumber() ) {
            final VariableRestriction.LongVariableContextEntry context = new VariableRestriction.LongVariableContextEntry( extractor,
                                                                                   declaration,
                                                                                   evaluator );

            if ( row[2] == null ) {
                context.leftNull = true;
            } else {
                context.left = ((Number) row[2]).longValue();
            }

            if ( row[0] == null ) {
                context.rightNull = true;
            } else {
                context.right = ((Number) row[0]).longValue();
            }
            return context;
        } else if ( coerced.isChar() ) {
            final VariableRestriction.CharVariableContextEntry context = new VariableRestriction.CharVariableContextEntry( extractor,
                                                                                   declaration,
                                                                                   evaluator );

            if ( row[2] == null ) {
                context.leftNull = true;
            } else {
                context.left = ((Character) row[2]).charValue();
            }

            if ( row[0] == null ) {
                context.rightNull = true;
            } else {
                context.right = ((Character) row[0]).charValue();
            }
            return context;
        } else if ( coerced.isBoolean() ) {
            final VariableRestriction.BooleanVariableContextEntry context = new VariableRestriction.BooleanVariableContextEntry( extractor,
                                                                                         declaration,
                                                                                         evaluator );

            if ( row[2] == null ) {
                context.leftNull = true;
            } else {
                context.left = ((Boolean) row[2]).booleanValue();
            }

            if ( row[0] == null ) {
                context.rightNull = true;
            } else {
                context.right = ((Boolean) row[0]).booleanValue();
            }
            return context;
        } else if ( coerced.isDecimalNumber() ) {
            final VariableRestriction.DecimalVariableContextEntry context = new VariableRestriction.DecimalVariableContextEntry( extractor,
                                                                                       declaration,
                                                                                       evaluator );
            if ( row[2] == null ) {
                context.leftNull = true;
            } else {
                context.left = ((Number) row[2]).doubleValue();
            }

            if ( row[0] == null ) {
                context.rightNull = true;
            } else {
                context.right = ((Number) row[0]).doubleValue();
            }
            return context;
        } else {
            final VariableRestriction.ObjectVariableContextEntry context = new VariableRestriction.ObjectVariableContextEntry( extractor,
                                                                                       declaration,
                                                                                       evaluator );
            if ( row[2] == null ) {
                context.leftNull = true;
            } else {
                context.left = row[2];
            }

            if ( row[0] == null ) {
                context.rightNull = true;
            } else {
                context.right = row[0];
            }
            return context;
        }
    }

    public static class MockExtractor
        implements
        ReadAccessor {

        private static final long serialVersionUID = 510l;

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
        }

        public void writeExternal(ObjectOutput out) throws IOException {
        }

        public boolean isSelfReference() {
            return true;
        }

        public boolean getBooleanValue(GlobalResolver valueResolver,
                                       final Object object) {
            return object != null ? ((Boolean) object).booleanValue() : false;
        }

        public byte getByteValue(GlobalResolver valueResolver,
                                 final Object object) {
            return object != null ? ((Number) object).byteValue() : (byte) 0;
        }

        public char getCharValue(GlobalResolver valueResolver,
                                 final Object object) {
            return (char) getWholeNumberValue(valueResolver, object);
        }

        public double getDecimalValue(GlobalResolver valueResolver,
                                      final Object object) {
            return object != null ? ((Number) object).doubleValue() : 0.0;
        }

        public Class<?> getExtractToClass() {
            return null;
        }

        public String getExtractToClassName() {
            return null;
        }

        public int getHashCode(GlobalResolver valueResolver,
                               final Object object) {
            return 0;
        }


        public long getWholeNumberValue(GlobalResolver valueResolver,
                                 final Object object) {
            return object != null ? ((Number) object).longValue() : 0;
        }

        public Method getNativeReadMethod() {
            return null;
        }

        public String getNativeReadMethodName() {
            return null;
        }

        public short getShortValue(GlobalResolver valueResolver,
                                   final Object object) {
            return object != null ? ((Number) object).shortValue() : (short) 0;
        }

        public Object getValue(GlobalResolver valueResolver,
                               final Object object) {
            return object;
        }

        public boolean isNullValue(GlobalResolver valueResolver,
                                   final Object object) {
            return object == null;
        }

        public ValueType getValueType() {
            // TODO Auto-generated method stub
            return null;
        }

        public int getIndex() {
            return 0;
        }

        public boolean isGlobal() {
            return false;
        }

        public int getHashCode(Object object) {
            // TODO Auto-generated method stub
            return 0;
        }

        public Object getValue(Object object) {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean isNullValue(Object object) {
            // TODO Auto-generated method stub
            return false;
        }
    }

}
