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
package org.drools.core.common;

import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.util.LinkedList;

public class ActivationGroupImpl
    implements
    InternalActivationGroup {
    private final String                          name;

    private final LinkedList<ActivationGroupNode> list;

    private final ActivationsManager activationsManager;
    
    private long triggeredForRecency;

    public ActivationGroupImpl(ActivationsManager activationsManager, String name) {
        this.activationsManager = activationsManager;
        this.name = name;
        this.list = new LinkedList();
        this.triggeredForRecency = -1;
    }

    public String getName() {
        return this.name;
    }

    public void addActivation(final InternalMatch internalMatch) {
        final ActivationGroupNode node = new ActivationGroupNode(internalMatch,
                                                                 this );
        internalMatch.setActivationGroupNode(node);
        this.list.add( node );
    }

    public void removeActivation(final InternalMatch internalMatch) {
        final ActivationGroupNode node = internalMatch.getActivationGroupNode();
        this.list.remove( node );
        internalMatch.setActivationGroupNode(null);
    }

    public java.util.Iterator iterator() {
        return this.list.javaUtilIterator();
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public int size() {
        return this.list.size();
    }

    public void clear() {
        activationsManager.clearAndCancelActivationGroup( name );
    }

    public void reset() {
        list.clear();
    }

    public LinkedList<ActivationGroupNode> getList() {
        return list;
    }

    public long getTriggeredForRecency() {
        return triggeredForRecency;
    }

    public void setTriggeredForRecency(long executedForRecency) {
        this.triggeredForRecency = executedForRecency;
    }

    @Override
    public String toString() {
        return "activation-group: " + name;
    }
}
