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
package org.drools.testcoverage.functional.parser;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieServices;
import org.kie.api.io.Resource;

import static org.junit.jupiter.params.provider.Arguments.arguments;

class DslParserTest extends ParserTest {

    static Stream<Arguments> parameters() {
        final Set<Arguments> set = new HashSet<>();

        for (File f : getFiles("dsl", "dslr")) {
            final String dslPath = f.getAbsolutePath();
            final File dsl = new File(dslPath.substring(0, dslPath.length() - 1));
            set.add(arguments(dsl, f, KieBaseTestConfiguration.CLOUD_EQUALITY));
            set.add(arguments(dsl, f, KieBaseTestConfiguration.CLOUD_EQUALITY_MODEL_PATTERN));
        }

        return set.stream();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testParserDsl(File dslr, File dsl, KieBaseTestConfiguration kieBaseTestConfiguration) {
        final Resource dslResource = KieServices.Factory.get().getResources().newFileSystemResource(dsl);
        final Resource dslrResource = KieServices.Factory.get().getResources().newFileSystemResource(dslr);
        KieUtil.getKieBuilderFromResources(kieBaseTestConfiguration, true, dslResource, dslrResource);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testParserDsl2(File dslr, File dsl, KieBaseTestConfiguration kieBaseTestConfiguration) {
        final Resource dslResource = KieServices.Factory.get().getResources().newFileSystemResource(dsl);
        final Resource dslrResource = KieServices.Factory.get().getResources().newFileSystemResource(dslr);
        KieUtil.getKieBuilderFromResources(kieBaseTestConfiguration, true, dslrResource, dslResource);
    }
}
