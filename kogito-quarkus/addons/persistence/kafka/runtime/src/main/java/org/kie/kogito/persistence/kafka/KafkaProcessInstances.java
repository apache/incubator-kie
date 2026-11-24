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
package org.kie.kogito.persistence.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerService;
import org.kie.kogito.Model;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceDuplicatedException;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.rocksdb.RocksDBException;

import static java.lang.String.format;
import static java.util.stream.Collectors.toCollection;
import static org.kie.kogito.persistence.kafka.KafkaPersistenceUtils.topicName;

public class KafkaProcessInstances<T extends Model> implements MutableProcessInstances<T> {
    private final String EVENT_SEPARATOR = "::";
    private Process<?> process;
    private KafkaProducer<String, byte[]> producer;
    private String topic;
    private ReadOnlyKeyValueStore<String, byte[]> store;
    private ProcessInstanceMarshallerService marshaller;
    private CountDownLatch latch = new CountDownLatch(1);

    public KafkaProcessInstances(Process<?> process, KafkaProducer<String, byte[]> producer) {
        this.process = process;
        this.topic = topicName();
        this.producer = producer;
        setMarshaller(ProcessInstanceMarshallerService.newBuilder().withDefaultObjectMarshallerStrategies().build());
    }

    protected Process<?> getProcess() {
        return process;
    }

    protected ReadOnlyKeyValueStore<String, byte[]> getStore() {
        if (store != null) {
            return store;
        }

        return getStoreAwait();
    }

    protected void setStore(ReadOnlyKeyValueStore<String, byte[]> store) {
        this.store = store;
        this.latch.countDown();
    }

    private ReadOnlyKeyValueStore<String, byte[]> getStoreAwait() {
        try {
            if (latch.await(1, TimeUnit.MINUTES)) {
                if (store == null) {
                    throw new RuntimeException("Failed to obtain Kafka Store for process: " + process.id());
                } else {
                    return store;
                }
            } else {
                throw new RuntimeException("Timeout waiting to obtain Kafka Store for process: " + process.id());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain Kafka Store for process: " + process.id(), e);
        }
    }

    protected void setMarshaller(ProcessInstanceMarshallerService marshaller) {
        this.marshaller = marshaller;
    }

    @Override
    public boolean exists(String id) {
        return getProcessInstanceById(id).isPresent();
    }

    protected Optional<byte[]> getProcessInstanceById(String id) {
        return Optional.ofNullable(getStore().get(getKeyForProcessInstance(id)));
    }

    protected String getKeyForProcessInstance(String id) {
        return format("process-%s-%s", getProcess().id(), id);
    }

    protected String getKeyForEvents() {
        return format("events-%s", getProcess().id(), "events");
    }

    protected void sendKafkaRecord(String id, byte[] data) throws ExecutionException, InterruptedException {
        producer.send(new ProducerRecord<>(topic, getKeyForProcessInstance(id), data)).get();
    }

    protected void sendEventKafkaRecord(byte[] data) throws ExecutionException, InterruptedException {
        producer.send(new ProducerRecord<>(topic, getKeyForEvents(), data)).get();
    }

    @Override
    public void create(String id, ProcessInstance<T> instance) {
        if (isActive(instance)) {
            if (getProcessInstanceById(id).isPresent()) {
                throw new ProcessInstanceDuplicatedException(id);
            }
            try {
                sendKafkaRecord(id, marshaller.marshallProcessInstance(instance));
                updateEvents(instance);
                connectInstance(instance);
            } catch (Exception e) {
                throw new RuntimeException("Unable to persist process instance id: " + id, e);
            }
        }
    }

    public void updateEvents(ProcessInstance<T> instance) throws Exception {
        Set<String> eventTypes = clearEventTypes(instance.id());
        eventTypes.addAll(getUniqueEvents(instance));
        sendEventKafkaRecord(toBytes(eventTypes));
    }

    @Override
    public void update(String id, ProcessInstance<T> instance) {
        if (isActive(instance)) {
            byte[] data = marshaller.marshallProcessInstance(instance);
            try {
                sendKafkaRecord(id, data);
                updateEvents(instance);
                connectInstance(instance);
            } catch (Exception e) {
                throw new RuntimeException("Unable to update process instance id: " + id, e);
            }
        }
    }

    @Override
    public void remove(String id) {
        try {
            sendKafkaRecord(id, null);
            clearEvents(id);
            // this avoids generates a race condition as one thing is send to kafka and the other is to be processed by the table.
            while (exists(id)) {
                Thread.sleep(100L);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to remove process instance id: " + id, e);
        }
    }

    public void clearEvents(String id) throws Exception {
        Set<String> eventTypes = clearEventTypes(id);
        sendEventKafkaRecord(toBytes(eventTypes));
    }

    @Override
    public Optional<ProcessInstance<T>> findById(String id, ProcessInstanceReadMode mode) {
        return getProcessInstanceById(id).map(r -> {
            AbstractProcessInstance<T> pi = (AbstractProcessInstance<T>) marshaller.unmarshallProcessInstance(r, process, mode);
            connectInstance(pi);
            return pi;
        });
    }

    @Override
    public Stream<ProcessInstance<T>> stream(ProcessInstanceReadMode mode) {
        KeyValueIterator<String, byte[]> iterator = getStore().prefixScan("process-" + getProcess().id(), Serdes.String().serializer());
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .map(k -> k.value)
                .map(data -> {
                    AbstractProcessInstance<T> pi = (AbstractProcessInstance) marshaller.unmarshallProcessInstance(data, process, mode);
                    connectInstance(pi);
                    return (ProcessInstance<T>) pi;
                })
                .onClose(iterator::close);
    }

    protected void connectInstance(ProcessInstance<?> instance) {
        if (instance == null) {
            return;
        }
        ((AbstractProcessInstance<?>) instance).internalSetReloadSupplier(marshaller.createdReloadFunction(() -> getProcessInstanceById(instance.id()).orElseThrow()));
    }

    @Override
    public Stream<ProcessInstance<T>> waitingForEventType(String eventType, ProcessInstanceReadMode mode) {

        byte[] eventData = getStore().get(getKeyForEvents());
        if (eventData == null) {
            return Collections.<ProcessInstance<T>> emptyList().stream();
        }
        String list = new String(eventData);
        List<String> processInstancesId = Stream.of(list.split(","))
                .filter(e -> e.startsWith(eventType + EVENT_SEPARATOR))
                .map(e -> e.substring(e.indexOf(EVENT_SEPARATOR) + EVENT_SEPARATOR.length()))
                .toList();

        List<ProcessInstance<T>> waitingInstances = new ArrayList<>();
        for (String processInstanceId : processInstancesId) {
            byte[] data = getStore().get(getKeyForProcessInstance(processInstanceId));
            AbstractProcessInstance<T> pi = (AbstractProcessInstance) marshaller.unmarshallProcessInstance(data, process, mode);
            connectInstance(pi);
            waitingInstances.add(pi);
        }
        return waitingInstances.stream();

    }

    private byte[] toBytes(Set<String> events) {
        return String.join(",", events).getBytes();
    }

    private Set<String> clearEventTypes(String processInstanceId) throws RocksDBException {
        byte[] eventData = getStore().get(getKeyForEvents());
        if (eventData == null) {
            return new HashSet<>();
        }
        String list = new String(eventData);
        return Stream.of(list.split(",")).filter(e -> !e.endsWith(EVENT_SEPARATOR + processInstanceId)).collect(toCollection(HashSet::new));
    }

    private Set<String> getUniqueEvents(ProcessInstance<T> instance) {
        return Stream.of(((AbstractProcessInstance<T>) instance).internalGetProcessInstance().getEventTypes())
                .map(e -> e + EVENT_SEPARATOR + instance.id())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
