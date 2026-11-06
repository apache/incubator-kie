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
package org.jbpm.flow.migration.impl;

import java.io.IOException;
import java.io.InputStream;

import org.jbpm.flow.migration.MigrationPlanFileFormatException;
import org.jbpm.flow.migration.MigrationPlanFileReader;
import org.jbpm.flow.migration.model.MigrationPlan;
import org.jbpm.flow.migration.model.VariableMigrationPlan;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class JsonMigrationPlanFileReader implements MigrationPlanFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMigrationPlanFileReader.class);
    public static final String MIGRATION_PLAN_FILE_EXTENSION = "mpf";

    private ObjectMapper objectMapper;

    public JsonMigrationPlanFileReader() {
        objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(WorkflowElementIdentifier.class, new JsonWorkflowElementIdentifierDeserializer());
        objectMapper.registerModule(simpleModule);
    }

    public MigrationPlan read(InputStream is) throws IOException {
        try {

            MigrationPlan migrationPlan = objectMapper.readValue(is, MigrationPlan.class);
            LOGGER.trace("Read migration plan {} in json format", migrationPlan);
            if (migrationPlan.getProcessMigrationPlan() != null) {
                migrationPlan.getProcessMigrationPlan().getVariableMigrationPlan().forEach(VariableMigrationPlan::validate);
            }
            return migrationPlan;
        } catch (IOException e) {
            throw new MigrationPlanFileFormatException("Error during marshalling", e);
        } catch (IllegalArgumentException e) {
            throw new MigrationPlanFileFormatException("Invalid variable migration plan: " + e.getMessage());
        }
    }

    @Override
    public String getFileExtension() {
        return "." + MIGRATION_PLAN_FILE_EXTENSION;
    }

}
