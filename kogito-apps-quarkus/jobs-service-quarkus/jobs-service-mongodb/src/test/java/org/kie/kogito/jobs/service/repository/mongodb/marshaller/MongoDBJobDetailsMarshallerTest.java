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
package org.kie.kogito.jobs.service.repository.mongodb.marshaller;

import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipient;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.RecipientInstance;
import org.kie.kogito.jobs.service.repository.marshaller.RecipientMarshaller;
import org.kie.kogito.jobs.service.repository.marshaller.TriggerMarshaller;
import org.kie.kogito.timer.Trigger;
import org.kie.kogito.timer.impl.PointInTimeTrigger;

import io.vertx.core.json.JsonObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.jobs.service.utils.DateUtil.DEFAULT_ZONE;

class MongoDBJobDetailsMarshallerTest {
    MongoDBJobDetailsMarshaller mongoDBJobDetailsMarshaller = new MongoDBJobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller());

    @Test
    void unmarshall() {
        String id = "testId";
        String correlationId = "testCorrelationId";
        JobStatus status = JobStatus.SCHEDULED;
        Date date = new Date();
        ZonedDateTime lastUpdate = ZonedDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE);
        Integer retries = 2;
        Integer priority = 3;
        Integer executionCounter = 4;
        String scheduledId = "testScheduledId";
        Object payload = new JsonObject().put("payload", "test").encode();
        RecipientInstance recipient = new RecipientInstance(HttpRecipient.builder().forStringPayload().url("testEndpoint").build());
        Trigger trigger = new PointInTimeTrigger(new Date().toInstant().toEpochMilli(), null, null);

        JobDetails jobDetails = JobDetails.builder()
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
                .build();

        JsonObject jsonObject = new JsonObject()
                .put("_id", "test")
                .put("id", id)
                .put("correlationId", correlationId)
                .put("status", status.name())
                .put("lastUpdate", date.getTime())
                .put("retries", retries)
                .put("executionCounter", executionCounter)
                .put("scheduledId", scheduledId)
                .put("priority", priority)
                .put("recipient", JsonObject
                        .mapFrom(HttpRecipient.builder().forStringPayload().url("testEndpoint").build())
                        .put("classType", HttpRecipient.class.getName()))
                .put("trigger", new JsonObject()
                        .put("nextFireTime", trigger.hasNextFireTime().getTime())
                        .put("classType", PointInTimeTrigger.class.getName()));

        assertThat(mongoDBJobDetailsMarshaller.unmarshall(jsonObject)).isEqualTo(jobDetails);
    }
}
