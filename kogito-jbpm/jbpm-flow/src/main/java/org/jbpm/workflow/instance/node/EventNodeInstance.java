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
package org.jbpm.workflow.instance.node;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.NodeIoHelper;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.instance.impl.ExtendedNodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.kogito.internal.process.event.KogitoEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.jobs.TimerDescription;
import org.kie.kogito.process.BaseEventDescription;
import org.kie.kogito.process.EventDescription;
import org.kie.kogito.process.NamedDataType;
import org.kie.kogito.timer.TimerInstance;

import static java.util.Objects.isNull;
import static org.jbpm.ruleflow.core.Metadata.EVENT_TYPE;
import static org.jbpm.ruleflow.core.Metadata.EVENT_TYPE_SIGNAL;
import static org.jbpm.workflow.instance.impl.DummyEventListener.EMPTY_EVENT_LISTENER;
import static org.jbpm.workflow.instance.node.TimerNodeInstance.TIMER_TRIGGERED_EVENT;
import static org.kie.kogito.internal.utils.ConversionUtils.isEmpty;
import static org.kie.kogito.internal.utils.ConversionUtils.isNotEmpty;

/**
 * Runtime counterpart of an event node.
 * 
 */
public class EventNodeInstance extends ExtendedNodeInstanceImpl implements KogitoEventListener, EventNodeInstanceInterface, EventBasedNodeInstanceInterface {

    private static final long serialVersionUID = 510l;

    @Override
    public void signalEvent(String type, Object event, Function<String, Object> varResolver) {
        if (TIMER_TRIGGERED_EVENT.equals(type)) {
            TimerInstance timerInstance = (TimerInstance) event;
            if (timerInstance.getId().equals(slaTimerId)) {
                handleSLAViolation();
            }
        } else if (("slaViolation:" + getStringId()).equals(type)) {
            handleSLAViolation();
        } else {
            EventNode eventNode = (EventNode) getNode();
            Map<String, Object> outputSet = new HashMap<>();
            outputSet.put(eventNode.getInputVariableName(), event);
            NodeIoHelper.processOutputs(this, key -> outputSet.get(key), varName -> this.getVariable(varName));
            triggerCompleted();
        }
    }

    public void signalEvent(String type, Object event) {
        this.signalEvent(type, event, varName -> this.getVariable(varName));
    }

    @Override
    public void internalTrigger(final KogitoNodeInstance from, String type) {
        if (!Node.CONNECTION_DEFAULT_TYPE.equals(type)) {
            throw new IllegalArgumentException(
                    "An EventNode only accepts default incoming connections!");
        }
        triggerTime = new Date();
        addEventListeners();
        // Do nothing, event activated
    }

    @Override
    protected void configureSla() {
        String slaDueDateExpression = (String) getNode().getMetaData().get("customSLADueDate");
        if (slaDueDateExpression != null) {
            TimerInstance timer = ((WorkflowProcessInstanceImpl) getProcessInstance()).configureSLATimer(slaDueDateExpression, this.getId());
            if (timer != null) {
                this.slaTimerId = timer.getId();
                this.slaDueDate = new Date(System.currentTimeMillis() + timer.getDelay());
                this.slaCompliance = KogitoProcessInstance.SLA_PENDING;
                logger.debug("SLA for node instance {} is PENDING with due date {}", this.getStringId(), this.slaDueDate);
            }
        }
    }

    protected void handleSLAViolation() {
        if (slaCompliance == KogitoProcessInstance.SLA_PENDING) {
            InternalProcessRuntime processRuntime = ((InternalProcessRuntime) getProcessInstance().getKnowledgeRuntime().getProcessRuntime());
            processRuntime.getProcessEventSupport().fireBeforeSLAViolated(getProcessInstance(), this, getProcessInstance().getKnowledgeRuntime());
            logger.debug("SLA violated on node instance {}", getStringId());
            this.slaCompliance = KogitoProcessInstance.SLA_VIOLATED;
            this.slaTimerId = null;
            processRuntime.getProcessEventSupport().fireAfterSLAViolated(getProcessInstance(), this, getProcessInstance().getKnowledgeRuntime());
        }
    }

    private void cancelSlaTimer() {
        if (isNotEmpty(this.slaTimerId)) {
            JobsService jobService = ((InternalProcessRuntime) getProcessInstance().getKnowledgeRuntime().getProcessRuntime()).getJobsService();
            jobService.cancelJob(this.slaTimerId);
            logger.debug("SLA Timer {} has been canceled", this.slaTimerId);
        }
    }

    protected void addTimerListener() {

        getProcessInstance().addEventListener(TIMER_TRIGGERED_EVENT, new VariableExternalEventListener(TIMER_TRIGGERED_EVENT), false);
        getProcessInstance().addEventListener("timer", new VariableExternalEventListener("timer"), true);
        getProcessInstance().addEventListener("slaViolation:" + getStringId(), new VariableExternalEventListener("slaViolation"), true);
    }

    public void removeTimerListeners() {
        getProcessInstance().removeEventListener(TIMER_TRIGGERED_EVENT, new VariableExternalEventListener(TIMER_TRIGGERED_EVENT), false);
        getProcessInstance().removeEventListener("timer", new VariableExternalEventListener("timer"), true);
        getProcessInstance().removeEventListener("slaViolation:" + getStringId(), new VariableExternalEventListener("slaViolation"), true);
    }

    public EventNode getEventNode() {
        return (EventNode) getNode();
    }

    public void triggerCompleted() {
        getProcessInstance().removeEventListener(getEventType(), getEventListener(), true);
        removeTimerListeners();
        if (this.slaCompliance == KogitoProcessInstance.SLA_PENDING) {
            if (System.currentTimeMillis() > slaDueDate.getTime()) {
                // completion of the node instance is after expected SLA due date, mark it accordingly
                this.slaCompliance = KogitoProcessInstance.SLA_VIOLATED;
            } else {
                this.slaCompliance = KogitoProcessInstance.SLA_MET;
            }
        }
        cancelSlaTimer();
        ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).setCurrentLevel(getLevel());
        triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, true);
    }

    @Override
    public void cancel(CancelType cancelType) {
        getProcessInstance().removeEventListener(getEventType(), getEventListener(), true);
        removeTimerListeners();
        if (this.slaCompliance == KogitoProcessInstance.SLA_PENDING) {
            if (System.currentTimeMillis() > slaDueDate.getTime()) {
                // completion of the process instance is after expected SLA due date, mark it accordingly
                this.slaCompliance = KogitoProcessInstance.SLA_VIOLATED;
            } else {
                this.slaCompliance = KogitoProcessInstance.SLA_ABORTED;
            }
        }
        removeTimerListeners();
        super.cancel(cancelType);
    }

    private class VariableExternalEventListener implements KogitoEventListener, Serializable {
        private static final long serialVersionUID = 5L;

        private String eventType;

        VariableExternalEventListener(String eventType) {
            this.eventType = eventType;
        }

        public String[] getEventTypes() {
            return new String[] { eventType };
        }

        public void signalEvent(String type, Object event) {
            callSignal(type, event);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((eventType == null) ? 0 : eventType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            VariableExternalEventListener other = (VariableExternalEventListener) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (eventType == null) {
                if (other.eventType != null)
                    return false;
            } else if (!eventType.equals(other.eventType))
                return false;
            return true;
        }

        private EventNodeInstance getOuterType() {
            return EventNodeInstance.this;
        }
    }

    @Override
    public void addEventListeners() {
        String eventType = getEventType();
        if (isVariableExpression(getEventNode().getType())) {
            getProcessInstance().addEventListener(eventType, new VariableExternalEventListener(eventType), true);
        } else {
            getProcessInstance().addEventListener(eventType, getEventListener(), true);
        }
        if (isNotEmpty(this.slaTimerId)) {
            addTimerListener();
        }
    }

    @Override
    public void removeEventListeners() {

    }

    public String getEventType() {
        return resolveExpression(getEventNode().getType());
    }

    protected KogitoEventListener getEventListener() {
        return EMPTY_EVENT_LISTENER;
    }

    private boolean isVariableExpression(String eventType) {
        if (eventType == null) {
            return false;
        }
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(eventType);
        if (matcher.find()) {
            return true;
        }

        return false;
    }

    private void callSignal(String type, Object event) {
        signalEvent(type, event);
    }

    @Override
    public String[] getEventTypes() {
        return new String[] { getEventType() };
    }

    @Override
    public Set<EventDescription<?>> getEventDescriptions() {
        NamedDataType dataType = null;
        String variableName = getEventNode().getVariableName();
        if (variableName != null) {
            VariableScopeInstance variableScopeInstance = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
            if (variableScopeInstance == null) {
                variableScopeInstance = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
            }
            if (variableScopeInstance != null) {
                Variable variable = variableScopeInstance.getVariableScope().findVariable(variableName);
                if (variable != null) {
                    dataType = new NamedDataType(variable.getName(), variable.getType());
                }
            }
        }
        EventNode eventNode = getEventNode();
        return Collections.singleton(new BaseEventDescription(getEventType(), eventNode.getUniqueId(), eventNode.getName(),
                (String) eventNode.getMetaData().getOrDefault(EVENT_TYPE, EVENT_TYPE_SIGNAL), getStringId(), getProcessInstance().getStringId(), dataType));
    }

    @Override
    public Collection<TimerDescription> timers() {
        if (isEmpty(slaTimerId)) {
            return super.timers();
        }

        Collection<TimerDescription> toReturn = super.timers();
        TimerDescription slaTimer = TimerDescription.Builder.ofNodeInstance(this)
                .timerId(slaTimerId)
                .timerDescription("[SLA] " + resolveExpression(getNodeName()))
                .build();
        toReturn.add(slaTimer);
        return toReturn;
    }

    @Override
    public void rescheduleSlaTimer(ZonedDateTime slaDueDate) {
        if (isNull(slaDueDate)) {
            throw new IllegalArgumentException("Cannot update SLA: slaDueDate cannot be null");
        }

        if (isEmpty(slaTimerId)) {
            throw new IllegalStateException("Cannot update SLA: Node has NO SLA configured");
        }
        InternalProcessRuntime processRuntime = ((InternalProcessRuntime) getProcessInstance().getKnowledgeRuntime().getProcessRuntime());
        ((WorkflowProcessInstanceImpl) getProcessInstance()).rescheduleTimer(slaTimerId, slaDueDate, getId());
        this.slaDueDate = Date.from(slaDueDate.toInstant());
        processRuntime.getProcessEventSupport().fireOnNodeStateChanged(this, getProcessInstance().getKnowledgeRuntime());
    }

}
