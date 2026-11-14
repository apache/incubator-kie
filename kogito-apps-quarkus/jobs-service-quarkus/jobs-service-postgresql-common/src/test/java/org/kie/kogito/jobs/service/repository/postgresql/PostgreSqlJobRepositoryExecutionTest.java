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
package org.kie.kogito.jobs.service.repository.postgresql;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipient;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipientStringPayloadData;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.Recipient;
import org.kie.kogito.jobs.service.model.RecipientInstance;
import org.kie.kogito.jobs.service.repository.ReactiveJobRepository;
import org.kie.kogito.jobs.service.repository.marshaller.RecipientMarshaller;
import org.kie.kogito.jobs.service.repository.marshaller.TriggerMarshaller;
import org.kie.kogito.jobs.service.utils.DateUtil;
import org.kie.kogito.timer.Trigger;
import org.kie.kogito.timer.impl.PointInTimeTrigger;
import org.mockito.ArgumentCaptor;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.MultiOnItem;
import io.smallrye.mutiny.groups.UniConvert;
import io.smallrye.mutiny.groups.UniOnItem;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({ "unchecked", "rawtypes" })
class PostgreSqlJobRepositoryExecutionTest {

    public static final String PAYLOAD_TEST = "{\"payload\": \"test\"}";
    public static final String URL = "test";
    public static final String JOB_DETAILS = "job_details";
    PostgreSqlJobRepository repository;

    PgPool client;

    PreparedQuery<RowSet<Row>> query;

    CompletableFuture completableFuture;

    ZonedDateTime lastUpdateTime;

    ZonedDateTime fireTime;

    @BeforeEach
    void setUp() {
        lastUpdateTime = ZonedDateTime.parse("2023-03-23T13:05:55.000Z");
        fireTime = ZonedDateTime.parse("2023-03-24T14:06:56.000Z");

        client = mock(PgPool.class);
        query = mock(PreparedQuery.class);
        when(client.preparedQuery(anyString())).thenReturn(query);
        when(client.query(anyString())).thenReturn(query);

        Uni uni = mock(Uni.class);
        when(query.execute(any(Tuple.class))).thenReturn(uni);
        when(query.execute()).thenReturn(uni);
        UniOnItem uniOnItem = mock(UniOnItem.class);
        when(uni.onItem()).thenReturn(uniOnItem);
        when(uniOnItem.transform(any(Function.class))).thenReturn(uni);
        when(uni.emitOn(any(Executor.class))).thenReturn(uni);

        Multi multi = mock(Multi.class);
        when(uniOnItem.transformToMulti(any(Function.class))).thenReturn(multi);
        MultiOnItem multiOnItem = mock(MultiOnItem.class);
        when(multi.onItem()).thenReturn(multiOnItem);
        when(multiOnItem.transform(any(Function.class))).thenReturn(multi);
        when(multi.emitOn(any(Executor.class))).thenReturn(multi);

        completableFuture = mock(CompletableFuture.class);
        UniConvert convert = mock(UniConvert.class);
        when(uni.convert()).thenReturn(convert);
        when(convert.toCompletableFuture()).thenReturn(completableFuture);

        TriggerMarshaller triggerMarshaller = mock(TriggerMarshaller.class);
        when(triggerMarshaller.marshall(any(Trigger.class))).thenReturn(new JsonObject().put("triggerMarshaller", "test"));
        when(triggerMarshaller.unmarshall(any(JsonObject.class))).thenReturn(new PointInTimeTrigger(fireTime.toInstant().toEpochMilli(), null, null));
        RecipientMarshaller recipientMarshaller = mock(RecipientMarshaller.class);
        when(recipientMarshaller.marshall(any(Recipient.class))).thenReturn(new JsonObject().put("recipientMarshaller", "test"));
        when(recipientMarshaller.unmarshall(any(JsonObject.class)))
                .thenReturn(new RecipientInstance(HttpRecipient.builder().forStringPayload().url(URL).payload(HttpRecipientStringPayloadData.from(PAYLOAD_TEST)).build()));

        repository = new PostgreSqlJobRepository(null, null, client, triggerMarshaller, recipientMarshaller);
    }

    @Test
    void doSave() {
        PointInTimeTrigger trigger = new PointInTimeTrigger(fireTime.toInstant().toEpochMilli(), null, null);
        Recipient recipient = new RecipientInstance(HttpRecipient.builder().forStringPayload().url(URL).payload(HttpRecipientStringPayloadData.from(PAYLOAD_TEST)).build());

        JobDetails job = JobDetails.builder()
                .id("test")
                .correlationId("test")
                .status(JobStatus.SCHEDULED)
                .lastUpdate(lastUpdateTime)
                .retries(1)
                .executionCounter(1)
                .scheduledId("test")
                .priority(1)
                .recipient(recipient)
                .trigger(trigger)
                .executionTimeout(4L)
                .executionTimeoutUnit(ChronoUnit.MINUTES)
                .build();

        CompletionStage<JobDetails> result = repository.doSave(job);
        assertThat(result).isEqualTo(completableFuture);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Tuple> parameterCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());
        verify(query, times(1)).execute(parameterCaptor.capture());

        String query = "INSERT INTO " + JOB_DETAILS + " (id, correlation_id, status, last_update, retries, execution_counter, scheduled_id, " +
                "priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created) VALUES ($1, $2, $3, now(), $4, $5, $6, $7, $8, $9, $10, $11, $12, now()) " +
                "ON CONFLICT (id) DO UPDATE SET correlation_id = $2, status = $3, last_update = now(), retries = $4, " +
                "execution_counter = $5, scheduled_id = $6, priority = $7, " +
                "recipient = $8, trigger = $9, fire_time = $10, execution_timeout = $11, execution_timeout_unit = $12 RETURNING id, correlation_id, status, last_update, retries, " +
                "execution_counter, scheduled_id, priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created";

        Tuple parameter = Tuple.tuple(Stream.of(
                job.getId(),
                job.getCorrelationId(),
                job.getStatus().name(),
                job.getRetries(),
                job.getExecutionCounter(),
                job.getScheduledId(),
                job.getPriority(),
                new JsonObject().put("recipientMarshaller", "test"),
                new JsonObject().put("triggerMarshaller", "test"),
                fireTime.toOffsetDateTime(),
                job.getExecutionTimeout(),
                job.getExecutionTimeoutUnit().name())
                .collect(toList()));

        assertThat(queryCaptor.getValue()).isEqualTo(query);
        assertThat(parameterCaptor.getValue().getString(0)).isEqualTo(parameter.getString(0));
        assertThat(parameterCaptor.getValue().getString(1)).isEqualTo(parameter.getString(1));
        assertThat(parameterCaptor.getValue().getString(2)).isEqualTo(parameter.getString(2));
        assertThat(parameterCaptor.getValue().getInteger(3)).isEqualTo(parameter.getInteger(3));
        assertThat(parameterCaptor.getValue().getInteger(4)).isEqualTo(parameter.getInteger(4));
        assertThat(parameterCaptor.getValue().getString(5)).isEqualTo(parameter.getString(5));
        assertThat(parameterCaptor.getValue().getInteger(6)).isEqualTo(parameter.getInteger(6));
        assertThat(parameterCaptor.getValue().getJson(7)).isEqualTo(parameter.getJson(7));
        assertThat(parameterCaptor.getValue().getJson(8)).isEqualTo(parameter.getJson(8));
        assertThat(parameterCaptor.getValue().getOffsetDateTime(9)).isEqualTo(parameter.getOffsetDateTime(9));
        assertThat(parameterCaptor.getValue().getJson(10)).isEqualTo(parameter.getJson(10));
        assertThat(parameterCaptor.getValue().getJson(11)).isEqualTo(parameter.getJson(11));
    }

    @Test
    void get() {
        CompletionStage<JobDetails> result = repository.get("test");
        assertThat(result).isEqualTo(completableFuture);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Tuple> parameterCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());
        verify(query, times(1)).execute(parameterCaptor.capture());

        String query = "SELECT id, correlation_id, status, last_update, retries, execution_counter, scheduled_id, " +
                "priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created FROM " + JOB_DETAILS + " WHERE id = $1";
        String parameter = "test";

        assertThat(queryCaptor.getValue()).isEqualTo(query);
        assertThat(parameterCaptor.getValue().getValue(0)).isEqualTo(parameter);
    }

    @Test
    void exists() {
        CompletionStage<Boolean> result = repository.exists("test");
        assertThat(result).isEqualTo(completableFuture);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Tuple> parameterCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());
        verify(query, times(1)).execute(parameterCaptor.capture());

        String query = "SELECT id FROM " + JOB_DETAILS + " WHERE id = $1";
        String parameter = "test";

        assertThat(queryCaptor.getValue()).isEqualTo(query);
        assertThat(parameterCaptor.getValue().getValue(0)).isEqualTo(parameter);
    }

    @Test
    void delete() {
        CompletionStage<JobDetails> result = repository.delete("test");
        assertThat(result).isEqualTo(completableFuture);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Tuple> parameterCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());
        verify(query, times(1)).execute(parameterCaptor.capture());

        String query = "DELETE FROM " + JOB_DETAILS + " WHERE id = $1 " +
                "RETURNING id, correlation_id, status, last_update, retries, " +
                "execution_counter, scheduled_id, priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created";
        String parameter = "test";

        assertThat(queryCaptor.getValue()).isEqualTo(query);
        assertThat(parameterCaptor.getValue().getValue(0)).isEqualTo(parameter);
    }

    @Test
    void findByStatusBetweenDates() {
        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = ZonedDateTime.now();

        PublisherBuilder<JobDetails> result = repository.findByStatusBetweenDates(from, to,
                new JobStatus[] { JobStatus.SCHEDULED, JobStatus.RETRY },
                new ReactiveJobRepository.SortTerm[] { ReactiveJobRepository.SortTerm.byFireTime(true) });
        assertThat(result).isNotNull();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());

        String query = "SELECT id, correlation_id, status, last_update, retries, execution_counter, scheduled_id, " +
                "priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created FROM " + JOB_DETAILS + " " +
                "WHERE status IN ('SCHEDULED', 'RETRY') AND fire_time BETWEEN $1 AND $2 ORDER BY fire_time ASC";

        assertThat(queryCaptor.getValue()).isEqualTo(query);
    }

    @Test
    void findByStatusBetweenDatesNoStatusCondition() {
        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = ZonedDateTime.now();

        PublisherBuilder<JobDetails> result = repository.findByStatusBetweenDates(from, to,
                new JobStatus[] {},
                new ReactiveJobRepository.SortTerm[] { ReactiveJobRepository.SortTerm.byFireTime(false) });
        assertThat(result).isNotNull();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).preparedQuery(queryCaptor.capture());

        String query = "SELECT id, correlation_id, status, last_update, retries, execution_counter, scheduled_id, " +
                "priority, recipient, trigger, fire_time, execution_timeout, execution_timeout_unit, created FROM " + JOB_DETAILS + " " +
                "WHERE fire_time BETWEEN $1 AND $2 ORDER BY fire_time DESC";

        assertThat(queryCaptor.getValue()).isEqualTo(query);
    }

    @Test
    void createStatusQuery() {
        String statusQuery = PostgreSqlJobRepository.createStatusFilter(JobStatus.SCHEDULED, JobStatus.RETRY);
        assertThat(statusQuery).isEqualTo("status IN ('SCHEDULED', 'RETRY')");
    }

    @Test
    void createTimeQuery() {
        String timeQuery = PostgreSqlJobRepository.createFireTimeFilter("$1", "$2");
        assertThat(timeQuery).isEqualTo("fire_time BETWEEN $1 AND $2");
    }

    @Test
    void from() {
        PointInTimeTrigger trigger = new PointInTimeTrigger(fireTime.toInstant().toEpochMilli(), null, null);
        Recipient recipient = new RecipientInstance(HttpRecipient.builder().forStringPayload().url(URL).payload(HttpRecipientStringPayloadData.from(PAYLOAD_TEST)).build());

        Row row = mock(Row.class);
        when(row.getString("id")).thenReturn("test");
        when(row.getString("correlation_id")).thenReturn("test");
        when(row.getString("status")).thenReturn("SCHEDULED");
        when(row.getOffsetDateTime("last_update")).thenReturn(lastUpdateTime.toOffsetDateTime());
        when(row.getInteger("retries")).thenReturn(1);
        when(row.getInteger("execution_counter")).thenReturn(1);
        when(row.getString("scheduled_id")).thenReturn("test");
        when(row.getInteger("priority")).thenReturn(1);
        when(row.get(JsonObject.class, "recipient")).thenReturn(new JsonObject().put("recipientMarshaller", "test"));
        when(row.get(JsonObject.class, "trigger")).thenReturn(new JsonObject().put("triggerMarshaller", "test"));
        when(row.getLong("execution_timeout")).thenReturn(3L);
        when(row.getString("execution_timeout_unit")).thenReturn(ChronoUnit.SECONDS.name());

        JobDetails jobDetails = repository.from(row);

        JobDetails expected = JobDetails.builder()
                .id("test")
                .correlationId("test")
                .status(JobStatus.SCHEDULED)
                .lastUpdate(lastUpdateTime.toOffsetDateTime().atZoneSameInstant(DateUtil.DEFAULT_ZONE))
                .retries(1)
                .executionCounter(1)
                .scheduledId("test")
                .priority(1)
                .recipient(recipient)
                .trigger(trigger)
                .executionTimeout(3L)
                .executionTimeoutUnit(ChronoUnit.SECONDS)
                .build();

        assertThat(jobDetails).isEqualTo(expected);
    }
}
