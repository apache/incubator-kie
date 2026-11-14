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

package org.kie.kogito.events.process;

import java.util.*;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.addon.quarkus.common.reactive.messaging.MessageDecoratorProvider;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.process.MultipleProcessInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceDataEvent;
import org.kie.kogito.event.usertask.MultipleUserTaskInstanceDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDataEvent;
import org.kie.kogito.events.config.EventsRuntimeConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.reactive.messaging.MutinyEmitter;

import jakarta.enterprise.inject.Instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
public class GroupingMessagingEventPublisherTest {

    @Mock
    private ObjectMapper json;

    @Mock
    private MutinyEmitter<String> processInstancesEventsEmitter;

    @Mock
    private MutinyEmitter<String> processDefinitionEventsEmitter;

    @Mock
    private MutinyEmitter<String> userTasksEventsEmitter;

    @Mock
    private EventsRuntimeConfig eventsRuntimeConfig;

    @Mock
    private MessageDecoratorProvider decoratorProvider;

    @Mock
    private Message<String> decoratedMessage;

    @Mock
    private Instance<MessageDecoratorProvider> decoratorProviderInstance;

    @Mock
    private AbstractMessagingEventPublisher.AbstractMessageEmitter processInstanceConsumer;

    @Mock
    private AbstractMessagingEventPublisher.AbstractMessageEmitter userTaskConsumer;

    @Mock
    private AbstractMessagingEventPublisher.AbstractMessageEmitter processDefinitionConsumer;

    @Spy
    @InjectMocks
    private GroupingMessagingEventPublisher groupingMessagingEventPublisher;

    @Spy
    @InjectMocks
    private ReactiveMessagingEventPublisher reactiveMessagingEventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(decoratorProviderInstance.isResolvable()).thenReturn(true);
        when(decoratorProviderInstance.get()).thenReturn(decoratorProvider);

        when(eventsRuntimeConfig.isProcessInstancesPropagateError()).thenReturn(false);
        when(eventsRuntimeConfig.isProcessDefinitionsPropagateErrors()).thenReturn(false);
        when(eventsRuntimeConfig.isUserTasksPropagateError()).thenReturn(false);

        when(eventsRuntimeConfig.isProcessInstancesEventsEnabled()).thenReturn(true);
        when(eventsRuntimeConfig.isUserTasksEventsEnabled()).thenReturn(true);
    }

    @Test
    public void testGroupingMessagingEventPublisher_publish() throws Exception {
        DataEvent<String> event = mock(DataEvent.class);
        when(event.getType()).thenReturn("ProcessInstanceErrorDataEvent");

        // Test initialization
        groupingMessagingEventPublisher.init();
        when(decoratorProvider.decorate(any(Message.class))).thenReturn(decoratedMessage);

        // Mock the message behavior
        mockMessageForBothAckNack(decoratedMessage);

        // Call method
        groupingMessagingEventPublisher.publish(event);

        // Verify that the consumer has been invoked
        verify(processInstancesEventsEmitter).sendMessageAndForget(any());
    }

    @Test
    public void testReactiveMessagingEventPublisher_publish() throws Exception {
        DataEvent<String> event = mock(DataEvent.class);
        when(event.getType()).thenReturn("ProcessInstanceErrorDataEvent");

        // Test initialization
        reactiveMessagingEventPublisher.init();
        when(decoratorProvider.decorate(any(Message.class))).thenReturn(decoratedMessage);

        // Mock the message behavior
        mockMessageForBothAckNack(decoratedMessage);

        // Call method
        reactiveMessagingEventPublisher.publish(event);

        // Verify that the consumer has been invoked
        verify(processInstancesEventsEmitter).sendMessageAndForget(any());
    }

    @Test
    public void testPublishGroupingByChannel() {
        // Create mock events
        DataEvent<String> processInstanceEvent = mock(ProcessInstanceDataEvent.class);
        when(processInstanceEvent.getType()).thenReturn("ProcessInstanceStateDataEvent");

        DataEvent<String> userTaskEvent = mock(UserTaskInstanceDataEvent.class);
        when(userTaskEvent.getType()).thenReturn("UserTaskInstanceStateDataEvent");

        // Mock getConsumer() to return different emitters based on event type
        doReturn(Optional.of(processInstanceConsumer)).when(groupingMessagingEventPublisher).getConsumer(processInstanceEvent);
        doReturn(Optional.of(userTaskConsumer)).when(groupingMessagingEventPublisher).getConsumer(userTaskEvent);

        // Create a collection of events with different types (ProcessInstance and UserTask)
        Collection<DataEvent<?>> events = Arrays.asList(processInstanceEvent, userTaskEvent);

        // Spy on the publisher's internal method to verify the calls
        doNothing().when(groupingMessagingEventPublisher).publishToTopic(any(), any());

        // Invoke the method to test
        groupingMessagingEventPublisher.publish(events);

        // Capture and verify that the correct emitter was used for each event
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(processInstanceConsumer), any(MultipleProcessInstanceDataEvent.class));
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(userTaskConsumer), any(MultipleUserTaskInstanceDataEvent.class));
    }

    @Test
    public void testPublishMultipleEventsGroupedByChannel() {
        // Create multiple events of different types
        DataEvent<String> processInstanceEvent1 = mock(ProcessInstanceDataEvent.class);
        DataEvent<String> processInstanceEvent2 = mock(ProcessInstanceDataEvent.class);
        DataEvent<String> userTaskEvent1 = mock(UserTaskInstanceDataEvent.class);
        DataEvent<String> userTaskEvent2 = mock(UserTaskInstanceDataEvent.class);

        when(processInstanceEvent1.getType()).thenReturn("ProcessInstanceStateDataEvent");
        when(processInstanceEvent2.getType()).thenReturn("ProcessInstanceStateDataEvent");
        when(userTaskEvent1.getType()).thenReturn("UserTaskInstanceStateDataEvent");
        when(userTaskEvent2.getType()).thenReturn("UserTaskInstanceStateDataEvent");

        // Mock getConsumer() to return corresponding emitters for event types
        doReturn(Optional.of(processInstanceConsumer)).when(groupingMessagingEventPublisher).getConsumer(processInstanceEvent1);
        doReturn(Optional.of(processInstanceConsumer)).when(groupingMessagingEventPublisher).getConsumer(processInstanceEvent2);
        doReturn(Optional.of(userTaskConsumer)).when(groupingMessagingEventPublisher).getConsumer(userTaskEvent1);
        doReturn(Optional.of(userTaskConsumer)).when(groupingMessagingEventPublisher).getConsumer(userTaskEvent2);

        // Create a collection of events that would be grouped by channel
        Collection<DataEvent<?>> events = Arrays.asList(processInstanceEvent1, processInstanceEvent2, userTaskEvent1, userTaskEvent2);

        // Spy on the internal publishToTopic to verify grouping
        doNothing().when(groupingMessagingEventPublisher).publishToTopic(any(), any());

        // Invoke the method to test
        groupingMessagingEventPublisher.publish(events);

        // Verify that two grouped publishToTopic calls are made: one for processInstanceConsumer, one for userTaskConsumer
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(processInstanceConsumer), any(MultipleProcessInstanceDataEvent.class));
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(userTaskConsumer), any(MultipleUserTaskInstanceDataEvent.class));

        // Verify that the right number of events was grouped and passed to each emitter
        ArgumentCaptor<MultipleProcessInstanceDataEvent> captorPI = ArgumentCaptor.forClass(MultipleProcessInstanceDataEvent.class);

        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(processInstanceConsumer), captorPI.capture());
        MultipleProcessInstanceDataEvent groupedProcessInstanceEvents = captorPI.getValue();
        assertThat(groupedProcessInstanceEvents.getData().size()).isEqualTo(2); // both processInstanceEvents are grouped

        ArgumentCaptor<MultipleUserTaskInstanceDataEvent> captorUT = ArgumentCaptor.forClass(MultipleUserTaskInstanceDataEvent.class);

        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(userTaskConsumer), captorUT.capture());
        MultipleUserTaskInstanceDataEvent groupedUserTaskEvents = captorUT.getValue();
        assertThat(groupedUserTaskEvents.getData().size()).isEqualTo(2); // both userTaskEvents are grouped
    }

    @Test
    public void testPublishEmptyEventsCollection() {
        Collection<DataEvent<?>> events = Collections.emptyList();

        // Spy on the internal publishToTopic to verify no calls are made
        doNothing().when(groupingMessagingEventPublisher).publishToTopic(any(), any());

        groupingMessagingEventPublisher.publish(events);

        // Verify that publishToTopic is never called
        verify(groupingMessagingEventPublisher, never()).publishToTopic(any(), anyCollection());
    }

    @Test
    public void testNoConsumersFound() {
        DataEvent<String> processInstanceEvent = mock(DataEvent.class);
        when(processInstanceEvent.getType()).thenReturn("ProcessInstanceStateDataEvent");

        DataEvent<String> userTaskEvent = mock(DataEvent.class);
        when(userTaskEvent.getType()).thenReturn("UserTaskInstanceStateDataEvent");

        // Mock getConsumer() to return empty optionals (no consumers found)
        doReturn(Optional.empty()).when(groupingMessagingEventPublisher).getConsumer(processInstanceEvent);
        doReturn(Optional.empty()).when(groupingMessagingEventPublisher).getConsumer(userTaskEvent);

        // Create a collection of events
        Collection<DataEvent<?>> events = Arrays.asList(processInstanceEvent, userTaskEvent);

        // Spy on the publisher's internal method to verify no calls are made
        doNothing().when(groupingMessagingEventPublisher).publishToTopic(any(), any());

        // Invoke the method to test
        groupingMessagingEventPublisher.publish(events);

        // Verify that publishToTopic is never called since no consumers were found
        verify(groupingMessagingEventPublisher, never()).publishToTopic(any(), anyCollection());
    }

    @Test
    void testPublishToTopic_ExceptionHandling() throws Exception {
        DataEvent<String> event = mock(DataEvent.class);
        when(event.getType()).thenReturn("ProcessInstanceErrorDataEvent");

        groupingMessagingEventPublisher.init();
        when(decoratorProvider.decorate(any(Message.class))).thenThrow(new RuntimeException("Serialization error"));

        // Mock the message behavior
        mockMessageForBothAckNack(decoratedMessage);

        // Call method
        groupingMessagingEventPublisher.publish(event);

        // Check that emitter.sendMessageAndForget was never called
        verify(processInstancesEventsEmitter, never()).sendMessageAndForget(any());
    }

    @Test
    public void testPublishUnsupportedEventType() {
        DataEvent<String> unsupportedEvent = mock(DataEvent.class);
        when(unsupportedEvent.getType()).thenReturn("UnsupportedEvent");

        doReturn(Optional.empty()).when(groupingMessagingEventPublisher).getConsumer(unsupportedEvent);

        Collection<DataEvent<?>> events = Collections.singletonList(unsupportedEvent);

        groupingMessagingEventPublisher.publish(events);

        // Verify no publishing occurred since no consumer exists for unsupported event
        verify(groupingMessagingEventPublisher, never()).publishToTopic(any(), anyCollection());
    }

    @Test
    public void testEventsDisabledInConfig() {
        DataEvent<String> processInstanceEvent = mock(DataEvent.class);
        when(processInstanceEvent.getType()).thenReturn("ProcessInstanceStateDataEvent");

        DataEvent<String> userTaskEvent = mock(DataEvent.class);
        when(userTaskEvent.getType()).thenReturn("UserTaskInstanceStateDataEvent");

        // Disable process and user task events in the config
        when(eventsRuntimeConfig.isProcessInstancesEventsEnabled()).thenReturn(false);
        when(eventsRuntimeConfig.isUserTasksEventsEnabled()).thenReturn(false);

        Collection<DataEvent<?>> events = Arrays.asList(processInstanceEvent, userTaskEvent);

        groupingMessagingEventPublisher.publish(events);

        // Verify no publishing occurred since events are disabled
        verify(groupingMessagingEventPublisher, never()).publishToTopic(any(), anyCollection());
    }

    @Test
    public void testNullEventInCollection() {
        DataEvent<String> validEvent = mock(ProcessInstanceDataEvent.class);
        when(validEvent.getType()).thenReturn("ProcessInstanceStateDataEvent");

        Collection<DataEvent<?>> events = Arrays.asList(validEvent, null); // One valid event and one null event

        // Return a mock consumer for the valid event
        doReturn(Optional.of(processInstanceConsumer)).when(groupingMessagingEventPublisher).getConsumer(validEvent);

        // Call the method
        groupingMessagingEventPublisher.publish(events);

        // Verify the valid event is processed
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(processInstanceConsumer), any(MultipleProcessInstanceDataEvent.class));
    }

    @Test
    public void testDecorateMessage() {
        Message<String> rawMessage = mock(Message.class);
        when(decoratorProvider.decorate(rawMessage)).thenReturn(decoratedMessage);

        reactiveMessagingEventPublisher.init();

        Message<String> result = reactiveMessagingEventPublisher.decorateMessage(rawMessage);
        assertThat(result).isEqualTo(decoratedMessage);

        verify(decoratorProvider).decorate(rawMessage);
    }

    @Test
    public void testPublishToTopicWithDecorator() throws Exception {
        Object event = new Object();
        when(json.writeValueAsString(event)).thenReturn("eventString");

        reactiveMessagingEventPublisher.init();

        // Mock the message emitter
        AbstractMessagingEventPublisher.AbstractMessageEmitter mockEmitter = mock(AbstractMessagingEventPublisher.AbstractMessageEmitter.class);

        // Ensure decorated message is used
        when(decoratorProvider.decorate(any(Message.class))).thenReturn(decoratedMessage);

        // Spy on the reactiveMessagingEventPublisher to allow publishToTopic
        reactiveMessagingEventPublisher.publishToTopic(mockEmitter, event);

        // Verify that the message was decorated and sent
        verify(decoratorProvider).decorate(any(Message.class));
        verify(mockEmitter).accept(decoratedMessage);
    }

    @Test
    public void testPublishWithMultipleEventTypesSomeWithoutConsumers() {
        DataEvent<String> processInstanceEvent = mock(ProcessInstanceDataEvent.class);
        when(processInstanceEvent.getType()).thenReturn("ProcessInstanceStateDataEvent");

        DataEvent<String> unsupportedEvent = mock(DataEvent.class);
        when(unsupportedEvent.getType()).thenReturn("UnsupportedEvent");

        doReturn(Optional.of(processInstanceConsumer)).when(groupingMessagingEventPublisher).getConsumer(processInstanceEvent);
        doReturn(Optional.empty()).when(groupingMessagingEventPublisher).getConsumer(unsupportedEvent);

        Collection<DataEvent<?>> events = Arrays.asList(processInstanceEvent, unsupportedEvent);

        groupingMessagingEventPublisher.publish(events);

        // Ensure that only the supported event was published
        verify(groupingMessagingEventPublisher, times(1)).publishToTopic(eq(processInstanceConsumer), any(MultipleProcessInstanceDataEvent.class));
        verify(groupingMessagingEventPublisher, never()).publishToTopic(any(), eq(Collections.singletonList(unsupportedEvent)));
    }

    private void mockMessageForBothAckNack(Message<String> message) {
        when(message.withAck(any())).thenReturn(message);
        when(message.withNack(any())).thenReturn(message);
    }
}
