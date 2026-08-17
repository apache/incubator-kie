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
package org.jbpm.process.instance.event;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.core.marshalling.MarshallerWriteContext;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.kogito.signal.SignalManager;

public class DefaultSignalManager implements SignalManager {

    private Map<String, List<EventListener>> processEventListeners = new ConcurrentHashMap<>();
    private InternalKnowledgeRuntime kruntime;

    public DefaultSignalManager(InternalKnowledgeRuntime kruntime) {
        this.kruntime = kruntime;
    }

    public void addEventListener(String type, EventListener eventListener) {
        List<EventListener> eventListeners = processEventListeners.get(type);
        //this first "if" is not pretty, but allows to synchronize only when needed
        if (eventListeners == null) {
            synchronized (processEventListeners) {
                eventListeners = processEventListeners.get(type);
                if (eventListeners == null) {
                    eventListeners = new CopyOnWriteArrayList<>();
                    processEventListeners.put(type, eventListeners);
                }
            }
        }
        eventListeners.add(eventListener);
    }

    public void removeEventListener(String type, EventListener eventListener) {
        if (processEventListeners != null) {
            List<EventListener> eventListeners = processEventListeners.get(type);
            if (eventListeners != null) {
                eventListeners.remove(eventListener);
                if (eventListeners.isEmpty()) {
                    processEventListeners.remove(type);
                }
            }
        }
    }

    public void signalEvent(String type, Object event) {
        ((DefaultSignalManager) ((InternalProcessRuntime) kruntime.getProcessRuntime()).getSignalManager()).internalSignalEvent(type, event);
    }

    public void internalSignalEvent(String type, Object event) {
        if (processEventListeners != null) {
            List<EventListener> eventListeners = processEventListeners.get(type);
            if (eventListeners != null) {
                for (EventListener eventListener : eventListeners) {
                    eventListener.signalEvent(type, event);
                }
            }
        }
    }

    public void signalEvent(String processInstanceId, String type, Object event) {
        ProcessInstance processInstance = kruntime.getProcessInstance(processInstanceId);
        if (processInstance != null) {
            processInstance.signalEvent(type, event);
        }
    }

    public static class SignalProcessInstanceAction extends AbstractPropagationEntry<ReteEvaluator> implements WorkingMemoryAction {

        private String processInstanceId;
        private String type;
        private Object event;

        public SignalProcessInstanceAction(String processInstanceId, String type, Object event) {
            this.processInstanceId = processInstanceId;
            this.type = type;
            this.event = event;

        }

        public SignalProcessInstanceAction(MarshallerReaderContext context) throws IOException, ClassNotFoundException {
            processInstanceId = context.readUTF();
            type = context.readUTF();
            if (context.readBoolean()) {
                event = context.readObject();
            }
        }

        public void internalExecute(ReteEvaluator reteEvaluator) {
            ProcessInstance processInstance = ((InternalKnowledgeRuntime) reteEvaluator).getProcessInstance(processInstanceId);
            if (processInstance != null) {
                processInstance.signalEvent(type, event);
            }
        }

        public void execute(InternalKnowledgeRuntime kruntime) {
            ProcessInstance processInstance = kruntime.getProcessInstance(processInstanceId);
            if (processInstance != null) {
                processInstance.signalEvent(type, event);
            }
        }

        public void write(MarshallerWriteContext context) throws IOException {
            context.writeInt(WorkingMemoryAction.SignalProcessInstanceAction);
            context.writeUTF(processInstanceId);
            context.writeUTF(type);
            context.writeBoolean(event != null);
            if (event != null) {
                context.writeObject(event);
            }
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            processInstanceId = in.readUTF();
            type = in.readUTF();
            if (in.readBoolean()) {
                event = in.readObject();
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(processInstanceId);
            out.writeUTF(type);
            out.writeBoolean(event != null);
            if (event != null) {
                out.writeObject(event);
            }
        }

    }

    public static class SignalAction extends AbstractPropagationEntry<ReteEvaluator> implements WorkingMemoryAction {

        private String type;
        private Object event;

        public SignalAction(String type, Object event) {
            this.type = type;
            this.event = event;
        }

        public SignalAction(MarshallerReaderContext context) throws IOException, ClassNotFoundException {
            type = context.readUTF();
            if (context.readBoolean()) {
                event = context.readObject();
            }
        }

        public void internalExecute(ReteEvaluator reteEvaluator) {
            ((DefaultSignalManager) ((InternalProcessRuntime) ((InternalWorkingMemory) reteEvaluator).getProcessRuntime()).getSignalManager()).internalSignalEvent(type, event);
        }

        public void execute(InternalKnowledgeRuntime kruntime) {
            ((DefaultSignalManager) ((InternalProcessRuntime) kruntime.getProcessRuntime()).getSignalManager()).internalSignalEvent(type, event);
        }

        public void write(MarshallerWriteContext context) throws IOException {
            context.writeInt(WorkingMemoryAction.SignalAction);
            context.writeUTF(type);
            context.writeBoolean(event != null);
            if (event != null) {
                context.writeObject(event);
            }
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            type = in.readUTF();
            if (in.readBoolean()) {
                event = in.readObject();
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(type);
            out.writeBoolean(event != null);
            if (event != null) {
                out.writeObject(event);
            }
        }

    }

    @Override
    public boolean accept(String type, Object event) {
        return processEventListeners.containsKey(type);
    }
}
