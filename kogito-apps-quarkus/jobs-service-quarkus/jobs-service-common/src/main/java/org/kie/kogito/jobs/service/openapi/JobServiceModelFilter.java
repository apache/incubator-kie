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
package org.kie.kogito.jobs.service.openapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Discriminator;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.kie.kogito.jobs.service.api.RecipientDescriptor;
import org.kie.kogito.jobs.service.api.RecipientDescriptorRegistry;
import org.kie.kogito.jobs.service.api.ScheduleDescriptor;
import org.kie.kogito.jobs.service.api.ScheduleDescriptorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.annotations.RegisterForReflection;

import static io.cloudevents.SpecVersion.V03;
import static io.cloudevents.SpecVersion.V1;

/**
 * OpenAPI document adjustments.
 */
@RegisterForReflection
public class JobServiceModelFilter implements OASFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobServiceModelFilter.class);

    static final String TYPE_PROPERTY_NAME = "type";
    static final String JSON_NODE_SCHEMA = "JsonNode";
    static final String SPEC_VERSION_SCHEMA = "SpecVersion";
    static final String SCHEDULE_SCHEMA = "Schedule";
    static final String RECIPIENT_SCHEMA = "Recipient";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        // Since smallrye-open-api 4.2 both the components and their schemas map can be null when the
        // document declares no schemas (e.g. embedded job service without the REST API).
        Components components = openAPI.getComponents();
        Map<String, Schema> schemas = components != null ? components.getSchemas() : null;
        if (schemas == null) {
            LOGGER.debug("No component schemas are present in the OpenAPI document, skipping job service schema adjustments.");
            return;
        }

        // The JsonNode schema is automatically generated from the com.fasterxml.jackson.databind.JsonNode class with
        // the type Schema.SchemaType.ARRAY, however the real type is Schema.SchemaType.OBJECT.
        Schema jsonObjectSchema = schemas.get(JSON_NODE_SCHEMA);
        if (jsonObjectSchema != null) {
            LOGGER.debug("Setting {} schema type to: {}.", JSON_NODE_SCHEMA, Schema.SchemaType.OBJECT);
            jsonObjectSchema.type(List.of(Schema.SchemaType.OBJECT));
        } else {
            LOGGER.warn("{} schema type is not present it the OpenAPI document.", JSON_NODE_SCHEMA);
        }

        // The SpecVersion schema is automatically generated from the io.cloudevents.SpecVersion enum, and thus will
        // be composed of the enum names V03 and V1, however third party clients must send the values "0.3" and "1.0"
        // as part of their produced json. So the OpenAPI document must declare these values instead.
        Schema specVersionSchema = schemas.get(SPEC_VERSION_SCHEMA);
        if (specVersionSchema != null) {
            List<Object> enumerationValues = Collections.unmodifiableList(Arrays.asList(V03.toString(), V1.toString()));
            LOGGER.debug("Changing {} enum schema from: {}, to: {}", SPEC_VERSION_SCHEMA, specVersionSchema.getEnumeration(), enumerationValues);
            specVersionSchema.enumeration(enumerationValues);
        } else {
            LOGGER.warn("{} enum schema is not present in the OpenAPI document.", SPEC_VERSION_SCHEMA);
        }

        Schema recipientSchema = schemas.get(RECIPIENT_SCHEMA);
        if (recipientSchema != null) {
            adjustRecipientSchema(recipientSchema);
        } else {
            LOGGER.error("{} schema is not present in the OpenAPI document.", RECIPIENT_SCHEMA);
        }

        Schema scheduleSchema = schemas.get(SCHEDULE_SCHEMA);
        if (scheduleSchema != null) {
            adjustScheduleSchema(scheduleSchema);
        } else {
            LOGGER.error("{} schema is not present in the OpenAPI document.", SCHEDULE_SCHEMA);
        }
    }

    /**
     * Adds the Recipient implementations to the RecipientSchema in a pluggable manner.
     */
    private void adjustRecipientSchema(Schema schema) {
        LOGGER.debug("Processing Recipient implementations.");
        Discriminator discriminator = addDiscriminator(schema, TYPE_PROPERTY_NAME);
        for (RecipientDescriptor<?> descriptor : RecipientDescriptorRegistry.getInstance().getDescriptors()) {
            String ref = buildLocalSchemaRef(descriptor.getType().getSimpleName());
            LOGGER.debug("Adding recipient mapping: {} -> {}", descriptor.getName(), ref);
            discriminator.addMapping(descriptor.getName(), ref);
        }
        schema.discriminator(discriminator);
        if (discriminator.getMapping() == null || discriminator.getMapping().isEmpty()) {
            LOGGER.error("No Recipients where found.");
        }
    }

    /**
     * Adds the Schedule implementations to the ScheduleSchema in a pluggable manner.
     */
    private void adjustScheduleSchema(Schema schema) {
        LOGGER.debug("Processing Schedule implementations.");
        Discriminator discriminator = addDiscriminator(schema, TYPE_PROPERTY_NAME);
        for (ScheduleDescriptor<?> descriptor : ScheduleDescriptorRegistry.getInstance().getDescriptors()) {
            String ref = buildLocalSchemaRef(descriptor.getType().getSimpleName());
            LOGGER.debug("Adding schedule mapping: {} -> {}", descriptor.getName(), ref);
            discriminator.addMapping(descriptor.getName(), buildLocalSchemaRef(descriptor.getType().getSimpleName()));
        }
        if (discriminator.getMapping() == null || discriminator.getMapping().isEmpty()) {
            LOGGER.error("No Schedules where found.");
        }
    }

    private static Discriminator addDiscriminator(Schema schema, String discriminatorProperty) {
        schema.addProperty(discriminatorProperty, OASFactory.createSchema().type(List.of(Schema.SchemaType.STRING)));
        schema.discriminator(OASFactory.createDiscriminator().propertyName(discriminatorProperty));
        if (schema.getRequired() == null || !schema.getRequired().contains(discriminatorProperty)) {
            schema.addRequired(discriminatorProperty);
        }
        return schema.getDiscriminator();
    }

    private static String buildLocalSchemaRef(String name) {
        String template = "#/components/schemas/%s";
        return String.format(template, name);
    }
}
