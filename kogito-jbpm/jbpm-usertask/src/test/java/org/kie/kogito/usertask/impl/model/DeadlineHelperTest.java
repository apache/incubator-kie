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
package org.kie.kogito.usertask.impl.model;

import java.time.Duration;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.ExpirationTime;
import org.kie.kogito.usertask.model.DeadlineInfo;
import org.kie.kogito.usertask.model.Notification;
import org.kie.kogito.usertask.model.Reassignment;
import org.kie.kogito.usertask.model.ScheduleInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class DeadlineHelperTest {

    @Test
    public void testRepetition() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs|body:NotCompleted repeated notification every 5secs]@[R/PT5S]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("body", "NotCompleted repeated notification every 5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(-1);
        assertThat(scheduleInfo.getEndDate()).isNull();
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isEqualTo(5000L);
        assertThat(time.repeatLimit()).isEqualTo(-1);
        assertThat(ZonedDateTime.now().plus(Duration.ofMillis(5003)).isAfter(time.get())).isTrue();
    }

    @Test
    public void testRepetitionWithEndDate() {
        ZonedDateTime future = ZonedDateTime.now().plus(Duration.ofMinutes(2)).plus(Duration.ofSeconds(2));
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs]@[R/PT5S/" + future.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(-1);
        assertEqualsDate(future, scheduleInfo.getEndDate());
        assertThat(scheduleInfo.getStartDate()).isNull();
        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isEqualTo(5000L);
        assertThat(time.repeatLimit()).isEqualTo(24);
        assertThat(ZonedDateTime.now().plus(Duration.ofSeconds(5)).isAfter(time.get())).as("Date is " + time.get()).isTrue();
    }

    @Test
    public void testRepetitionWithEndDateCornerCase() {
        ZonedDateTime future = ZonedDateTime.now().plus(Duration.ofSeconds(5));
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs]@[R/PT5S/" + future.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(-1);
        assertEqualsDate(future, scheduleInfo.getEndDate());
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isNull();
        assertThat(time.repeatLimit()).isZero();
        assertThat(ZonedDateTime.now().plus(Duration.ofSeconds(5)).isAfter(time.get())).isTrue();
    }

    @Test
    public void testRepetitionWithEndDateAndLimit() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs]@[R2/PT5S/2021-03-18T18:55:01+01:00]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(2);
        assertThat(scheduleInfo.getEndDate()).isEqualTo(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"));
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isEqualTo(5000L);
        assertThat(time.repeatLimit()).isEqualTo(2);
        assertEqualsDate(ZonedDateTime.parse("2021-03-18T18:54:51+01:00"), time.get());
    }

    @Test
    public void testRepetitionStartEndDate() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs]@[R1/2021-03-18T18:55:01+01:00/2021-03-18T18:55:06+01:00]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(1);
        assertThat(scheduleInfo.getStartDate()).isEqualTo(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"));
        assertThat(scheduleInfo.getEndDate()).isEqualTo(ZonedDateTime.parse("2021-03-18T18:55:06+01:00"));
        assertThat(scheduleInfo.getDuration()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isEqualTo(5000L);
        assertThat(time.repeatLimit()).isEqualTo(1);
        assertEqualsDate(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"), time.get());
    }

    @Test
    public void testRepetitionWithStartDate() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:5secs]@[R26/2021-03-18T18:55:01+01:00/PT2M]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "5secs");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(26);
        assertThat(scheduleInfo.getStartDate()).isEqualTo(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"));
        assertThat(scheduleInfo.getEndDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isEqualTo(Duration.ofMinutes(2).toMillis());
        assertThat(time.repeatLimit()).isEqualTo(26);
        assertEqualsDate(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"), time.get());
    }

    @Test
    public void testExactDate() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:exact date]@[2021-03-18T18:55:01+01:00]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "exact date");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isNull();
        assertThat(scheduleInfo.getNumRepetitions()).isZero();
        assertThat(scheduleInfo.getEndDate()).isEqualTo(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"));
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isNull();
        assertThat(time.repeatLimit()).isZero();
        assertEqualsDate(ZonedDateTime.parse("2021-03-18T18:55:01+01:00"), time.get());
    }

    @Test
    public void testLargeRepetition() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:more than 1 year]@[R/P1Y3WT1H]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "more than 1 year");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(DeadlineHelper.getDuration(Period.ofYears(1).plus(Period.ofWeeks(3)), Duration.ofHours(1)));
        assertThat(scheduleInfo.getNumRepetitions()).isEqualTo(-1);
        assertThat(scheduleInfo.getEndDate()).isNull();
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time
                .repeatInterval()).isEqualTo(DeadlineHelper.getDuration(Period.ofYears(1).plus(Period.ofWeeks(3)), Duration.ofHours(1))
                        .toMillis());
        assertThat(time.repeatLimit()).isEqualTo(-1);
    }

    @Test
    public void testMultipleDuration() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:1 and 4 hour]@[PT1H,PT4H]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "1 and 4 hour");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(2);
        assertThat(scheduling.stream().filter(s -> s.getDuration().equals(Duration.ofHours(1)) || s.getDuration()
                .equals(Duration.ofHours(4))).count()).isEqualTo(2);
    }

    @Test
    public void testMultipleNotification() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:1 hour]@[PT1H]^[subject:4 hour]@[PT4H]");
        assertThat(deadlines).hasSize(2);

        for (DeadlineInfo<Notification> deadline : deadlines) {
            if ("1 hour".equals(deadline.getNotification().getData().get("subject"))) {
                assertThat(deadline.getScheduleInfo().iterator().next().getDuration()).isEqualTo(Duration.ofHours(1));
            } else if ("4 hour".equals(deadline.getNotification().getData().get("subject"))) {
                assertThat(deadline.getScheduleInfo().iterator().next().getDuration()).isEqualTo(Duration.ofHours(4));
            } else {
                fail("Unexpected subject value");
            }
        }
    }

    @Test
    public void testReassignmentShorthandMinutes() {
        Collection<DeadlineInfo<Reassignment>> reassignments = DeadlineHelper.parseReassignments(
                "[users:Pepe,Pepa|groups:Admin,Managers]@[1m]");
        assertThat(reassignments).hasSize(1);
        DeadlineInfo<Reassignment> reassignment = reassignments.iterator().next();
        assertThat(reassignment.getNotification().getPotentialUsers()).containsExactlyInAnyOrder("Pepe", "Pepa");
        assertThat(reassignment.getNotification().getPotentialGroups()).containsExactlyInAnyOrder("Admin", "Managers");
        assertThat(reassignment.getScheduleInfo().iterator().next().getDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    public void testReassignmentMinutes() {
        Collection<DeadlineInfo<Reassignment>> reassignments = DeadlineHelper.parseReassignments(
                "[users:Pepe,Pepa|groups:Admin,Managers]@[PT1M]");
        assertThat(reassignments).hasSize(1);
        DeadlineInfo<Reassignment> reassignment = reassignments.iterator().next();
        assertThat(reassignment.getNotification().getPotentialUsers()).containsExactlyInAnyOrder("Pepe", "Pepa");
        assertThat(reassignment.getNotification().getPotentialGroups()).containsExactlyInAnyOrder("Admin", "Managers");
        assertThat(reassignment.getScheduleInfo().iterator().next().getDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    public void testReassignmentWithDateBasedDuration() {
        Collection<DeadlineInfo<Reassignment>> reassignments = DeadlineHelper.parseReassignments(
                "[users:John,Jane|groups:Admins]@[365D]");
        assertThat(reassignments).hasSize(1);
        DeadlineInfo<Reassignment> reassignment = reassignments.iterator().next();
        assertThat(reassignment.getNotification().getPotentialUsers()).containsExactlyInAnyOrder("John", "Jane");
        assertThat(reassignment.getNotification().getPotentialGroups()).containsExactlyInAnyOrder("Admins");
        assertThat(reassignment.getScheduleInfo().iterator().next().getDuration()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    public void testReassignmentShorthandMonths() {
        Collection<DeadlineInfo<Reassignment>> reassignments = DeadlineHelper.parseReassignments(
                "[users:John,Jane|groups:Admins]@[7M]");
        assertThat(reassignments).hasSize(1);
        DeadlineInfo<Reassignment> reassignment = reassignments.iterator().next();
        assertThat(reassignment.getNotification().getPotentialUsers()).containsExactlyInAnyOrder("John", "Jane");
        assertThat(reassignment.getNotification().getPotentialGroups()).containsExactlyInAnyOrder("Admins");
        assertThat(reassignment.getScheduleInfo().iterator().next().getDuration()).isEqualTo(DeadlineHelper.getDuration(Period.ofMonths(7), Duration.ZERO));
    }

    @Test
    public void testReassignmentMonths() {
        Collection<DeadlineInfo<Reassignment>> reassignments = DeadlineHelper.parseReassignments(
                "[users:John,Jane|groups:Admins]@[P7M]");
        assertThat(reassignments).hasSize(1);
        DeadlineInfo<Reassignment> reassignment = reassignments.iterator().next();
        assertThat(reassignment.getNotification().getPotentialUsers()).containsExactlyInAnyOrder("John", "Jane");
        assertThat(reassignment.getNotification().getPotentialGroups()).containsExactlyInAnyOrder("Admins");
        assertThat(reassignment.getScheduleInfo().iterator().next().getDuration()).isEqualTo(DeadlineHelper.getDuration(Period.ofMonths(7), Duration.ZERO));
    }

    private void assertEqualsDate(ZonedDateTime expectedDate, ZonedDateTime calculatedDate) {
        assertThat(calculatedDate.toInstant().getEpochSecond()).isEqualTo(expectedDate.toInstant().getEpochSecond());
    }

    @Test
    public void testStandaloneShorthandDuration() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:1 minute shorthand]@[1m]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "1 minute shorthand");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(scheduleInfo.getNumRepetitions()).isZero();
        assertThat(scheduleInfo.getEndDate()).isNull();
        assertThat(scheduleInfo.getStartDate()).isNull();

        ExpirationTime time = DeadlineHelper.getExpirationTime(scheduleInfo);
        assertThat(time.repeatInterval()).isNull();
        assertThat(time.repeatLimit()).isZero();
        assertThat(ZonedDateTime.now().plus(Duration.ofMillis(60005)).isAfter(time.get())).isTrue();
    }

    @Test
    public void testMultipleStandaloneShorthandDurations() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:1 min and 2 hours]@[1m,2h]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "1 min and 2 hours");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(2);
        assertThat(scheduling.stream().map(ScheduleInfo::getDuration))
                .containsExactlyInAnyOrder(Duration.ofMinutes(1), Duration.ofHours(2));
    }

    @Test
    public void testInvalidShorthandDuration() {
        assertThatThrownBy(() -> DeadlineHelper.parseDeadlines("[subject:Invalid unit]@[1x]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown shorthand duration unit: x");
    }

    @Test
    public void testZeroShorthandDuration() {
        Collection<DeadlineInfo<Notification>> deadlines = DeadlineHelper.parseDeadlines(
                "[subject:Zero seconds]@[0s]");
        assertThat(deadlines).hasSize(1);
        DeadlineInfo<Notification> deadlineInfo = deadlines.iterator().next();
        assertThat(deadlineInfo.getNotification().getData()).containsEntry("subject", "Zero seconds");
        Collection<ScheduleInfo> scheduling = deadlineInfo.getScheduleInfo();
        assertThat(scheduling).hasSize(1);
        ScheduleInfo scheduleInfo = scheduling.iterator().next();
        assertThat(scheduleInfo.getDuration()).isEqualTo(Duration.ZERO);
    }

}
