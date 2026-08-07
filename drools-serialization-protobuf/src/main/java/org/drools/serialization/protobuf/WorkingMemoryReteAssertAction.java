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
package org.drools.serialization.protobuf;

import java.io.IOException;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;

public class WorkingMemoryReteAssertAction
        extends AbstractPropagationEntry<ReteEvaluator>
        implements WorkingMemoryAction {
    protected InternalFactHandle factHandle;

    protected boolean            removeLogical;

    protected boolean            updateEqualsMap;

    protected RuleImpl ruleOrigin;

    protected Tuple tuple;

    protected WorkingMemoryReteAssertAction() { }

    public WorkingMemoryReteAssertAction(MarshallerReaderContext context) throws IOException {
        this.factHandle = context.getHandles().get( context.readLong() );
        this.removeLogical = context.readBoolean();
        this.updateEqualsMap = context.readBoolean();

        if ( context.readBoolean() ) {
            String pkgName = context.readUTF();
            String ruleName = context.readUTF();
            InternalKnowledgePackage pkg = context.getKnowledgeBase().getPackage( pkgName );
            this.ruleOrigin = pkg.getRule( ruleName );
        }
        if ( context.readBoolean() ) {
            this.tuple = context.getTerminalTupleMap().get( context.readInt() );
        }
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();

        final PropagationContext context = pctxFactory.createPropagationContext(reteEvaluator.getNextPropagationIdCounter(), PropagationContext.Type.INSERTION,
                                                                                this.ruleOrigin, (TerminalNode) (this.tuple != null ? this.tuple.getSink() : null), this.factHandle);
        reteEvaluator.getKnowledgeBase().getRete().assertObject(this.factHandle, context, reteEvaluator);
    }
}