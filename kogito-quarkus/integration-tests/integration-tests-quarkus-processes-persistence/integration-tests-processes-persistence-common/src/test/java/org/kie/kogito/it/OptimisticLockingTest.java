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
package org.kie.kogito.it;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;

@TestProfile(OptimisticLockingProfile.class)
public abstract class OptimisticLockingTest extends PersistenceTest {

    @Test
    void testParallelPersistence() {
        final String pid = given().contentType(ContentType.JSON)
                .when()
                .post("/parallel")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .get("/parallel/{id}", pid)
                        .then()
                        .statusCode(404));
    }

    /**
     * Verifies that a process with an async parallel split completes its parallel branches
     * exactly once each, without a ProcessInstanceOptimisticLockingException caused by a
     * stale optimistic-lock version read under concurrent lock access.
     * <p>
     * The asyncsplit process: async TimerReplacement → parallel split → async Branch1 +
     * async Branch2 (both sleep {@code waitTime} ms and increment a non-transactional counter)
     * → parallel join → end.
     * <p>
     * With {@code waitTime=100} both worker threads complete nearly simultaneously, triggering
     * the race. Before the fix, the second thread would read a stale optimistic-lock version
     * and fail with a locking exception, causing a retry that increments the counter a third
     * time. After the fix the lock is deferred until after the transaction commits, so each
     * branch runs exactly once and the counter equals 2.
     * <p>
     * The counter is non-transactional (an AtomicInteger in a @Singleton CDI bean) so it
     * reflects every execution attempt, including those rolled back by a failed transaction.
     */
    @Test
    void testAsyncSplitWithParallelAsyncTasks() {
        // Reset the non-transactional counter before the test run.
        given().when().delete("/branch-counter").then().statusCode(204);

        final String pid = given().contentType(ContentType.JSON)
                .when()
                .body(Map.of("waitTime", 100L))
                .post("/asyncsplit")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        // Wait for the process to complete (it ends automatically after the parallel join).
        await().atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .get("/asyncsplit/{pid}", pid)
                        .then()
                        .statusCode(404));

        // Counter must be exactly 2: Branch1 ran once and Branch2 ran once.
        // A value > 2 means a retry occurred due to the stale-lock bug.
        given().when()
                .get("/branch-counter")
                .then()
                .statusCode(200)
                .body(equalTo("2"));
    }
}
