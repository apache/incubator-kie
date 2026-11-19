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
package org.kie.kogito.jobs.service.repository.mongodb;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipient;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipientStringPayloadData;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobDetailsBuilder;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.Recipient;
import org.kie.kogito.jobs.service.model.RecipientInstance;
import org.kie.kogito.jobs.service.repository.ReactiveJobRepository;
import org.kie.kogito.jobs.service.repository.marshaller.JobDetailsMarshaller;
import org.kie.kogito.jobs.service.repository.marshaller.RecipientMarshaller;
import org.kie.kogito.jobs.service.repository.marshaller.TriggerMarshaller;
import org.kie.kogito.jobs.service.repository.mongodb.marshaller.MongoDBJobDetailsMarshaller;
import org.kie.kogito.jobs.service.utils.DateUtil;
import org.kie.kogito.timer.Trigger;
import org.kie.kogito.timer.impl.PointInTimeTrigger;
import org.mockito.ArgumentCaptor;

import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.FindPublisher;

import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.UniAwait;
import io.vertx.core.json.JsonObject;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Indexes.ascending;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.jobs.service.repository.mongodb.MongoDBJobRepository.CREATED_COLUMN;
import static org.kie.kogito.jobs.service.repository.mongodb.MongoDBJobRepository.FIRE_TIME_COLUMN;
import static org.kie.kogito.jobs.service.repository.mongodb.MongoDBJobRepository.ID;
import static org.kie.kogito.jobs.service.repository.mongodb.MongoDBJobRepository.STATUS_COLUMN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({ "unchecked", "rawtypes" })
class MongoDBJobRepositoryExecutionTest {

    private static final String JOB_ID = "JOB_ID";

    private MongoDBJobRepository mongoDBJobRepository;

    private ReactiveMongoCollection<Document> collection;

    private JobDetailsMarshaller jobDetailsMarshaller;

    @BeforeEach
    void setUp() {
        ReactiveMongoClient mongoClient = mock(ReactiveMongoClient.class);
        collection = mock(ReactiveMongoCollection.class);
        ReactiveMongoDatabase mongoDatabase = mock(ReactiveMongoDatabase.class);
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection(anyString())).thenReturn(collection);
        jobDetailsMarshaller = spy(new MongoDBJobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()));
        mongoDBJobRepository = new MongoDBJobRepository(null, null, mongoClient, "test", jobDetailsMarshaller);
    }

    @Test
    void saveExisting() throws Exception {
        doSave(createExistingJob(), true);
    }

    @Test
    void saveNotExisting() throws Exception {
        JobDetails notExisting = new JobDetailsBuilder()
                .id(JOB_ID)
                .trigger(createTrigger())
                .recipient(createRecipient())
                .build();
        doSave(notExisting, false);
    }

    private void doSave(JobDetails job, boolean exists) throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        Multi<Document> multi;
        if (exists) {
            Document document = Document.parse(new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()).marshall(job).toString());
            multi = Multi.createFrom().item(document);
        } else {
            multi = Multi.createFrom().empty();
        }
        when(collection.find(any(Bson.class))).thenReturn(multi);

        Document replaced = new Document().append("id", "replaced");
        Uni<Document> replacedDocument = Uni.createFrom().item(replaced);
        when(collection.findOneAndReplace(any(Bson.class), any(), any(FindOneAndReplaceOptions.class))).thenReturn(replacedDocument);

        CompletionStage<JobDetails> result = mongoDBJobRepository.doSave(job);
        JobDetails saved = result.toCompletableFuture().get();

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<FindOneAndReplaceOptions> optionCaptor = ArgumentCaptor.forClass(FindOneAndReplaceOptions.class);
        ArgumentCaptor<JobDetails> marshallCaptor = ArgumentCaptor.forClass(JobDetails.class);
        ArgumentCaptor<JsonObject> unmarshallCaptor = ArgumentCaptor.forClass(JsonObject.class);

        verify(collection, times(1)).findOneAndReplace(filterCaptor.capture(), documentCaptor.capture(), optionCaptor.capture());
        verify(jobDetailsMarshaller, times(1)).marshall(marshallCaptor.capture());
        verify(jobDetailsMarshaller).unmarshall(unmarshallCaptor.capture());

        assertThat(filterCaptor.getValue()).isEqualTo(eq(ID, job.getId()));
        assertThat(optionCaptor.getValue().isUpsert()).isTrue();
        assertThat(optionCaptor.getValue().getReturnDocument()).isEqualTo(ReturnDocument.AFTER);

        JobDetails timestampedJob = marshallCaptor.getAllValues().get(0);
        assertThat(timestampedJob.getId()).isEqualTo(job.getId());
        if (exists) {
            assertThat(timestampedJob.getCreated()).isEqualTo(job.getCreated());
            assertThat(timestampedJob.getLastUpdate()).isAfter(job.getLastUpdate());
        } else {
            assertThat(timestampedJob.getCreated()).isAfter(now);
            assertThat(timestampedJob.getLastUpdate()).isAfter(now);
        }
        JsonObject replacedAsJson = new JsonObject(replaced.toJson());
        assertThat(unmarshallCaptor.getValue()).isEqualTo(replacedAsJson);
        assertThat(saved.getId()).isEqualTo(replacedAsJson.getString("id"));
    }

    @Test
    void get() throws Exception {
        JobDetails job = createExistingJob();
        Document document = Document.parse(new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()).marshall(job).toString());
        Multi<Document> multi = Multi.createFrom().item(document);
        when(collection.find(any(Bson.class))).thenReturn(multi);

        JobDetails result = mongoDBJobRepository.get(job.getId()).toCompletableFuture().get();
        assertThat(result).isEqualTo(job);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<JsonObject> unmarshallCaptor = ArgumentCaptor.forClass(JsonObject.class);

        verify(collection, times(1)).find(filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(eq(ID, job.getId()));

        verify(jobDetailsMarshaller).unmarshall(unmarshallCaptor.capture());
        assertThat(unmarshallCaptor.getValue()).isEqualTo(new JsonObject(document.toJson()));
    }

    @Test
    void exists() throws Exception {
        JobDetails job = createExistingJob();
        Document document = Document.parse(new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()).marshall(job).toString());
        Multi<Document> multi = Multi.createFrom().item(document);
        when(collection.find(any(Bson.class))).thenReturn(multi);

        Boolean result = mongoDBJobRepository.exists(job.getId()).toCompletableFuture().get();
        assertThat(result).isTrue();

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(1)).find(filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(eq(ID, job.getId()));
    }

    @Test
    void delete() throws Exception {
        JobDetails job = createExistingJob();
        Document document = Document.parse(new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()).marshall(job).toString());
        Uni<Document> uni = Uni.createFrom().item(document);
        when(collection.findOneAndDelete(any(Bson.class))).thenReturn(uni);

        JobDetails result = mongoDBJobRepository.delete(job.getId()).toCompletableFuture().get();
        assertThat(result).isEqualTo(job);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<JsonObject> unmarshallCaptor = ArgumentCaptor.forClass(JsonObject.class);

        verify(collection, times(1)).findOneAndDelete(filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(eq(ID, job.getId()));

        verify(jobDetailsMarshaller).unmarshall(unmarshallCaptor.capture());
        assertThat(unmarshallCaptor.getValue()).isEqualTo(new JsonObject(document.toJson()));
    }

    @Test
    void findByStatusBetweenDates() {
        JobDetails job = createExistingJob();
        Document document = Document.parse(new JobDetailsMarshaller(new TriggerMarshaller(), new RecipientMarshaller()).marshall(job).toString());
        Multi<Document> multi = Multi.createFrom().item(document);
        doReturn(multi).when(collection).find(any(FindOptions.class));

        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = ZonedDateTime.now();

        PublisherBuilder<JobDetails> result = mongoDBJobRepository.findByStatusBetweenDates(from, to,
                new JobStatus[] { JobStatus.SCHEDULED, JobStatus.RETRY },
                new ReactiveJobRepository.SortTerm[] { ReactiveJobRepository.SortTerm.byFireTime(true) });
        assertThat(result).isNotNull();

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<FindOptions> optionCaptor = ArgumentCaptor.forClass(FindOptions.class);
        verify(collection, times(1)).find(optionCaptor.capture());

        FindPublisher findPublisher = mock(FindPublisher.class);
        doReturn(findPublisher).when(findPublisher).filter(any());

        optionCaptor.getValue().apply(findPublisher);
        verify(findPublisher).filter(filterCaptor.capture());

        assertThat(filterCaptor.getValue()).isEqualTo(and(
                in("status", Arrays.stream(new JobStatus[] { JobStatus.SCHEDULED, JobStatus.RETRY }).map(Enum::name).collect(toList())),
                gte("trigger.nextFireTime", from.toInstant().toEpochMilli()),
                lte("trigger.nextFireTime", to.toInstant().toEpochMilli())));
    }

    @Test
    void onStart() {
        Uni uni = mock(Uni.class);
        when(collection.createIndex(any())).thenReturn(uni);
        when(uni.await()).thenReturn(mock(UniAwait.class));

        mongoDBJobRepository.onStart(null);

        ArgumentCaptor<Bson> indexCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).createIndex(indexCaptor.capture());

        assertThat(indexCaptor.getAllValues().get(0)).isEqualTo(ascending(STATUS_COLUMN, FIRE_TIME_COLUMN));
        assertThat(indexCaptor.getAllValues().get(1)).isEqualTo(ascending(CREATED_COLUMN, FIRE_TIME_COLUMN, ID));
    }

    @Test
    void documentToJson() {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", 1618276009165L);
        JsonObject object = new JsonObject(objectMap);

        Document document = new Document()
                .append("testKey1", "testValue1")
                .append("testKey2", 1618276009165L);

        assertThat(MongoDBJobRepository.documentToJson(document)).isEqualTo(object);
    }

    @Test
    void jsonToDocument() {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", 1618276009165L);
        JsonObject object = new JsonObject(objectMap);

        Document document = new Document()
                .append("testKey1", "testValue1")
                .append("testKey2", 1618276009165L);

        assertThat(MongoDBJobRepository.jsonToDocument(object)).isEqualTo(document);
    }

    private static JobDetails createExistingJob() {
        ZonedDateTime createdTime = ZonedDateTime.parse("2024-01-30T12:00:00.000Z[UTC]");
        ZonedDateTime lastUpdateTime = ZonedDateTime.parse("2024-01-30T15:00:00.000Z[UTC]");
        return new JobDetailsBuilder()
                .id(JOB_ID)
                .trigger(createTrigger())
                .recipient(createRecipient())
                .created(createdTime)
                .lastUpdate(lastUpdateTime)
                .build();
    }

    private static Trigger createTrigger() {
        return new PointInTimeTrigger(DateUtil.now().toInstant().toEpochMilli(), null, null);
    }

    private static Recipient createRecipient() {
        return new RecipientInstance(HttpRecipient.builder()
                .forStringPayload()
                .url("http://my-service")
                .payload(HttpRecipientStringPayloadData.from("payload data"))
                .build());
    }
}
