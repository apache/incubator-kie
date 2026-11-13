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
package org.kie.kogito.app.jobs.integrations;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.kie.kogito.app.jobs.api.JobDescriptionMerger;
import org.kie.kogito.jobs.DurationExpirationTime;
import org.kie.kogito.jobs.ExpirationTime;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;
import org.kie.kogito.jobs.descriptors.ProcessJobDescription;
import org.kie.kogito.jobs.descriptors.UserTaskInstanceJobDescription;
import org.kie.kogito.jobs.service.utils.DateUtil;
import org.kie.kogito.timer.Trigger;
import org.kie.kogito.timer.impl.SimpleTimerTrigger;

public class JobDescriptionHelper {

    public static ExpirationTime toExpirationTime(Trigger trigger) {
        if (trigger instanceof SimpleTimerTrigger simpleTimerTrigger) {
            ZonedDateTime zoneDateTime = DateUtil.fromDate(simpleTimerTrigger.hasNextFireTime());
            ZonedDateTime now = DateUtil.now();
            Long delay = ChronoUnit.MILLIS.between(now, zoneDateTime);
            DurationExpirationTime time = DurationExpirationTime.repeat(Math.max(delay, 0L), simpleTimerTrigger.getPeriod(), simpleTimerTrigger.getRepeatCount());
            return time;
        }
        throw new IllegalArgumentException("this type of trigger is not supported " + trigger.getClass().getName());
    }

    public static JobDescription newJobDescription(JobDescription jobDescription, Trigger trigger) {
        List<JobDescriptionMerger> mergers = List.of(new ProcessInstanceJobDescriptionMerger(), new ProcessJobDescriptionMerger(), new UserTaskInstanceJobDescriptorMerger());
        return mergers.stream().filter(merger -> merger.accept(jobDescription)).map(merger -> merger.mergeTrigger(jobDescription, trigger)).findFirst().orElseThrow();
    }

    public static <T extends JobDescription> String resolveCorrelationId(T jobDescription) {
        String correlationId = null;
        if (jobDescription instanceof ProcessJobDescription processJobDescription) {
            correlationId = processJobDescription.processId();
        } else if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            correlationId = processInstanceJobDescription.processInstanceId();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            correlationId = userTaskInstanceJobDescription.id();
        }
        return correlationId;
    }

    /**
     * Resolves the id of the process instance that owns the job, if any. Process definition jobs
     * (e.g. start timers) are not bound to a process instance and return null.
     */
    public static <T extends JobDescription> String resolveProcessInstanceId(T jobDescription) {
        if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            return processInstanceJobDescription.processInstanceId();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            return userTaskInstanceJobDescription.processInstanceId();
        }
        return null;
    }

    public static <T extends JobDescription> String resolveProcessId(T jobDescription) {
        String processId = null;
        if (jobDescription instanceof ProcessJobDescription processJobDescription) {
            processId = processJobDescription.processId();
        } else if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            processId = processInstanceJobDescription.processId();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            processId = userTaskInstanceJobDescription.processId();
        }
        return processId;
    }

    public static <T extends JobDescription> String resolveProcessVersion(T jobDescription) {
        String processVersion = null;
        if (jobDescription instanceof ProcessJobDescription processJobDescription) {
            processVersion = processJobDescription.processVersion();
        } else if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            processVersion = processInstanceJobDescription.processVersion();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            processVersion = userTaskInstanceJobDescription.processVersion();
        }
        return processVersion;
    }

    public static <T extends JobDescription> String resolveRootProcessId(T jobDescription) {
        String rootProcessId = null;
        if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            rootProcessId = processInstanceJobDescription.rootProcessId();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            rootProcessId = userTaskInstanceJobDescription.rootProcessId();
        }
        return rootProcessId;
    }

    public static <T extends JobDescription> String resolveRootProcessVersion(T jobDescription) {
        String rootProcessVersion = null;
        if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
            rootProcessVersion = processInstanceJobDescription.rootProcessVersion();
        } else if (jobDescription instanceof UserTaskInstanceJobDescription userTaskInstanceJobDescription) {
            rootProcessVersion = userTaskInstanceJobDescription.rootProcessVersion();
        }
        return rootProcessVersion;
    }
}
