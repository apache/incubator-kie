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
package org.kie.test.util;

import static org.kie.test.util.StaticMethodTestHelper.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticMethodTestHelperTest {

    @Disabled("Not working with \"quarkus-3-SNAPSHOT\" maven version")
    @Test
    void versionIsLessThanProjectVersion() {
        double version = 6.2d;
        assertThat(projectVersionIsLessThan(version)).isFalse();

        assertThat(isLessThanProjectVersion("7.0.0.Beta1", version)).isFalse();
        assertThat(isLessThanProjectVersion("7.0.0.20160123-098765", version)).isFalse();
        assertThat(isLessThanProjectVersion("7.0.0-SNAPSHOT", version)).isFalse();
    }
}
