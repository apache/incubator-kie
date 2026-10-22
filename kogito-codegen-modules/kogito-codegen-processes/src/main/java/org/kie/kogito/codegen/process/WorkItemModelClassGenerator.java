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
package org.kie.kogito.codegen.process;

import java.util.List;

import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.jbpm.compiler.canonical.WorkItemModelMetaData;
import org.kie.api.definition.process.WorkflowProcess;

public class WorkItemModelClassGenerator {

    private final WorkflowProcess workFlowProcess;
    private List<WorkItemModelMetaData> modelMetaData;

    public WorkItemModelClassGenerator(WorkflowProcess workFlowProcess) {
        this.workFlowProcess = workFlowProcess;
    }

    public List<WorkItemModelMetaData> generate() {
        // create model class for all variables
        modelMetaData = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(workFlowProcess);
        return modelMetaData;
    }

    public static String generatedFilePath(String classname) {
        return classname.replace('.', '/') + ".java";
    }

}
