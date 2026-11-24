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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerService;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceDuplicatedException;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.kie.kogito.persistence.kafka.KafkaPersistenceUtils.topicName;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertEmpty;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertOne;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KafkaProcessInstancesTest {

    KafkaProcessInstances instances;
    String processId = "aProcessId";

    @Mock
    KafkaProducer producer;

    @Mock
    Process process;

    @Mock
    KeyValueStore<String, byte[]> store;

    @Mock
    ProcessInstanceMarshallerService marshaller;

    String id = UUID.randomUUID().toString();

    String storedId = "process-" + processId + "-" + id;

    @BeforeEach
    public void setup() {
        lenient().doReturn(processId).when(process).id();

        instances = new KafkaProcessInstances(process, producer);
        instances.setStore(store);
        instances.setMarshaller(marshaller);
        lenient().when(marshaller.unmarshallProcessInstance(any(), any())).thenCallRealMethod();
    }

    @Test
    public void testProcessInstancesSetup() {
        instances = new KafkaProcessInstances(process, producer);

        assertThat(instances.getProcess()).isEqualTo(process);

        CompletableFuture<Void> async = CompletableFuture.runAsync(() -> instances.setStore(store));

        assertThat(instances.getStore()).isNotNull();

        assertThat(async).hasNotFailed();

        instances.setStore(null);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> instances.getStore()).havingCause().withMessage("Failed to obtain Kafka Store for process: aProcessId");
    }

    @Test
    public void testProcessInstancesRemove() {
        doReturn(mock(Future.class)).when(producer).send(any());

        instances.remove(id);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).value()).isNull();
        assertThat(captor.getAllValues().get(0).key()).isEqualTo(storedId);
        assertThat(captor.getAllValues().get(0).topic()).isEqualTo(topicName());
    }

    @Test
    public void testProcessInstancesExists() {
        doReturn(new byte[] {}).when(store).get(storedId);

        assertThat(instances.exists(id)).isTrue();
        assertThat(instances.exists(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    public void testProcessInstancesFindById() {
        doReturn(mock(AbstractProcessInstance.class)).when(marshaller).unmarshallProcessInstance(any(), any(), eq(ProcessInstanceReadMode.MUTABLE));

        doReturn(new byte[] {}).when(store).get(storedId);

        assertThat(instances.findById(id)).isPresent();
        assertThat(instances.findById(UUID.randomUUID().toString())).isNotPresent();
    }

    @Test
    public void testProcessInstancesFindByIdReadOnly() {
        doReturn(mock(AbstractProcessInstance.class)).when(marshaller).unmarshallProcessInstance(any(), any(), eq(ProcessInstanceReadMode.READ_ONLY));

        doReturn(new byte[] {}).when(store).get(storedId);

        assertThat(instances.findById(id, ProcessInstanceReadMode.READ_ONLY)).isPresent();
        assertThat(instances.findById(UUID.randomUUID().toString(), ProcessInstanceReadMode.READ_ONLY)).isNotPresent();
    }

    private static class KeyValueIteratorMock implements KeyValueIterator<String, String> {
        boolean hasNext = true;

        @Override
        public boolean hasNext() {
            boolean current = hasNext;
            hasNext = false;
            return current;
        }

        @Override
        public KeyValue<String, String> next() {
            return mock(KeyValue.class);
        }

        @Override
        public void close() {
        }

        @Override
        public String peekNextKey() {
            return "";
        }
    }

    @Test
    public void testProcessInstancesValues() {
        KeyValueIteratorMock iterator = new KeyValueIteratorMock();
        doReturn(iterator).when(store).prefixScan(contains(processId), any());
        assertOne(instances);
    }

    @Test
    public void testProcessInstancesValuesMutable() {
        KeyValueIteratorMock iterator = new KeyValueIteratorMock();
        doReturn(iterator).when(store).prefixScan(contains(processId), any());
        assertOne(instances, ProcessInstanceReadMode.MUTABLE);
    }

    @Test
    public void testProcessInstancesSize() {
        doReturn(mock(KeyValueIterator.class)).when(store).prefixScan(contains(processId), any());
        assertEmpty(instances);
    }

    @Test
    public void testProcessInstancesUpdate() {
        doReturn(mock(Future.class)).when(producer).send(any());
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        doReturn(new byte[] {}).when(marshaller).marshallProcessInstance(instance);
        when(instance.status()).thenReturn(ProcessInstance.STATE_ACTIVE);

        WorkflowProcessInstance processInstance = mock(WorkflowProcessInstance.class);
        when(processInstance.getEventTypes()).thenReturn(new String[0]);
        when(instance.internalGetProcessInstance()).thenReturn(processInstance);

        instances.update(id, instance);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).value()).isEqualTo(new byte[] {});
        assertThat(captor.getAllValues().get(0).key()).isEqualTo(storedId);
        assertThat(captor.getAllValues().get(0).topic()).isEqualTo(topicName());

        verify(marshaller).createdReloadFunction(any());
    }

    @Test
    public void testProcessInstancesUpdateException() {
        doThrow(new RuntimeException()).when(producer).send(any());
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        doReturn(new byte[] {}).when(marshaller).marshallProcessInstance(instance);
        when(instance.status()).thenReturn(ProcessInstance.STATE_ACTIVE);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> instances.update(id, instance));
    }

    @Test
    public void testProcessInstancesUpdateNotActive() {
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        when(instance.status()).thenReturn(ProcessInstance.STATE_COMPLETED);

        instances.update(id, instance);

        verify(producer, never()).send(any(), any());
    }

    @Test
    public void testProcessInstancesCreate() {
        doReturn(mock(Future.class)).when(producer).send(any());
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        doReturn(new byte[] {}).when(marshaller).marshallProcessInstance(instance);
        when(instance.status()).thenReturn(ProcessInstance.STATE_ACTIVE);
        WorkflowProcessInstance processInstance = mock(WorkflowProcessInstance.class);
        when(processInstance.getEventTypes()).thenReturn(new String[0]);
        when(instance.internalGetProcessInstance()).thenReturn(processInstance);
        instances.create(id, instance);

        ArgumentCaptor<ProducerRecord> kafkaCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer, times(2)).send(kafkaCaptor.capture());
        assertThat(kafkaCaptor.getAllValues().get(0).value()).isEqualTo(new byte[] {});
        assertThat(kafkaCaptor.getAllValues().get(0).key()).isEqualTo(storedId);
        assertThat(kafkaCaptor.getAllValues().get(0).topic()).isEqualTo(topicName());

        ArgumentCaptor<Consumer> supplierCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(store).get(storedId);
    }

    @Test
    public void testProcessInstancesCreateNotActive() {
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        when(instance.status()).thenReturn(ProcessInstance.STATE_COMPLETED);

        instances.create(id, instance);

        verify(producer, never()).send(any(), any());
    }

    @Test
    public void testProcessInstancesCreateException() {
        doThrow(new RuntimeException()).when(producer).send(any());
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        doReturn(new byte[] {}).when(marshaller).marshallProcessInstance(instance);
        when(instance.status()).thenReturn(ProcessInstance.STATE_ACTIVE);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> instances.create(id, instance));
    }

    @Test
    public void testProcessInstancesCreateDuplicate() {
        when(store.get(storedId)).thenReturn(new byte[] {});
        AbstractProcessInstance instance = mock(AbstractProcessInstance.class);
        when(instance.status()).thenReturn(ProcessInstance.STATE_ACTIVE);

        assertThatExceptionOfType(ProcessInstanceDuplicatedException.class).isThrownBy(() -> instances.create(id, instance));
    }

}
