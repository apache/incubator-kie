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
package org.drools.tms;

import org.drools.core.common.ActivationsManager;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.TerminalNode;
import org.drools.base.util.LinkedList;
import org.drools.kiesession.MockInternalMatch;
import org.drools.tms.agenda.TruthMaintenanceSystemInternalMatch;

public class TMSMockInternalMatch extends MockInternalMatch implements TruthMaintenanceSystemInternalMatch {
    @Override
    public void addBlocked(LogicalDependency node) {

    }

    @Override
    public void setBlocked(LinkedList justified) {

    }

    @Override
    public void addLogicalDependency(LogicalDependency node) {

    }

    @Override
    public LinkedList getLogicalDependencies() {
        return null;
    }

    @Override
    public LinkedList<SimpleMode> getBlockers() {
        return null;
    }

    @Override
    public void setLogicalDependencies(LinkedList justified) {

    }

    @Override
    public void removeAllBlockersAndBlocked(ActivationsManager activationsManager) {

    }

    @Override
    public void removeBlocked(LogicalDependency dep) {

    }

    @Override
    public void setActivationFactHandle(InternalFactHandle factHandle) {

    }

    @Override
    public TerminalNode getTerminalNode() {
        return null;
    }

    @Override
    public String toExternalForm() {
        return null;
    }

    @Override
    public Runnable getCallback() {
        return null;
    }

    @Override
    public void setCallback(Runnable callback) {

    }
}
