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
package org.drools.tms.beliefsystem.simple;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.kiesession.entrypoints.NamedEntryPoint;
import org.drools.tms.TruthMaintenanceSystemEqualityKey;
import org.drools.tms.beliefsystem.BeliefSet;

import java.io.IOException;

import static org.drools.base.reteoo.PropertySpecificUtil.allSetButTraitBitMask;

public class BeliefSystemLogicalCallback extends AbstractPropagationEntry<ReteEvaluator> implements WorkingMemoryAction {

    protected InternalFactHandle handle;
    protected PropagationContext context;
    protected InternalMatch internalMatch;

    protected boolean update;
    protected boolean fullyRetract;

    public BeliefSystemLogicalCallback() {

    }

    public BeliefSystemLogicalCallback(final InternalFactHandle handle,
        final PropagationContext context,
        final InternalMatch internalMatch,
        final boolean update,
        final boolean fullyRetract) {
        this.handle = handle;
        this.context = context;
        this.internalMatch = internalMatch;
        this.update = update;
        this.fullyRetract = fullyRetract;
    }

    public BeliefSystemLogicalCallback(MarshallerReaderContext context) throws IOException {
        this.handle = context.getHandles().get( context.readLong() );
        this.context = context.getPropagationContexts().get( context.readLong() );
        this.internalMatch = (InternalMatch) context.getTerminalTupleMap().get(context.readInt());
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public boolean isFullyRetract() {
        return fullyRetract;
    }

    public void setFullyRetract(boolean fullyRetract) {
        this.fullyRetract = fullyRetract;
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        NamedEntryPoint nep = (NamedEntryPoint) handle.getEntryPoint(reteEvaluator) ;

        BeliefSet bs = ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).getBeliefSet();
        bs.setWorkingMemoryAction( null );

        if ( update ) {
            if ( !bs.isEmpty() ) {
                // We need the isEmpty check, in case the BeliefSet was made empty (due to retract) after this was scheduled
                nep.update( handle, handle.getObject(), allSetButTraitBitMask(), Object.class, null );
            }
        } else  {
            if ( fullyRetract ) {
                nep.delete(this.handle, context.getRuleOrigin(), SuperCacheFixer.asTerminalNode(this.internalMatch.getTuple()));
            } else {
                ObjectTypeConf typeConf = nep.getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( nep.getEntryPoint(), handle.getObject() );
                nep.getEntryPointNode().immediateDeleteObject( handle, context, typeConf, reteEvaluator );
            }
        }
    }
}
