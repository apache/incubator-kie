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
package org.drools.mvel.compiler.command;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.drools.core.common.ReteEvaluator;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.phreak.SynchronizedPropagationList;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class PropagationListTest {

    @Test 
    @Disabled
    public void test() {
        final int OBJECT_NR = 1000000;
        final int THREAD_NR = 8;

        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_NR, r -> {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        try {
            final long[] results = new long[10];

            for (int counter = 0; counter < results.length;) {

                final Checker checker = new Checker(THREAD_NR);
                final PropagationList propagationList = new SynchronizedPropagationList(null);
                final CompletionService<Boolean> ecs = new ExecutorCompletionService<Boolean>(executor);

                final long start = System.nanoTime();

                for (int i = 0; i < THREAD_NR; i++) {
                    ecs.submit(getTask(OBJECT_NR, checker, propagationList, i));
                }

                try {
                    Thread.sleep(1L);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }

                for (int i = 0; i < THREAD_NR * 20; i++) {
                    //System.out.println("FLUSHING!");
                    propagationList.flush();
                    try {
                        Thread.sleep(1L);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                boolean success = true;
                for (int i = 0; i < THREAD_NR; i++) {
                    try {
                        success = ecs.take().get() && success;
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                propagationList.flush();

                results[counter++] = System.nanoTime() - start;

                System.out.println("Threads DONE!");
            }

            analyzeResults(results);
        } finally {
            executor.shutdownNow();
        }
    }

    private void analyzeResults(final long[] results) {
        long min = results[0];
        long max = results[0];
        long total = results[0];
        for (final long result : results) {
            if (result < min) {
                min = result;
            }
            if (result > max) {
                max = result;
            }
            total += result;
        }
        System.out.println("min = " + min);
        System.out.println("max = " + max);
        System.out.println("avg = " + ((total - min - max) / (results.length - 2)));
    }

    private Callable<Boolean> getTask(final int OBJECT_NR, final Checker checker, final PropagationList propagationList, final int i) {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                for (int j = 0; j < OBJECT_NR; j++) {
                    propagationList.addEntry(new TestEntry(checker, i, j));
                }
                return true;
            }
        };
    }

    public static class TestEntry extends AbstractPropagationEntry<ReteEvaluator> {

        final Checker checker;
        final int i;
        final int j;

        public TestEntry(final Checker checker, final int i, final int j) {
            this.checker = checker;
            this.i = i;
            this.j = j;
        }

        @Override
        public void internalExecute(final ReteEvaluator reteEvaluator) {
            checker.check(this);
        }

        @Override
        public String toString() {
            return "[" + i + ", " + j + "]";
        }
    }

    public static class Checker {
        private final int[] counters;

        public Checker(final int nr) {
            counters = new int[nr];
        }

        public void check(final TestEntry entry) {
            if (counters[entry.i] == entry.j) {
                if (entry.j % 10000 == 0) {
                    //System.out.println("[" + entry.i + ", " + entry.j / 10000 + "]");
                }
                counters[entry.i]++;
            } else {
                throw new RuntimeException("ERROR for thread " + entry.i + " expected " + counters[entry.i] + " but was " + entry.j);
            }
        }
    }
}
