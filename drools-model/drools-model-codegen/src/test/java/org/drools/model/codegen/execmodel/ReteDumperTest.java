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
package org.drools.model.codegen.execmodel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import org.drools.core.common.BaseNode;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.model.codegen.execmodel.domain.Address;
import org.drools.model.codegen.execmodel.domain.Person;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

class ReteDumperTest extends BaseModelTest {

    public static final String DRL_RIGHT_EXISTS = "import " + Person.class.getCanonicalName() + ";\n" +
            "import " + Address.class.getCanonicalName() + ";\n" +
            """
                    rule R1
                    when
                        $person: Person()
                        exists Address(number > 18) from $person.addresses
                    then
                    end
                    """;

    @ParameterizedTest
    @MethodSource("parameters")
    void rightInputAdapterNodeSink_coreReteDumper(RUN_TYPE runType) {
        KieSession ksession = getKieSession(runType, DRL_RIGHT_EXISTS);
        KieBase kbase = ksession.getKieBase();

        List<String> lines = getLinesByCoreReteDumper(kbase);

        // The first RightInputAdapterNode should be followed by ExistsNode
        assertSinkOrder(lines, "RightInputAdapterNode", "[ExistsNode");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void rightInputAdapterNodeSink_execmodelReteDumper(RUN_TYPE runType) {
        KieSession ksession = getKieSession(runType, DRL_RIGHT_EXISTS);
        KieBase kbase = ksession.getKieBase();

        List<String> lines = getLinesByExecmodelReteDumper(kbase);

        // The first RightInputAdapterNode should be followed by ExistsNode
        assertSinkOrder(lines, "RightInputAdapterNode", "[ExistsNode");
    }

    private static void assertSinkOrder(List<String> lines, String targetNodePrefix, String expectedNextNodePrefix) {
        // get index of the first line which starts with targetNodePrefix
        int targetNodeIndex = -1;
        String targetNodeLine = null;
        String targetNode = null;
        for (int i = 0; i < lines.size(); i++) {
            targetNodeLine = lines.get(i);
            targetNode = targetNodeLine.trim();
            if (targetNode.startsWith(targetNodePrefix)) {
                targetNodeIndex = i;
                break;
            }
        }
        assertThat(targetNodeIndex).as(targetNodePrefix + " is not found").isNotEqualTo(-1);

        String nextLine = lines.get(targetNodeIndex + 1);
        String nextNode = nextLine.trim();
        assertThat(nextNode).as(targetNodePrefix + " should be followed by " + expectedNextNodePrefix).startsWith(expectedNextNodePrefix);

        // The next node has to be a leaf of the target node. It means the number of leading spaces in nextLine should be greater than that in targetNodeLine
        int targetNodeLeadingSpaces = targetNodeLine.indexOf(targetNode);
        int nextNodeLeadingSpaces = nextLine.indexOf(nextNode);
        assertThat(nextNodeLeadingSpaces).as(expectedNextNodePrefix + " should be a sink of " + targetNodePrefix)
                .isGreaterThan(targetNodeLeadingSpaces);
    }

    private static List<String> getLinesByCoreReteDumper(KieBase kbase) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        org.drools.core.reteoo.ReteDumper dumper = new org.drools.core.reteoo.ReteDumper();
        dumper.setWriter(pw);
        dumper.dump(kbase);
        pw.flush();

        String result = sw.toString();
        return Arrays.stream(result.split("\n")).toList();
    }

    private static List<String> getLinesByExecmodelReteDumper(KieBase kbase) {
        List<String> lines = new ArrayList<>();
        BiConsumer<String, BaseNode> lineCollector = (ident, node) -> lines.add(ident + node.toString());
        org.drools.model.codegen.execmodel.ReteDumper.visitRete(((InternalKnowledgeBase) kbase).getRete(), lineCollector);
        return lines;
    }
}
