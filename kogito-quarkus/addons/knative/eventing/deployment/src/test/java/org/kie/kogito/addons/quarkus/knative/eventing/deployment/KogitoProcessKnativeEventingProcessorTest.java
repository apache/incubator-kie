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
package org.kie.kogito.addons.quarkus.knative.eventing.deployment;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jbpm.compiler.canonical.TriggerMetaData;
import org.jbpm.ruleflow.core.Metadata;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Node;
import org.kie.kogito.codegen.process.events.ProcessCloudEventMeta;
import org.kie.kogito.codegen.process.events.ProcessCloudEventMetaBuilder;
import org.kie.kogito.event.EventKind;
import org.kie.kogito.event.cloudevents.CloudEventMeta;
import org.kie.kogito.quarkus.extensions.spi.deployment.KogitoProcessContainerGeneratorBuildItem;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.kubernetes.spi.DeployStrategy;
import io.quarkus.kubernetes.spi.KubernetesDeploymentTargetBuildItem;
import io.quarkus.kubernetes.spi.KubernetesResourceMetadataBuildItem;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KogitoProcessKnativeEventingProcessorTest {

    private static TriggerMetaData triggerMetadata;
    private static final CloudEventMeta EXTENDED_CLOUD_EVENT1 = new CloudEventMeta("extendedEvent1", "source1", EventKind.PRODUCED);
    private static final CloudEventMeta EXTENDED_CLOUD_EVENT2 = new CloudEventMeta("extendedEvent2", "source2", EventKind.CONSUMED);

    @BeforeAll
    static void setupClass() {
        Node node = mock(Node.class);
        when(node.getId()).thenReturn(WorkflowElementIdentifierFactory.fromExternalFormat(1L));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Metadata.TRIGGER_REF, "myType");
        metadata.put(Metadata.MAPPING_VARIABLE, "myVar");
        metadata.put(Metadata.TRIGGER_TYPE, "ProduceMessage");
        metadata.put(Metadata.MESSAGE_TYPE, "myDataType");
        when(node.getMetaData()).thenReturn(metadata);
        triggerMetadata = TriggerMetaData.of(node);
    }

    @Test
    void checkNotBuiltMetadataIfNoCEs() {
        final KogitoProcessContainerGeneratorBuildItem containerGeneratorBuildItem = new KogitoProcessContainerGeneratorBuildItem(Collections.emptySet());
        final KogitoProcessKnativeEventingProcessor processor = new KogitoProcessKnativeEventingProcessor();
        final MockKogitoKnativeMetadataProducer metadata = new MockKogitoKnativeMetadataProducer();

        assertThat(metadata.getItem()).isNull();
    }

    @Test
    void checkBuiltMetadataWithCEsNullSelectedItem() {
        final KogitoProcessContainerGeneratorBuildItem containerGeneratorBuildItem = new KogitoProcessContainerGeneratorBuildItem(Collections.emptySet());
        final ProcessCloudEventMetaBuilder mockedCEBuilder = mock(ProcessCloudEventMetaBuilder.class);
        final KogitoProcessKnativeEventingProcessor processor = new KogitoProcessKnativeEventingProcessor();
        final MockKogitoKnativeMetadataProducer metadata = new MockKogitoKnativeMetadataProducer();
        final KubernetesResourceMetadataBuildItem kubernetesResourceMetadataBuildItem = new KubernetesResourceMetadataBuildItem("kubernetes", "apps", "v1", "Deployment", "name");
        final List<KubernetesResourceMetadataBuildItem> kubernetesMetaBuildItems = singletonList(kubernetesResourceMetadataBuildItem);

        Optional<KogitoServiceDeploymentTarget> selectedTarget = processor.selectDeploymentTarget(null, kubernetesMetaBuildItems);

        assertThat(selectedTarget.isPresent()).isTrue();

        // Create a set of cloud events including the process event and extended events
        Set<CloudEventMeta> cloudEvents = new HashSet<>();
        cloudEvents.add(new ProcessCloudEventMeta("123", triggerMetadata));
        cloudEvents.add(EXTENDED_CLOUD_EVENT1);
        cloudEvents.add(EXTENDED_CLOUD_EVENT2);

        KogitoKnativeResourcesMetadataBuildItem item = new KogitoKnativeResourcesMetadataBuildItem(
                cloudEvents,
                selectedTarget.get());
        metadata.produce(item);

        assertThat(metadata.getItem()).isNotNull();
        Set<CloudEventMeta> resultCloudEvents = metadata.getItem().getCloudEvents();
        assertThat(resultCloudEvents.contains(EXTENDED_CLOUD_EVENT1)).isTrue();
        assertThat(resultCloudEvents.contains(EXTENDED_CLOUD_EVENT2)).isTrue();
    }

    @Test
    void checkBuiltMetadataWithCEsNotNullSelectedItem() {
        final KogitoProcessContainerGeneratorBuildItem containerGeneratorBuildItem = new KogitoProcessContainerGeneratorBuildItem(Collections.emptySet());
        final ProcessCloudEventMetaBuilder mockedCEBuilder = mock(ProcessCloudEventMetaBuilder.class);
        final KogitoProcessKnativeEventingProcessor processor = new KogitoProcessKnativeEventingProcessor();
        final MockKogitoKnativeMetadataProducer metadata = new MockKogitoKnativeMetadataProducer();
        final KubernetesResourceMetadataBuildItem kubernetesResourceMetadataBuildItem = new KubernetesResourceMetadataBuildItem("kubernetes", "apps", "v1", "Deployment", "name");
        final List<KubernetesResourceMetadataBuildItem> kubernetesMetaBuildItems = singletonList(kubernetesResourceMetadataBuildItem);
        final List<KubernetesDeploymentTargetBuildItem> deploymentTargets =
                singletonList(new KubernetesDeploymentTargetBuildItem("kubernetes", "Deployment", "apps", "v1", DeployStrategy.CreateOrUpdate));

        Optional<KogitoServiceDeploymentTarget> selectedTarget = processor.selectDeploymentTarget(deploymentTargets, kubernetesMetaBuildItems);

        assertThat(selectedTarget.isPresent()).isTrue();

        // Create a set of cloud events including the process event and extended events
        Set<CloudEventMeta> cloudEvents = new HashSet<>();
        cloudEvents.add(new ProcessCloudEventMeta("123", triggerMetadata));
        cloudEvents.add(EXTENDED_CLOUD_EVENT1);
        cloudEvents.add(EXTENDED_CLOUD_EVENT2);

        KogitoKnativeResourcesMetadataBuildItem item = new KogitoKnativeResourcesMetadataBuildItem(
                cloudEvents,
                selectedTarget.get());
        metadata.produce(item);

        assertThat(metadata.getItem()).isNotNull();
        Set<CloudEventMeta> resultCloudEvents = metadata.getItem().getCloudEvents();
        assertThat(resultCloudEvents.contains(EXTENDED_CLOUD_EVENT1)).isTrue();
        assertThat(resultCloudEvents.contains(EXTENDED_CLOUD_EVENT2)).isTrue();
    }

    private static final class MockKogitoKnativeMetadataProducer implements BuildProducer<KogitoKnativeResourcesMetadataBuildItem> {

        private KogitoKnativeResourcesMetadataBuildItem item;

        @Override
        public void produce(KogitoKnativeResourcesMetadataBuildItem item) {
            this.item = item;
        }

        public KogitoKnativeResourcesMetadataBuildItem getItem() {
            return item;
        }
    }
}
