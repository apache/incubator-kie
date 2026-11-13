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
package org.drools.commands.runtime;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.drools.commands.fluent.Batch;
import org.drools.commands.runtime.pmml.ApplyPmmlModelCommand;
import org.drools.commands.runtime.process.AbortWorkItemCommand;
import org.drools.commands.runtime.process.CompleteWorkItemCommand;
import org.drools.commands.runtime.process.SignalEventCommand;
import org.drools.commands.runtime.process.StartProcessCommand;
import org.drools.commands.runtime.rule.ActivateRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.AgendaGroupSetFocusCommand;
import org.drools.commands.runtime.rule.ClearActivationGroupCommand;
import org.drools.commands.runtime.rule.ClearAgendaCommand;
import org.drools.commands.runtime.rule.ClearAgendaGroupCommand;
import org.drools.commands.runtime.rule.ClearRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.DeleteCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.FireUntilHaltCommand;
import org.drools.commands.runtime.rule.GetFactHandlesCommand;
import org.drools.commands.runtime.rule.GetObjectCommand;
import org.drools.commands.runtime.rule.GetObjectsCommand;
import org.drools.commands.runtime.rule.InsertElementsCommand;
import org.drools.commands.runtime.rule.InsertObjectCommand;
import org.drools.commands.runtime.rule.ModifyCommand;
import org.drools.commands.runtime.rule.QueryCommand;
import org.drools.commands.runtime.rule.UpdateCommand;
import org.drools.core.base.RuleNameEndsWithAgendaFilter;
import org.drools.core.base.RuleNameEqualsAgendaFilter;
import org.drools.core.base.RuleNameMatchesAgendaFilter;
import org.drools.core.base.RuleNameStartsWithAgendaFilter;
import org.kie.api.command.Command;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.ExecutionResults;


/**
 * <p>Java class for BatchExecutionCommand complex type.
 *
 * DO NOT ADD NEW COMMANDS TO THIS CLASS
 * WITHOUT THOROUGHLY TESTING
 * 1. THE SERIALIZATION OF THOSE COMMANDS
 * 2. THE INTEGRATION OF THOSE COMMANDS IN THE REST AND WS/SOAP IMPLEMENTATIONS!
 */
@XmlRootElement(name="batch-execution")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "batch-execution", propOrder = {"lookup", "commands"})
public class BatchExecutionCommandImpl implements Batch, ExecutableCommand<ExecutionResults> {

    private static final long serialVersionUID = 510l;

    @XmlAttribute
    private String lookup;

    @XmlElements({
                         @XmlElement(name = "abort-work-item", type = AbortWorkItemCommand.class),
                         @XmlElement(name = "signal-event", type = SignalEventCommand.class),
                         @XmlElement(name = "start-process", type = StartProcessCommand.class),
                         @XmlElement(name = "retract", type = DeleteCommand.class),
                         @XmlElement(name = "get-global", type = GetGlobalCommand.class),
                         @XmlElement(name = "set-global", type = SetGlobalCommand.class),
                         @XmlElement(name = "insert-elements", type = InsertElementsCommand.class),
                         @XmlElement(name = "query", type = QueryCommand.class),
                         @XmlElement(name = "insert", type = InsertObjectCommand.class),
                         @XmlElement(name = "modify", type = ModifyCommand.class),
                         @XmlElement(name = "update", type = UpdateCommand.class),
                         @XmlElement(name = "get-object", type = GetObjectCommand.class),
                         @XmlElement(name = "fire-all-rules", type = FireAllRulesCommand.class),
                         @XmlElement(name = "fire-until-halt", type = FireUntilHaltCommand.class),
                         @XmlElement(name = "dispose", type = DisposeCommand.class),
                         @XmlElement(name = "complete-work-item", type = CompleteWorkItemCommand.class),
                         @XmlElement(name = "get-objects", type = GetObjectsCommand.class),
                         @XmlElement(name = "set-focus", type = AgendaGroupSetFocusCommand.class),
                         @XmlElement(name = "activate-ruleflow-group", type = ActivateRuleFlowGroupCommand.class),
                         @XmlElement(name = "clear-activation-group", type = ClearActivationGroupCommand.class),
                         @XmlElement(name = "clear-agenda", type = ClearAgendaCommand.class),
                         @XmlElement(name = "clear-agenda-group", type = ClearAgendaGroupCommand.class),
                         @XmlElement(name = "clear-ruleflow-group", type = ClearRuleFlowGroupCommand.class),
                         @XmlElement(name = "get-fact-handles", type = GetFactHandlesCommand.class),
                         @XmlElement(name = "apply-pmml-model-command", type = ApplyPmmlModelCommand.class),
                         @XmlElement(name = "rule-name-ends-with-agenda-filter", type = RuleNameEndsWithAgendaFilter.class),
                         @XmlElement(name = "rule-name-starts-with-agenda-filter", type = RuleNameStartsWithAgendaFilter.class),
                         @XmlElement(name = "rule-name-equals-agenda-filter", type = RuleNameEqualsAgendaFilter.class),
                         @XmlElement(name = "rule-name-matches-agenda-filter", type = RuleNameMatchesAgendaFilter.class)
                 })
    protected List<Command> commands;

    public BatchExecutionCommandImpl() {
        // JAXB constructor
    }

    public BatchExecutionCommandImpl( List<? extends Command> commands ) {
        this( commands, null );
    }

    public BatchExecutionCommandImpl( List<? extends Command> commands, String lookup ) {
        this.commands = (List<Command>) commands;
        this.lookup = lookup;
    }

    /**
     * Gets the value of the abortWorkItemOrCompleteWorkItemOrFireAllRules property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the abortWorkItemOrCompleteWorkItemOrFireAllRules property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCommand().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SetGlobalCommand }
     * {@link CompleteWorkItemCommand }
     * {@link AbortWorkItemCommand }
     * {@link SignalEventCommand }
     * {@link FireAllRulesCommand }
     * {@link StartProcessCommand }
     * {@link GetGlobalCommand }
     * {@link InsertElementsCommand }
     * {@link QueryCommand }
     * {@link InsertObjectCommand }
     */
    public List<Command> getCommands() {
        return this.commands;
    }

    public BatchExecutionCommandImpl addCommand( Command cmd ) {
        if ( commands == null ) {
            commands = new ArrayList<>();
        }
        this.commands.add(cmd);
        return this;
    }

    public ExecutionResults execute(Context context) {
        for ( Command command : commands ) {
            ((ExecutableCommand<?>) command).execute( context );
        }
        return null;
    }

    public long getDistance() {
        return 0L;
    }

    public void setLookup(String lookup) {
        this.lookup = lookup;
    }

    public String getLookup() {
        return lookup;
    }

    public String toString() {
        return "BatchExecutionCommandImpl{" +
               "lookup='" + lookup + '\'' +
               ", commands=" + commands +
               '}';
    }

    @Override
    public boolean autoFireAllRules() {
        return getCommands().stream().map(ExecutableCommand.class::cast).allMatch(ExecutableCommand::autoFireAllRules);
    }
}
