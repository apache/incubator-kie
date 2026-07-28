/**
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
package org.drools.commands.runtime.rule;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.drools.core.common.InternalWorkingMemory;
import org.drools.commands.jaxb.JaxbUnknownAdapter;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.internal.command.RegistryContext;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class FireUntilHaltCommand
	implements
    ExecutableCommand<Void> {
    private static final long serialVersionUID = 510l;

    // test

    @XmlJavaTypeAdapter(JaxbUnknownAdapter.class)
    @XmlAnyElement(lax = true)
    private AgendaFilter agendaFilter = null;

    public FireUntilHaltCommand() {
    }

    public FireUntilHaltCommand(AgendaFilter agendaFilter) {
        this.agendaFilter = agendaFilter;
    }

    public AgendaFilter getAgendaFilter() {
        return agendaFilter;
    }

    public void setAgendaFilter(AgendaFilter agendaFilter) {
        this.agendaFilter = agendaFilter;
    }

    public Void execute(Context context) {
        KieSession ksession = ((RegistryContext) context).lookup( KieSession.class );

        if ( !( (InternalWorkingMemory) ksession ).getAgenda().isFiring() ) {
            new Thread( () -> ksession.fireUntilHalt( agendaFilter ) ).start();
        }

        return null;
    }

    public String toString() {
        if ( agendaFilter != null ) {
            return "session.fireUntilHalt( " + agendaFilter + " );";
        } else {
            return "session.fireUntilHalt();";
        }
    }
}
