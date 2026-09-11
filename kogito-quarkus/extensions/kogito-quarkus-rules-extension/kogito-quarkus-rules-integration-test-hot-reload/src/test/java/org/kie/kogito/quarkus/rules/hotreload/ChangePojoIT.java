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
package org.kie.kogito.quarkus.rules.hotreload;

import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.kogito.quarkus.rules.hotreload.newunit.Person;
import org.kie.kogito.test.utils.SocketUtils;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChangePojoIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static final String PACKAGE = "org.kie.kogito.quarkus.rules.hotreload";
    private static final String RESOURCE_FILE = PACKAGE.replace('.', '/') + "/adult.drl";

    // Pick a free port up front and hand it to the dev-mode app through the archive's
    // application.properties; since Quarkus 3.33 dev mode no longer writes its resolved
    // port back for tests to read.
    final static int httpPort = SocketUtils.findAvailablePort();

    @RegisterExtension
    final static QuarkusDevModeTest test = new QuarkusDevModeTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Person.class)
                    .addAsResource(new StringAsset("quarkus.kogito.devservices.enabled=false\nquarkus.http.port=" + httpPort), "application.properties")
                    .addAsResource("drl1.txt", RESOURCE_FILE));

    @Test
    public void test1Change() {
        doTest(true);
    }

    @Test
    public void test2Changes() {
        doTest(false);
    }

    private void doTest(boolean allChangesAtOnce) {
        String personsPayload1 = "{\"persons\":[{\"name\":\"Mario\",\"age\":45,\"adult\":false},{\"name\":\"Sofia\",\"age\":17,\"adult\":false}]}";
        String personsPayload2 = "{\"persons\":[{\"name\":\"Mario\",\"surname\":\"Fusco\",\"age\":45,\"adult\":false},{\"name\":\"Sofia\",\"surname\":\"Fusco\",\"age\":17,\"adult\":false}]}";

        List<Map> persons = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(personsPayload1)
                .when()
                .post("/find-adults")
                .then()
                .statusCode(200)
                .extract()
                .as(List.class);

        assertEquals(1, persons.size());
        assertEquals("Mario", persons.get(0).get("name"));

        test.modifySourceFile(Person.class, s -> POJO2);

        if (!allChangesAtOnce) {
            persons = given()
                    .baseUri("http://localhost:" + httpPort)
                    .contentType(ContentType.JSON)
                    .accept(ContentType.JSON)
                    .body(personsPayload2)
                    .when()
                    .post("/find-adults")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(List.class);

            assertEquals(1, persons.size());
            assertEquals("Mario", persons.get(0).get("name"));
        }

        test.modifyResourceFile(RESOURCE_FILE, s -> DRL2);

        persons = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(personsPayload2).when()
                .post("/find-adults")
                .then()
                .statusCode(200)
                .extract().as(List.class);

        assertEquals(2, persons.size());
        assertTrue(persons.stream().map(p -> p.get("name")).allMatch(n -> n.equals("Mario") || n.equals("Sofia")));
    }

    private static String POJO2 =
            "package org.kie.kogito.quarkus.rules.hotreload.newunit;\n" +
                    "\n" +
                    "public class Person {\n" +
                    "\n" +
                    "    private String name;\n" +
                    "    private String surname;\n" +
                    "\n" +
                    "    private int age;\n" +
                    "\n" +
                    "    private boolean adult;\n" +
                    "\n" +
                    "    public Person() {\n" +
                    "    }\n" +
                    "\n" +
                    "    public String getName() {\n" +
                    "        return name;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setName(String name) {\n" +
                    "        this.name = name;\n" +
                    "    }\n" +
                    "\n" +
                    "    public String getSurname() {\n" +
                    "        return surname;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setSurname( String surname ) {\n" +
                    "        this.surname = surname;\n" +
                    "    }\n" +
                    "\n" +
                    "    public int getAge() {\n" +
                    "        return age;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setAge(int age) {\n" +
                    "        this.age = age;\n" +
                    "    }\n" +
                    "\n" +
                    "    public boolean isAdult() {\n" +
                    "        return adult;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setAdult(boolean adult) {\n" +
                    "        this.adult = adult;\n" +
                    "    }\n" +
                    "\n" +
                    "}\n";

    private static String DRL2 =
            "package io.quarkus.it.kogito.drools;\n" +
                    "unit AdultUnit;\n" +
                    "\n" +
                    "import org.kie.kogito.quarkus.rules.hotreload.newunit.Person;\n" +
                    "\n" +
                    "import org.drools.ruleunits.api.DataStore;\n" +
                    "import org.drools.ruleunits.api.RuleUnitData;\n" +
                    "\n" +
                    "declare AdultUnit extends RuleUnitData\n" +
                    "   persons: DataStore<Person>\n" +
                    "end\n" +
                    "\n" +
                    "rule CheckAdult when\n" +
                    "    $p: /persons[ age >= 16, surname == \"Fusco\" ]\n" +
                    "then\n" +
                    "    modify($p) { setAdult(true) };\n" +
                    "end\n" +
                    "\n" +
                    "rule CheckNotAdult when\n" +
                    "    $p: /persons[ age < 16, surname == \"Fusco\" ]\n" +
                    "then\n" +
                    "    modify($p) { setAdult(false) };\n" +
                    "end\n" +
                    "\n" +
                    "query FindAdultNames\n" +
                    "    /persons[ adult, $name : name ]\n" +
                    "end\n" +
                    "query FindAdults\n" +
                    "    $p: /persons[ adult ]\n" +
                    "end\n";
}
