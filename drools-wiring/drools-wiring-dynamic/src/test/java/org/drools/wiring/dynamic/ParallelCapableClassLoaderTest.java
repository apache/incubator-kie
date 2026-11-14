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
package org.drools.wiring.dynamic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parallel capability is per-class, not inherited: the JVM only enables per-name class loading
 * locks when the loader's concrete runtime class and its whole superclass chain are registered.
 * Without the subclass registration, getClassLoadingLock falls back to locking the loader itself
 * and all class loading serializes on one monitor (issue #6758).
 *
 * <p>The probe subclass verifies this deterministically: {@link
 * ClassLoader#registerAsParallelCapable()} returns {@code false} when any ancestor class is not
 * itself registered, so these assertions fail if the registration is ever removed from the
 * loaders under test.
 */
class ParallelCapableClassLoaderTest {

    @Test
    void dynamicProjectClassLoaderChainIsParallelCapable() {
        assertThat(DynamicProbe.PARALLEL_CAPABLE).as("registerAsParallelCapable() in a subclass only succeeds when the whole "
                + "DynamicProjectClassLoader ancestry is registered").isTrue();
        DynamicProbe probe = new DynamicProbe(getClass().getClassLoader());
        Object lockA = probe.lockFor("com.example.A");
        assertThat(lockA).as("a parallel-capable loader must not lock on itself").isNotSameAs(probe);
        assertThat(probe.lockFor("com.example.B")).isNotSameAs(lockA);
    }

    private static class DynamicProbe extends DynamicProjectClassLoader {
        private static final boolean PARALLEL_CAPABLE = registerAsParallelCapable();

        private DynamicProbe(ClassLoader parent) {
            super(parent, null);
        }

        private Object lockFor(String className) {
            return getClassLoadingLock(className);
        }
    }
}
