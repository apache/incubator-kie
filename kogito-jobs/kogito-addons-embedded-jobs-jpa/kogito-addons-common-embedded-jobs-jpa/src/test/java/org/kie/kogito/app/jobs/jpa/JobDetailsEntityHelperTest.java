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
package org.kie.kogito.app.jobs.jpa;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.kie.kogito.app.jobs.impl.InVMRecipient;
import org.kie.kogito.app.jobs.jpa.model.JobDetailsEntity;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.RecipientInstance;
import org.kie.kogito.timer.impl.SimpleTimerTrigger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class JobDetailsEntityHelperTest {

    private static final String CLASS_TYPE = "classType";

    /**
     * {@link JobDetailsEntityHelper#from(JobDetailsEntity)} is called on entities managed by Hibernate (e.g. loaded by
     * {@code JPAJobStore.loadActiveJobs}). Mutating their JSON attributes makes Hibernate flag them as dirty and issue an
     * UPDATE at commit, which can fail with a StaleObjectStateException when a concurrent transaction already removed the row.
     */
    @Test
    void testFromDoesNotMutateEntity() {
        JobDetailsEntity entity = newEntity();
        ObjectNode recipientBefore = entity.getRecipient().deepCopy();
        ObjectNode triggerBefore = entity.getTrigger().deepCopy();
        assertThat(recipientBefore.has(CLASS_TYPE)).isTrue();
        assertThat(triggerBefore.has(CLASS_TYPE)).isTrue();

        JobDetails jobDetails = JobDetailsEntityHelper.from(entity);

        assertThat(jobDetails).isNotNull();
        assertThat(jobDetails.getId()).isEqualTo(entity.getId());
        assertThat(entity.getRecipient()).isEqualTo(recipientBefore);
        assertThat(entity.getTrigger()).isEqualTo(triggerBefore);
    }

    @Test
    void testFromCanBeCalledRepeatedly() {
        JobDetailsEntity entity = newEntity();

        JobDetails first = JobDetailsEntityHelper.from(entity);
        JobDetails second = JobDetailsEntityHelper.from(entity);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(first.getStatus());
        assertThat(second.getTrigger().hasNextFireTime()).isEqualTo(first.getTrigger().hasNextFireTime());
    }

    private static JobDetailsEntity newEntity() {
        JobDetails jobDetails = JobDetails.builder()
                .id("job-id")
                .status(JobStatus.SCHEDULED)
                .retries(0)
                .lastUpdate(OffsetDateTime.now().toZonedDateTime())
                .recipient(new RecipientInstance(new InVMRecipient()))
                .trigger(new SimpleTimerTrigger(new Date(), 0L, ChronoUnit.MILLIS, 0, "UTC"))
                .build();
        return JobDetailsEntityHelper.merge(jobDetails, new JobDetailsEntity());
    }
}
