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
package org.kie.kogito.jobs.service.repository.marshaller;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipient;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipientStringPayloadData;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.Recipient;
import org.kie.kogito.jobs.service.model.RecipientInstance;
import org.kie.kogito.timer.Trigger;
import org.kie.kogito.timer.impl.PointInTimeTrigger;

import io.vertx.core.json.JsonObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.jobs.service.utils.DateUtil.DEFAULT_ZONE;

class JobDetailsMarshallerTest {

    JobDetailsMarshaller jobDetailsMarshaller;

    JobDetails jobDetails;

    JsonObject jsonObject;

    @BeforeEach
    void setUp() {
        jobDetailsMarshaller = new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller());

        String id = "testId";
        String correlationId = "testCorrelationId";
        JobStatus status = JobStatus.SCHEDULED;
        Date lastUpdateDate = new Date();
        ZonedDateTime lastUpdate = ZonedDateTime.ofInstant(lastUpdateDate.toInstant(), DEFAULT_ZONE);
        Integer retries = 2;
        Integer priority = 3;
        Integer executionCounter = 4;
        String scheduledId = "testScheduledId";
        String payload = "test";
        Recipient recipient = new RecipientInstance(HttpRecipient.builder().forStringPayload().url("url").payload(HttpRecipientStringPayloadData.from(payload)).build());
        Trigger trigger = new PointInTimeTrigger(new Date().toInstant().toEpochMilli(), null, null);
        Long executionTimeout = 10L;
        ChronoUnit executionTimeoutUnit = ChronoUnit.SECONDS;
        Date createdDate = new Date();
        ZonedDateTime created = ZonedDateTime.ofInstant(createdDate.toInstant(), DEFAULT_ZONE);

        jobDetails = JobDetails.builder()
                .id(id)
                .correlationId(correlationId)
                .status(status)
                .lastUpdate(lastUpdate)
                .retries(retries)
                .executionCounter(executionCounter)
                .scheduledId(scheduledId)
                .priority(priority)
                .recipient(recipient)
                .trigger(trigger)
                .executionTimeout(executionTimeout)
                .executionTimeoutUnit(executionTimeoutUnit)
                .created(created)
                .build();

        jsonObject = new JsonObject()
                .put("id", id)
                .put("correlationId", correlationId)
                .put("status", status.name())
                .put("lastUpdate", lastUpdateDate.getTime())
                .put("retries", retries)
                .put("executionCounter", executionCounter)
                .put("scheduledId", scheduledId)
                .put("priority", priority)
                .put("recipient", JsonObject
                        .mapFrom(HttpRecipient.builder().forStringPayload().url("url").payload(HttpRecipientStringPayloadData.from(payload)).build())
                        .put("classType", HttpRecipient.class.getName()))
                .put("trigger", new JsonObject()
                        .put("nextFireTime", trigger.hasNextFireTime().getTime())
                        .put("classType", PointInTimeTrigger.class.getName()))
                .put("executionTimeout", executionTimeout)
                .put("executionTimeoutUnit", executionTimeoutUnit.name())
                .put("created", createdDate.getTime());
    }

    @Test
    void marshall() {
        assertThat(jobDetailsMarshaller.marshall(jobDetails)).isEqualTo(jsonObject);
    }

    @Test
    void marshallNull() {
        assertThat(jobDetailsMarshaller.marshall(null)).isNull();
    }

    @Test
    void unmarshall() {
        assertThat(jobDetailsMarshaller.unmarshall(jsonObject)).isEqualTo(jobDetails);
    }

    @Test
    void unmarshallNull() {
        assertThat(jobDetailsMarshaller.unmarshall(null)).isNull();
    }
}
