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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.persistence.KafkaProcessInstancesFactory;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.bpmn2.BpmnProcess;
import org.kie.kogito.process.bpmn2.BpmnVariables;
import org.kie.kogito.process.bpmn2.StaticApplicationAssembler;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.kie.kogito.testcontainers.KogitoKafkaContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE;
import static org.kie.kogito.persistence.kafka.KafkaPersistenceUtils.createTopologyForProcesses;
import static org.kie.kogito.persistence.kafka.KafkaPersistenceUtils.topicName;
import static org.kie.kogito.process.ProcessInstance.STATE_COMPLETED;
import static org.kie.kogito.process.ProcessInstance.STATE_ERROR;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.abort;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertEmpty;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.getFirst;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.getFirstReadOnly;

@Testcontainers
public class KafkaProcessInstancesIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProcessInstancesIT.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    @Container
    KogitoKafkaContainer kafka = new KogitoKafkaContainer();

    KafkaProcessInstancesFactory factory;

    KafkaStreamsStateListener listener;

    @BeforeEach
    void start() {
        listener = new KafkaStreamsStateListener();
        factory = new KafkaProcessInstancesFactory();
        factory.setKafkaConfig(singletonMap(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
        factory.setStateListener(listener);
        createTopic();
    }

    // With Kafka Streams >= 4.2 the global table fails to initialize ("no partitions available") if its source topic
    // does not exist yet, so create it before the topology starts.
    private void createTopic() {
        String topic = topicName();
        try (Admin admin = Admin.create(singletonMap(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while creating Kafka topic '" + topic + "'", e);
        } catch (ExecutionException e) {
            fail("Unable to create Kafka topic '" + topic + "': " + e.getCause(), e.getCause());
        }
    }

    @AfterEach
    void stop() {
        if (factory != null) {
            factory.stop();
        }
        if (listener.getKafkaStreams() != null) {
            listener.getKafkaStreams().close();
            listener.getKafkaStreams().cleanUp();
        }
        listener.close();
    }

    private <T> void awaitTillOne(ProcessInstances<T> instances) {
        awaitTillSize(instances, 1);
    }

    private <T> void awaitTillEmpty(ProcessInstances<T> instances) {
        awaitTillSize(instances, 0);
    }

    private <T> void awaitTillSize(ProcessInstances<T> instances, int size) {
        await().atMost(TIMEOUT).until(() -> {
            try (Stream<ProcessInstance<T>> stream = instances.stream()) {
                return stream.count() == size;
            }
        });
    }

    private BpmnProcess createProcess(String fileName) {
        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler("Human Task", new DefaultKogitoWorkItemHandler())
                .build();

        Application application = StaticApplicationAssembler.instance().newStaticApplication(factory, processConfig, fileName);

        org.kie.kogito.process.Processes container = application.get(org.kie.kogito.process.Processes.class);
        String processId = container.processIds().stream().findFirst().get();
        org.kie.kogito.process.Process<? extends Model> process = container.processById(processId);

        abort(process.instances());
        return (BpmnProcess) process;
    }

    @Test
    void testFindByIdReadMode() {
        listener.setKafkaStreams(createStreams());
        listener.getKafkaStreams().start();
        BpmnProcess process = createProcess("BPMN2-UserTask-Script.bpmn2");

        ProcessInstances<BpmnVariables> instances = process.instances();
        assertEmpty(instances);

        ProcessInstance<BpmnVariables> mutablePi = process.createInstance(BpmnVariables.create(singletonMap("var", "value")));

        mutablePi.start();
        assertThat(mutablePi.status()).isEqualTo(STATE_ERROR);
        assertThat(mutablePi.error()).hasValueSatisfying(error -> {
            assertThat(error.errorMessage()).contains("java.lang.NullPointerException");
            assertThat(error.failedNodeId()).isEqualTo("ScriptTask_1");
        });
        assertThat(mutablePi.variables().toMap()).containsExactly(entry("var", "value"));

        ProcessInstance<BpmnVariables> pi = instances.findById(mutablePi.id(), ProcessInstanceReadMode.READ_ONLY).get();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> pi.abort());

        ProcessInstance<BpmnVariables> readOnlyPi = instances.findById(mutablePi.id(), ProcessInstanceReadMode.READ_ONLY).get();
        assertThat(readOnlyPi.status()).isEqualTo(STATE_ERROR);
        assertThat(readOnlyPi.error()).hasValueSatisfying(error -> {
            assertThat(error.errorMessage()).contains("java.lang.NullPointerException");
            assertThat(error.failedNodeId()).isEqualTo("ScriptTask_1");
        });
        assertThat(readOnlyPi.variables().toMap()).containsExactly(entry("var", "value"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> readOnlyPi.abort());

        awaitTillOne(instances);
        ProcessInstance<BpmnVariables> mutablePI = getFirst(instances);
        mutablePI.abort();
        awaitTillEmpty(instances);
    }

    @Test
    void testValuesReadMode() {
        listener.setKafkaStreams(createStreams());
        listener.getKafkaStreams().start();
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");

        ProcessInstances<BpmnVariables> instances = process.instances();
        assertEmpty(instances);

        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(singletonMap("test", "test")));

        processInstance.start();

        awaitTillOne(instances);

        ProcessInstance<BpmnVariables> pi = getFirstReadOnly(instances);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> pi.abort());
        awaitTillOne(instances);
        ProcessInstance<BpmnVariables> mutablePI = getFirst(instances);
        mutablePI.abort();
        awaitTillEmpty(instances);
    }

    @Test
    void testBasicFlow() {
        listener.setKafkaStreams(createStreams());
        listener.getKafkaStreams().start();
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");

        ProcessInstances<BpmnVariables> instances = process.instances();
        assertEmpty(instances);

        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(singletonMap("test", "test")));

        processInstance.start();
        assertEquals(STATE_ACTIVE, processInstance.status());

        awaitTillOne(instances);

        SecurityPolicy asJohn = SecurityPolicy.of(IdentityProviders.of("john"));

        assertThat(getFirst(instances).workItems(asJohn)).hasSize(1);

        List<WorkItem> workItems = processInstance.workItems(asJohn);
        assertThat(workItems).hasSize(1);
        WorkItem workItem = workItems.get(0);
        assertEquals("john", workItem.getParameters().get("ActorId"));
        processInstance.completeWorkItem(workItem.getId(), null, asJohn);
        assertEquals(STATE_COMPLETED, processInstance.status());
        awaitTillEmpty(instances);
    }

    KafkaStreams createStreams() {
        Topology topology = createTopologyForProcesses();
        KafkaStreams streams = new KafkaStreams(topology, getStreamsConfig());
        streams.setUncaughtExceptionHandler((Throwable throwable) -> {
            LOGGER.error("Kafka persistence error: " + throwable.getMessage(), throwable);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });
        streams.cleanUp();
        return streams;
    }

    Properties getStreamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "kogito");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try {
            Path tempDir = Files.createTempDirectory("kafka-streams-test");
            properties.put(StreamsConfig.STATE_DIR_CONFIG, tempDir.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for Kafka Streams state", e);
        }
        return properties;
    }

}
