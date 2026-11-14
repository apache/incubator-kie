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
package org.kie.kogito.jobs.service.resource;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.api.Job;
import org.kie.kogito.jobs.api.JobBuilder;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.kie.kogito.jobs.service.utils.DateUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class BaseJobResourceTest extends CommonBaseJobResourceTest {

    @Override
    protected String getCreatePath() {
        return RestApiConstants.JOBS_PATH;
    }

    @Override
    protected String getGetJobQuery(String jobId) {
        return String.format(RestApiConstants.JOBS_PATH + "/%s", jobId);
    }

    @Test
    void create() throws Exception {
        final Job job = buildJob("1");
        final ScheduledJob response = create(jobToJson(job))
                .statusCode(OK)
                .extract()
                .as(ScheduledJob.class);

        // Resetting the date to set the right UTC ZoneId. This is required due to recent changes on jackson-datatype-jsr310
        response.setExpirationTime(DateUtil.instantToZonedDateTime(response.getExpirationTime().toInstant()));

        assertThat(response).isEqualTo(job);
    }

    @Test
    void createWithMissingAttributes() throws Exception {
        final Job job = JobBuilder
                .builder()
                .id("1")
                .callbackEndpoint(getCallbackEndpoint())
                .build();
        create(jobToJson(job))
                .statusCode(BAD_REQUEST);
    }

    private String jobToJson(Job job) throws JsonProcessingException {
        return objectMapper.writeValueAsString(job);
    }

    private Job buildJob(String id) {
        return buildJob(id, DateUtil.now().plusSeconds(10));
    }

    private Job buildJob(String id, ZonedDateTime expirationTime, Integer repeatLimit, Long repeatInterval) {
        return JobBuilder
                .builder()
                .id(id)
                .expirationTime(expirationTime)
                .repeatInterval(repeatInterval)
                .repeatLimit(repeatLimit)
                .callbackEndpoint(getCallbackEndpoint())
                .processId(PROCESS_ID)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .rootProcessId(ROOT_PROCESS_ID)
                .rootProcessInstanceId(ROOT_PROCESS_INSTANCE_ID)
                .nodeInstanceId(NODE_INSTANCE_ID)
                .priority(PRIORITY)
                .build();
    }

    private Job buildJob(String id, ZonedDateTime expirationTime) {
        return buildJob(id, expirationTime, 0, 0L);
    }

    @Test
    void deleteAfterCreate() throws Exception {
        final String id = "2";
        final Job job = buildJob(id);
        create(jobToJson(job));
        final ScheduledJob response = given().pathParam("id", id)
                .when()
                .delete(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK)
                .contentType(ContentType.JSON)
                .extract()
                .as(ScheduledJob.class);

        // Resetting the date to set the right UTC ZoneId. This is required due to recent changes on jackson-datatype-jsr310
        response.setExpirationTime(DateUtil.instantToZonedDateTime(response.getExpirationTime().toInstant()));

        assertThat(response).isEqualTo(job);
    }

    @Test
    void getAfterCreate() throws Exception {
        final String id = "3";
        final Job job = buildJob(id);
        create(jobToJson(job));
        assertGetScheduledJob(id);
    }

    @Test
    void executeTest() throws Exception {
        final String id = "4";
        final Job job = buildJob(id);
        create(jobToJson(job));
        final ScheduledJob scheduledJob = assertGetScheduledJob(id);
        assertThat(job.getId()).isEqualTo(scheduledJob.getId());
        assertThat(scheduledJob.getRetries()).isEqualTo(0);
        assertThat(scheduledJob.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(scheduledJob.getScheduledId()).isNotNull();
    }

    @Test
    void cancelRunningNonPeriodicJobTest() throws Exception {
        final String id = UUID.randomUUID().toString();
        final Job job = buildJob(id, DateUtil.now().plus(10, ChronoUnit.SECONDS));
        create(jobToJson(job));

        assertGetScheduledJob(id);

        //guarantee the job is scheduled on vertx
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {

            //canceled the running job
            ScheduledJob scheduledJob = assertCancelScheduledJob(id);

            //assert the job was deleted from the api perspective
            assertJobNotFound(id);

            //ensure the job was indeed canceled on vertx
            assertJobNotScheduledOnVertx(scheduledJob);
        });
    }

    @Test
    //@Disabled("see https://issues.redhat.com/browse/KOGITO-1941")
    void cancelRunningPeriodicJobTest() throws Exception {
        final String id = UUID.randomUUID().toString();
        int timeMillis = 1000;
        final Job job = buildJob(id, DateUtil.now().plus(timeMillis, ChronoUnit.MILLIS), 10, 500l);
        create(jobToJson(job));

        //check the job was created
        assertGetScheduledJob(id);

        //guarantee the job is running
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    //assert executed at least once
                    ScheduledJob scheduledJob = assertGetScheduledJob(id);
                    assertThat(scheduledJob.getExecutionCounter()).isPositive();

                    //canceled the running job
                    assertCancelScheduledJob(id);

                    //assert the job was deleted from the api perspective
                    assertJobNotFound(id);

                    //ensure the job was indeed canceled on vertx
                    assertJobNotScheduledOnVertx(scheduledJob);
                });
    }

    private void assertJobNotScheduledOnVertx(ScheduledJob scheduledJob) {
        assertJobScheduledOnVertx(scheduledJob, false);
    }

    private void assertJobScheduledOnVertx(ScheduledJob scheduledJob, boolean wasScheduled) {
        Long scheduledId = Long.valueOf(scheduledJob.getScheduledId());
        boolean timerCanceled = timer.getVertx().cancelTimer(scheduledId);
        assertThat(timerCanceled).isEqualTo(wasScheduled);
    }

    private void assertJobNotFound(String id) {
        given()
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(404);
    }

    private ScheduledJob assertCancelScheduledJob(String id) {
        ScheduledJob scheduledJob = given()
                .pathParam("id", id)
                .when()
                .delete(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK)
                .contentType(ContentType.JSON)
                .assertThat()
                .extract()
                .as(ScheduledJob.class);

        assertThat(id).isEqualTo(scheduledJob.getId());
        assertThat(scheduledJob.getStatus()).isEqualTo(JobStatus.CANCELED);
        assertThat(scheduledJob.getScheduledId()).isNotBlank();
        return scheduledJob;
    }

    private ScheduledJob assertGetScheduledJob(String id) {
        return assertGetScheduledJob(id, true);
    }

    private ScheduledJob assertGetScheduledJob(String id, boolean wasScheduled) {
        ScheduledJob scheduledJob = given()
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK)
                .contentType(ContentType.JSON)
                .assertThat()
                .extract()
                .as(ScheduledJob.class);

        assertThat(scheduledJob.getId()).isEqualTo(id);
        assertThat(scheduledJob.getPriority()).isEqualTo(PRIORITY);
        assertThat(scheduledJob.getProcessId()).isEqualTo(PROCESS_ID);
        assertThat(scheduledJob.getProcessInstanceId()).isEqualTo(PROCESS_INSTANCE_ID);
        assertThat(scheduledJob.getRootProcessId()).isEqualTo(ROOT_PROCESS_ID);
        assertThat(scheduledJob.getRootProcessInstanceId()).isEqualTo(ROOT_PROCESS_INSTANCE_ID);
        assertThat(scheduledJob.getNodeInstanceId()).isEqualTo(NODE_INSTANCE_ID);
        assertThat(scheduledJob.getCallbackEndpoint()).isEqualTo(getCallbackEndpoint());
        assertThat(scheduledJob.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        if (wasScheduled) {
            assertThat(scheduledJob.getScheduledId()).isNotBlank();
        } else {
            assertThat(scheduledJob.getScheduledId()).isBlank();
        }
        return scheduledJob;
    }

    @Test
    void testCreateExpiredJob() throws Exception {
        createExpiredJob().statusCode(BAD_REQUEST);
    }

    @Test
    void testForcingCreateExpiredJob() throws Exception {
        scheduler.setForceExecuteExpiredJobs(true);
        createExpiredJob().statusCode(OK);
    }

    private ValidatableResponse createExpiredJob() throws JsonProcessingException {
        final Job job =
                JobBuilder
                        .builder()
                        .id(UUID.randomUUID().toString())
                        .expirationTime(DateUtil.now().minusMinutes(10))
                        .callbackEndpoint(getCallbackEndpoint())
                        .processId(PROCESS_ID)
                        .processInstanceId(PROCESS_INSTANCE_ID)
                        .rootProcessId(ROOT_PROCESS_ID)
                        .rootProcessInstanceId(ROOT_PROCESS_INSTANCE_ID)
                        .nodeInstanceId(NODE_INSTANCE_ID)
                        .priority(PRIORITY)
                        .build();
        return create(jobToJson(job));
    }

    @Test
    void patchInvalidAttributesTest() throws Exception {
        final String id = UUID.randomUUID().toString();
        final Job job = buildJob(id);
        create(jobToJson(job));

        final String newCallbackEndpoint = "http://localhost/newcallback";
        Job toPatch = JobBuilder.builder().callbackEndpoint(newCallbackEndpoint).build();

        assertPatch(id, toPatch, BAD_REQUEST);

        toPatch = JobBuilder.builder().priority(10).build();
        assertPatch(id, toPatch, BAD_REQUEST);

        toPatch = JobBuilder.builder().repeatLimit(1).repeatInterval(1l).build();
        assertPatch(id, toPatch, OK);
    }

    private void assertPatch(String id, Job toPatch, int i) throws JsonProcessingException {
        given()
                .pathParam("id", id)
                .contentType(ContentType.JSON)
                .body(jobToJson(toPatch))
                .when()
                .patch(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(i);
    }

    @Test
    void patchInvalidIdPathTest() throws Exception {
        final String id = UUID.randomUUID().toString();
        final Job job = buildJob(id);
        create(jobToJson(job));

        Job toPatch = JobBuilder.builder().expirationTime(DateUtil.now()).build();

        //not found id on path
        assertPatch("invalid", toPatch, 404);

        //different id on the job object from path id
        toPatch = JobBuilder.builder().id("differentId").build();
        assertPatch(id, toPatch, BAD_REQUEST);
    }

    @Test
    void patchReschedulingTest() throws Exception {
        final String id = UUID.randomUUID().toString();
        final Job job = buildJob(id, DateUtil.now().plusHours(1));
        create(jobToJson(job));

        assertGetScheduledJob(id, false);

        Job toPatch = JobBuilder.builder().expirationTime(DateUtil.now().plusSeconds(20)).build();

        assertPatch(id, toPatch, OK);

        //ensure the job was scheduled in vertx
        assertJobScheduledOnVertx(assertGetScheduledJob(id), true);
    }
}
