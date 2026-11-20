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
package org.kie.kogito.persistence.mongodb.index;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.api.schema.AttributeDescriptor;
import org.kie.kogito.persistence.api.schema.EntityIndexDescriptor;
import org.kie.kogito.persistence.api.schema.IndexDescriptor;
import org.kie.kogito.persistence.api.schema.ProcessDescriptor;
import org.kie.kogito.persistence.api.schema.SchemaDescriptor;
import org.kie.kogito.persistence.api.schema.SchemaRegisteredEvent;
import org.kie.kogito.persistence.api.schema.SchemaRegistrationException;
import org.kie.kogito.persistence.api.schema.SchemaType;
import org.kie.kogito.persistence.mongodb.mock.MockProcessIndexEventListener;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.kie.kogito.persistence.mongodb.index.IndexManager.DEFAULT_INDEX;
import static org.kie.kogito.persistence.mongodb.index.IndexManager.INDEX_NAME_FIELD;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class IndexManagerIT {

    @Inject
    MockProcessIndexEventListener mockProcessIndexEventListener;

    @Inject
    IndexManager indexManager;

    List<MongoCollection> collections = new LinkedList<>();

    private static EntityIndexDescriptor travelEntityIndexDescriptor;
    private static EntityIndexDescriptor flightEntityIndexDescriptor;
    private static EntityIndexDescriptor hotelEntityIndexDescriptor;
    private static EntityIndexDescriptor errorEntityIndexDescriptor;

    @BeforeAll
    static void setup_all() {
        AttributeDescriptor flightNumber = new AttributeDescriptor("flightNumber", "string", true);
        IndexDescriptor flightNumberIndex = new IndexDescriptor("flightNumber", List.of("flightNumber"));
        flightEntityIndexDescriptor = new EntityIndexDescriptor("org.acme.travels.travels.Flight", List.of(flightNumberIndex), List.of(flightNumber));

        AttributeDescriptor hotelName = new AttributeDescriptor("name", "string", true);
        AttributeDescriptor hotelRoom = new AttributeDescriptor("room", "string", true);
        IndexDescriptor hotelNameIndex = new IndexDescriptor("name", List.of("name"));
        hotelEntityIndexDescriptor = new EntityIndexDescriptor("org.acme.travels.travels.Hotel", List.of(hotelNameIndex), List.of(hotelName, hotelRoom));

        AttributeDescriptor flight = new AttributeDescriptor("flight", "Flight", false);
        AttributeDescriptor hotel = new AttributeDescriptor("hotel", "org.acme.travels.travels.Hotel", false);
        AttributeDescriptor id = new AttributeDescriptor("id", "string", true);
        AttributeDescriptor metadata = new AttributeDescriptor("metadata", "string", true);
        IndexDescriptor flightIndex = new IndexDescriptor("flight", List.of("flight"));
        IndexDescriptor hotelIndex = new IndexDescriptor("hotel", List.of("hotel"));
        IndexDescriptor idIndex = new IndexDescriptor("id", List.of("id"));
        IndexDescriptor metadataIndex = new IndexDescriptor("metadata", List.of("metadata"));
        travelEntityIndexDescriptor = new EntityIndexDescriptor("org.acme.travels.travels.Travels",
                List.of(flightIndex, hotelIndex, idIndex, metadataIndex),
                List.of(flight, hotel, id, metadata));

        errorEntityIndexDescriptor = mockErrorIndexes();
    }

    @BeforeEach
    void setup() {
        indexManager.getIndexes().put(flightEntityIndexDescriptor.getName(), flightEntityIndexDescriptor);
        indexManager.getIndexes().put(hotelEntityIndexDescriptor.getName(), hotelEntityIndexDescriptor);
        indexManager.getIndexes().put(travelEntityIndexDescriptor.getName(), travelEntityIndexDescriptor);

        mockProcessIndexEventListener.reset();
    }

    @AfterEach
    void tearDown() {
        mockProcessIndexEventListener.reset();

        indexManager.getCollectionIndexMapping().clear();
        indexManager.getIndexes().clear();

        collections.forEach(MongoCollection::drop);
        collections.clear();
    }

    @Test
    void testOnSchemaRegisteredEvent() {
        Map<String, EntityIndexDescriptor> indexes = new HashMap<>();
        indexes.put("test", travelEntityIndexDescriptor);

        indexManager.getCollectionIndexMapping().put("test", travelEntityIndexDescriptor.getName());

        indexManager.onSchemaRegisteredEvent(
                new SchemaRegisteredEvent(new SchemaDescriptor("test", "test", indexes,
                        new ProcessDescriptor("test", travelEntityIndexDescriptor.getName())),
                        new SchemaType("test")));

        MongoCollection<Document> testCollection = indexManager.getCollection("test");
        collections.add(testCollection);

        Set<String> testIndexes = StreamSupport.stream(testCollection.listIndexes().spliterator(), false)
                .map(document -> document.getString(INDEX_NAME_FIELD)).filter(name -> !DEFAULT_INDEX.equals(name)).collect(toSet());
        assertThat(testIndexes).isEqualTo(getTestIndexNames());

        mockProcessIndexEventListener.assertFire("test", travelEntityIndexDescriptor.getName());
    }

    @Test
    void testOnIndexCreateOrUpdateEvent() {
        IndexCreateOrUpdateEvent event = new IndexCreateOrUpdateEvent("test", travelEntityIndexDescriptor.getName());
        indexManager.onIndexCreateOrUpdateEvent(event);

        MongoCollection<Document> testCollection = indexManager.getCollection("test");
        collections.add(testCollection);

        Set<String> indexes = StreamSupport.stream(testCollection.listIndexes().spliterator(), false)
                .map(document -> document.getString(INDEX_NAME_FIELD)).filter(name -> !DEFAULT_INDEX.equals(name)).collect(toSet());
        assertThat(indexes).isEqualTo(getTestIndexNames());
    }

    @Test
    void testUpdateIndexes() {
        indexManager.getCollectionIndexMapping().put("test1", travelEntityIndexDescriptor.getName());
        indexManager.getCollectionIndexMapping().put("test2", travelEntityIndexDescriptor.getName());

        indexManager.updateIndexes(List.of(travelEntityIndexDescriptor, hotelEntityIndexDescriptor, flightEntityIndexDescriptor));

        MongoCollection<Document> testCollection1 = indexManager.getCollection("test1");
        MongoCollection<Document> testCollection2 = indexManager.getCollection("test2");
        collections.add(testCollection1);
        collections.add(testCollection2);

        Set<String> indexes1 = StreamSupport.stream(testCollection1.listIndexes().spliterator(), false)
                .map(document -> document.getString(INDEX_NAME_FIELD)).filter(name -> !DEFAULT_INDEX.equals(name)).collect(toSet());
        assertThat(indexes1).isEqualTo(getTestIndexNames());
        Set<String> indexes2 = StreamSupport.stream(testCollection2.listIndexes().spliterator(), false)
                .map(document -> document.getString(INDEX_NAME_FIELD)).filter(name -> !DEFAULT_INDEX.equals(name)).collect(toSet());
        assertThat(indexes2).isEqualTo(getTestIndexNames());
    }

    @Test
    void testUpdateCollection() {
        MongoCollection<Document> collection = indexManager.getCollection("test");
        collections.add(collection);

        collection.createIndex(Indexes.ascending("test"), new IndexOptions().name("test"));

        indexManager.updateCollection(collection, travelEntityIndexDescriptor);
        Set<String> indexes = StreamSupport.stream(collection.listIndexes().spliterator(), false)
                .map(document -> document.getString(INDEX_NAME_FIELD)).filter(name -> !DEFAULT_INDEX.equals(name)).collect(toSet());
        assertThat(indexes).isEqualTo(getTestIndexNames());
    }

    @Test
    void testUpdateCollectionTooManyIndexes() {
        MongoCollection<Document> collection = indexManager.getCollection("test");
        collections.add(collection);

        collection.createIndex(Indexes.ascending("test"), new IndexOptions().name("test"));

        try {
            indexManager.updateCollection(collection, errorEntityIndexDescriptor);
            fail("Exception for creating too many indexes was not thrown");
        } catch (SchemaRegistrationException ex) {
            assertThat(ex.getMessage().contains("no more than 64 indexes")).isTrue();
        }
    }

    @Test
    void testCreateIndexForEntity() {
        List<IndexModel> indexes = indexManager.createIndexForEntity("", travelEntityIndexDescriptor);

        assertThat(equalsIndexModels(indexes, getTestIndexModels())).isTrue();
    }

    @Test
    void testCreateSingleIndex() {
        String fieldName = "id";
        IndexDescriptor id = new IndexDescriptor(fieldName, List.of(fieldName));
        String parentField = "";
        String prefixUUID = UUID.randomUUID().toString() + ".";

        Optional<IndexModel> index = indexManager.createIndex(id, parentField, prefixUUID);

        assertThat(equalsIndexModels(List.of(index.get()), List.of(new IndexModel(Indexes.ascending(fieldName), new IndexOptions().name(prefixUUID + fieldName).sparse(true))))).isTrue();
    }

    @Test
    void testCreateNoIndex() {
        String fieldName = "id";
        IndexDescriptor id = new IndexDescriptor(fieldName, List.of());
        String parentField = "";
        String prefixUUID = UUID.randomUUID().toString() + ".";

        Optional<IndexModel> index = indexManager.createIndex(id, parentField, prefixUUID);

        assertThat(index.isPresent()).isFalse();
    }

    @Test
    void testCreateCompoundIndex() {
        String indexName = "test";
        IndexDescriptor id = new IndexDescriptor(indexName, List.of("test1", "test2"));
        String parentField = "test";
        String prefixUUID = UUID.randomUUID().toString() + ".";

        Optional<IndexModel> index = indexManager.createIndex(id, parentField, prefixUUID);

        assertThat(equalsIndexModels(List.of(index.get()),
                List.of(new IndexModel(Indexes.ascending(indexName + ".test1", indexName + ".test2"), new IndexOptions().name(prefixUUID + indexName).sparse(true))))).isTrue();
    }

    @Test
    void testGetCollectionsWithIndex() {
        indexManager.getCollectionIndexMapping().put("collection1", "index1");
        indexManager.getCollectionIndexMapping().put("collection2", "index1");
        indexManager.getCollectionIndexMapping().put("collection3", "index1");
        indexManager.getCollectionIndexMapping().put("collection4", "index2");
        indexManager.getCollectionIndexMapping().put("collection5", "index2");

        List<String> index1Collections = indexManager.getCollectionsWithIndex("index1");
        List<String> index2Collections = indexManager.getCollectionsWithIndex("index2");
        List<String> index3Collections = indexManager.getCollectionsWithIndex("index3");

        Set<String> expectedIndex1Collections = new HashSet<>();
        expectedIndex1Collections.add("collection1");
        expectedIndex1Collections.add("collection2");
        expectedIndex1Collections.add("collection3");

        Set<String> expectedIndex2Collections = new HashSet<>();
        expectedIndex2Collections.add("collection4");
        expectedIndex2Collections.add("collection5");

        assertThat(new HashSet<>(index1Collections)).isEqualTo(expectedIndex1Collections);
        assertThat(new HashSet<>(index2Collections)).isEqualTo(expectedIndex2Collections);
        assertThat(index3Collections.isEmpty()).isTrue();
    }

    private List<IndexModel> getTestIndexModels() {
        return List.of(
                new IndexModel(Indexes.ascending("flight"), new IndexOptions().name("d41d8cd9-8f00-3204-a980-0998ecf8427e.flight").sparse(true)),
                new IndexModel(Indexes.ascending("flight.flightNumber"), new IndexOptions().name("e325b16a-a10b-32b0-a574-2595902073cb.flightNumber").sparse(true)),
                new IndexModel(Indexes.ascending("hotel"), new IndexOptions().name("d41d8cd9-8f00-3204-a980-0998ecf8427e.hotel").sparse(true)),
                new IndexModel(Indexes.ascending("hotel.name"), new IndexOptions().name("e919c49d-5f0c-3737-a853-67810a3394d0.name").sparse(true)),
                new IndexModel(Indexes.ascending("id"), new IndexOptions().name("d41d8cd9-8f00-3204-a980-0998ecf8427e.id").sparse(true)),
                new IndexModel(Indexes.ascending("metadata"), new IndexOptions().name("d41d8cd9-8f00-3204-a980-0998ecf8427e.metadata").sparse(true)));
    }

    private Set<String> getTestIndexNames() {
        return getTestIndexModels().stream().map(indexModel -> indexModel.getOptions().getName()).collect(toSet());
    }

    private boolean equalsIndexModels(List<IndexModel> indexModels1, List<IndexModel> indexModels2) {
        Map<String, Bson> indexModelMapping1 = new HashMap<>();
        indexModels1.forEach(indexModel -> indexModelMapping1.put(indexModel.getOptions().getName(), indexModel.getKeys()));

        Map<String, Bson> indexModelMapping2 = new HashMap<>();
        indexModels2.forEach(indexModel -> indexModelMapping2.put(indexModel.getOptions().getName(), indexModel.getKeys()));

        return indexModelMapping1.equals(indexModelMapping2);
    }

    private static EntityIndexDescriptor mockErrorIndexes() {
        List<IndexDescriptor> indexDescriptors = IntStream.rangeClosed(0, 75).mapToObj(i -> new IndexDescriptor("test" + i, List.of("test" + i))).collect(toList());
        List<AttributeDescriptor> attributeDescriptors = IntStream.rangeClosed(0, 75).mapToObj(i -> new AttributeDescriptor("test" + i, "string", true)).collect(toList());
        return new EntityIndexDescriptor("org.acme.travels.travels.Travels", indexDescriptors, attributeDescriptors);
    }
}
