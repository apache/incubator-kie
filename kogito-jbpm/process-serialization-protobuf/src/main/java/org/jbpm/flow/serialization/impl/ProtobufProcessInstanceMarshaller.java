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
package org.jbpm.flow.serialization.impl;

import java.io.IOException;

import org.jbpm.flow.serialization.MarshallerContextName;
import org.jbpm.flow.serialization.MarshallerReaderContext;
import org.jbpm.flow.serialization.MarshallerWriterContext;
import org.jbpm.flow.serialization.ProcessInstanceMarshaller;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.process.impl.AbstractProcessInstance;

/**
 * Marshaller class for RuleFlowProcessInstances
 */

public class ProtobufProcessInstanceMarshaller implements ProcessInstanceMarshaller {

    @Override
    public void writeProcessInstance(MarshallerWriterContext context, ProcessInstance<?> processInstance) throws IOException {
        RuleFlowProcessInstance pi = (RuleFlowProcessInstance) ((AbstractProcessInstance<?>) processInstance).internalGetProcessInstance();
        ProtobufProcessInstanceWriter writer = new ProtobufProcessInstanceWriter(context);
        writer.writeProcessInstance(pi, context.output());
    }

    @Override
    public ProcessInstance<?> readProcessInstance(MarshallerReaderContext context) throws IOException {
        ProtobufProcessInstanceReader reader = new ProtobufProcessInstanceReader(context);
        boolean readOnly = context.get(MarshallerContextName.MARSHALLER_INSTANCE_READ_ONLY);
        boolean eager = context.get(MarshallerContextName.MARSHALLER_INSTANCE_EAGER);
        AbstractProcess<?> process = (AbstractProcess<?>) context.get(MarshallerContextName.MARSHALLER_PROCESS);
        RuleFlowProcessInstance wpi = reader.read(context.input());
        if (readOnly) {
            return process.createReadOnlyInstance(wpi);
        }
        ProcessInstance<?> processInstance = process.createInstance(wpi);
        if (eager) {
            ((AbstractProcessInstance<?>) processInstance).internalSetProcessInstance(wpi);
        }
        return processInstance;
    }

    @Override
    public void reloadProcessInstance(MarshallerReaderContext context, ProcessInstance<?> processInstance) throws IOException {
        ProtobufProcessInstanceReader reader = new ProtobufProcessInstanceReader(context);
        ((AbstractProcessInstance<?>) processInstance).internalSetProcessInstance(reader.read(context.input()));
    }

}
