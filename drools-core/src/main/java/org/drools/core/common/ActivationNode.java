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
import org.drools.base.util.AbstractLinkedListNode;

public class ActivationNode extends AbstractLinkedListNode<ActivationNode> {

    private static final long     serialVersionUID = 510l;

    private final InternalMatch internalMatch;
    private final Object parentContainer;

    public ActivationNode(final InternalMatch internalMatch,
                          final Object parentContainer) {
        super();
        this.internalMatch = internalMatch;
        this.internalMatch.setActivationNode(this);
        this.parentContainer = parentContainer;
    }

    public InternalMatch getActivation() {
        return this.internalMatch;
    }

    public Object getParentContainer() {
        return this.parentContainer;
    }

}
