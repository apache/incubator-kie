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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

import org.drools.core.RuleBaseConfiguration.AssertBehaviour;
import org.drools.base.beliefsystem.Mode;
import org.drools.core.common.ClassAwareObjectStore;
import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.Tuple;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.util.LinkedList;
import org.drools.tms.agenda.TruthMaintenanceSystemInternalMatch;
import org.drools.tms.beliefsystem.BeliefSet;
import org.drools.tms.beliefsystem.BeliefSystem;
import org.drools.tms.beliefsystem.BeliefSystemMode;
import org.drools.tms.beliefsystem.ModedAssertion;
import org.drools.tms.beliefsystem.jtms.JTMSBeliefSetImpl;
import org.drools.tms.util.CustomKeyTransformerHashMap;
import org.kie.api.runtime.rule.FactHandle;

public class TruthMaintenanceSystemImpl implements TruthMaintenanceSystem {

    private final InternalWorkingMemoryEntryPoint ep;

    private final ObjectTypeConfigurationRegistry typeConfReg;

    private final Map<EqualityKey, EqualityKey> equalityKeyMap;

    private final BeliefSystem defaultBeliefSystem;

    private final AssertBehaviour assertBehaviour;

    public TruthMaintenanceSystemImpl(InternalWorkingMemoryEntryPoint ep) {
        this.ep = ep;

        this.assertBehaviour = ep.getKnowledgeBase().getRuleBaseConfiguration().getAssertBehaviour();

        this.typeConfReg = ep.getObjectTypeConfigurationRegistry();

        this.equalityKeyMap = new CustomKeyTransformerHashMap<>(EqualityKeyPlaceholder::transformEqualityKey);

        this.defaultBeliefSystem = BeliefSystemFactory.createBeliefSystem(ep.getReteEvaluator().getRuleSessionConfiguration().getBeliefSystemType(), ep, this);
    }

    private static class EqualityKeyPlaceholder {
        private final Object object;

        private EqualityKeyPlaceholder(final Object object) {
            this.object = object;
        }

        private static Object transformEqualityKey(Object o) {
            return o instanceof EqualityKey ? o : new EqualityKeyPlaceholder(o);
        }

        @Override
        public int hashCode() {
            return object.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            // notice it switches it to always use b, the EqualityKey.
            return this == other || other.equals( object );
        }
    }

    @Override
    public int getEqualityKeysSize() {
        return equalityKeyMap.size();
    }

    @Override
    public Collection<EqualityKey> getEqualityKeys() {
        return equalityKeyMap.values();
    }

    @Override
    public void put(final EqualityKey key) {
        this.equalityKeyMap.put( key, key );
    }

    @Override
    public InternalFactHandle insertPositive(Object object, InternalMatch internalMatch) {
        return insert(object, JTMSBeliefSetImpl.MODE.POSITIVE.getId(), internalMatch);
    }

    @Override
    public InternalFactHandle insert(Object object, Object tmsValue, InternalMatch internalMatch) {
        ObjectTypeConf typeConf = typeConfReg.getOrCreateObjectTypeConf( ep.getEntryPoint(), object );
        if ( !typeConf.isTMSEnabled()) {
            enableTMS(object, typeConf);
        }

        // get the key for other "equal" objects, returns null if none exist
        EqualityKey key = get(object);

        InternalFactHandle fh;
        if ( key == null ) {
            // no EqualityKey exits, so we construct one. We know it can only be justified.
            fh =  ep.getHandleFactory().newFactHandle(object, typeConf, ep.getReteEvaluator(), ep );
            key = new TruthMaintenanceSystemEqualityKey( fh, EqualityKey.JUSTIFIED );
            fh.setEqualityKey( key );
            put(key);
        } else {
            fh = key.getLogicalFactHandle();
            if ( fh == null ) {
                // The EqualityKey exists, but this is the first logical object in the key.
                fh = ep.getHandleFactory().newFactHandle(object, typeConf, ep.getReteEvaluator(), ep );
                key.setLogicalFactHandle( fh );
                fh.setEqualityKey( key );
            }
        }

        // Any logical propagations are handled via the TMS.addLogicalDependency
        return addLogicalDependency(fh, object, tmsValue, (TruthMaintenanceSystemInternalMatch) internalMatch, typeConf, false);
    }

    @Override
    public void delete(FactHandle fh) {
        if ( fh == null ) {
            return;
        }
        InternalFactHandle ifh = (InternalFactHandle) fh;
        // This will clear out the logical entries for the FH. However the FH and EqualityKey remain, if it's stated

        // Update the equality key, which maintains a list of stated FactHandles
        final EqualityKey key = ifh.getEqualityKey();

        if ( key.getLogicalFactHandle() != fh ) {
            throw new IllegalArgumentException( "The FactHandle did not originate from TMS : " + fh);
        }

        final PropagationContext propagationContext = ep.getPctxFactory().createPropagationContext( ep.getReteEvaluator().getNextPropagationIdCounter(),
                                                                                                    PropagationContext.Type.DELETION,
                                                                                                    null, null, ifh, ep.getEntryPoint());
        BeliefSet beliefSet = ((TruthMaintenanceSystemEqualityKey)key).getBeliefSet();
        if ( beliefSet != null && !beliefSet.isEmpty() ) {
            beliefSet.cancel(propagationContext);
        }
    }

    @Override
    public EqualityKey get(final Object object) {
        EqualityKey key = this.equalityKeyMap.get( object );

        if ( key == null && assertBehaviour == AssertBehaviour.EQUALITY ) {
            // Edge case: another object X, equivalent (equals+hashcode) to "object" Y
            // has been previously stated. However, if X is a subclass of Y, TMS
            // may have not been enabled yet, and key would be null.
            InternalFactHandle fh = ep.getObjectStore().getHandleForObject(object);
            if ( fh != null ) {
                key = fh.getEqualityKey();
                if ( key == null ) {
                    // we use the FH's Object here, not the inserted object
                    ObjectTypeConf typeC = this.typeConfReg.getOrCreateObjectTypeConf( ep.getEntryPoint(), fh.getObject() );
                    enableTMS( fh.getObject(), typeC );
                    key = fh.getEqualityKey();
                }
            }
        }

        return key;
    }

    @Override
    public void remove(final EqualityKey key) {
        this.equalityKeyMap.remove( key );
    }

    /**
     * Adds a justification for the FactHandle to the justifiedMap.
     */
    @Override
    public void readLogicalDependency(final InternalFactHandle handle,
                                      final Object object,
                                      final Object value,
                                      final InternalMatch internalMatch,
                                      final ObjectTypeConf typeConf) {
        addLogicalDependency(handle, object, value, (TruthMaintenanceSystemInternalMatch) internalMatch, typeConf, true);
    }

    private InternalFactHandle addLogicalDependency(final InternalFactHandle handle,
                                                    final Object object,
                                                    final Object tmsValue,
                                                    final TruthMaintenanceSystemInternalMatch activation,
                                                    final ObjectTypeConf typeConf,
                                                    final boolean read) {
        BeliefSystem beliefSystem = defaultBeliefSystem;
        if (tmsValue instanceof Mode & !(tmsValue instanceof SimpleMode)) {
            BeliefSystemMode mode = (BeliefSystemMode) tmsValue;
            beliefSystem = mode.getBeliefSystem();
        }

        BeliefSet beliefSet = ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).getBeliefSet();
        if ( beliefSet == null ) {
            beliefSet = beliefSystem.newBeliefSet( handle );
            ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).setBeliefSet( beliefSet );
        }

        final LogicalDependency node = beliefSystem.newLogicalDependency( activation, beliefSet, object, tmsValue );
        activation.getRule().setHasLogicalDependency( true );

        activation.addLogicalDependency( node );

        if ( read ) {
            // used when deserialising
            beliefSystem.read( node, beliefSet, activation.getPropagationContext(), typeConf );
        } else {
            beliefSet = beliefSystem.insert( node, beliefSet, activation.getPropagationContext(), typeConf );
        }
        return beliefSet.getFactHandle();
    }

    @Override
    public void clear() {
        this.equalityKeyMap.clear();
    }

    public BeliefSystem getBeliefSystem() {
        return defaultBeliefSystem;
    }

    /**
     * TMS will be automatically enabled when the first logical insert happens.
     *
     * We will take all the already asserted objects of the same type and initialize
     * the equality map.
     *
     * @param object the logically inserted object.
     * @param conf the type's configuration.
     */
    private void enableTMS(Object object, ObjectTypeConf conf) {
        Iterator<InternalFactHandle> it = ep.getObjectStore().iterateFactHandles(ClassAwareObjectStore.getActualClass(object));

        while (it.hasNext()) {
            InternalFactHandle handle = it.next();
            if (handle != null && handle.getEqualityKey() == null) {
                EqualityKey key = new TruthMaintenanceSystemEqualityKey(handle);
                handle.setEqualityKey(key);
                key.setStatus(EqualityKey.STATED);
                put(key);
            }
        }

        // Enable TMS for this type.
        conf.enableTMS();
    }

    @Override
    public InternalFactHandle insertOnTms(Object object, ObjectTypeConf typeConf, PropagationContext propagationContext,
                                          InternalFactHandle handle, BiFunction<Object, ObjectTypeConf, InternalFactHandle> fhFactory) {
        EqualityKey key = get(object);

        if ( handle != null && key != null && key.getStatus() == EqualityKey.JUSTIFIED) {
            // The justified set needs to be staged, before we can continue with the stated insert
            BeliefSet bs = ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).getBeliefSet();
            bs.getBeliefSystem().stage(propagationContext, bs ); // staging will set it's status to stated
        }

        handle = fhFactory.apply(object, typeConf); // we know the handle is null
        if ( key == null ) {
            key = new TruthMaintenanceSystemEqualityKey(handle, EqualityKey.STATED  );
            put( key );
        } else {
            key.addFactHandle(handle);
        }
        handle.setEqualityKey( key );
        return handle;
    }

    @Override
    public void updateOnTms(InternalFactHandle handle, Object object, InternalMatch internalMatch) {
        EqualityKey newKey = get(object);
        EqualityKey oldKey = handle.getEqualityKey();

        if ((oldKey.getStatus() == EqualityKey.JUSTIFIED || ((TruthMaintenanceSystemEqualityKey)oldKey).getBeliefSet() != null) && newKey != oldKey) {
            // Mixed stated and justified, we cannot have updates untill we figure out how to use this.
            throw new IllegalStateException("Currently we cannot modify something that has mixed stated and justified equal objects. " +
                                            "Rule " + (internalMatch == null ? "" : internalMatch.getRule().getName()) + " attempted an illegal operation");
        }

        if (newKey == null) {
            oldKey.removeFactHandle(handle);
            newKey = new TruthMaintenanceSystemEqualityKey(handle, EqualityKey.STATED); // updates are always stated
            handle.setEqualityKey(newKey);
            put(newKey);
        } else if (newKey != oldKey) {
            oldKey.removeFactHandle(handle);
            handle.setEqualityKey(newKey);
            newKey.addFactHandle(handle);
        }

        // If the old equality key is now empty, and no justified entries, remove it
        if (oldKey.isEmpty() && oldKey.getLogicalFactHandle() == null) {
            remove(oldKey);
        }
    }

    @Override
    public void deleteFromTms(InternalFactHandle handle, EqualityKey key, PropagationContext propagationContext ) {
        // Update the equality key, which maintains a list of stated FactHandles
        key.removeFactHandle( handle );
        handle.setEqualityKey( null );

        // If the equality key is now empty, then remove it, as it's no longer state either
        if ( key.isEmpty() && key.getLogicalFactHandle() == null ) {
            remove( key );
        } else if ( key.getLogicalFactHandle() != null ) {
            // The justified set can be unstaged, now that the last stated has been deleted
            final InternalFactHandle justifiedHandle = key.getLogicalFactHandle();
            BeliefSet bs = ((TruthMaintenanceSystemEqualityKey)justifiedHandle.getEqualityKey()).getBeliefSet();
            bs.getBeliefSystem().unstage( propagationContext, bs );
        }
    }

    public static <M extends ModedAssertion<M>> void removeLogicalDependencies(TruthMaintenanceSystemInternalMatch<M> activation) {
        final LinkedList<LogicalDependency<M>> list = activation.getLogicalDependencies();
        if ( list == null || list.isEmpty() ) {
            return;
        }

        PropagationContext context = ((Tuple)activation).findMostRecentPropagationContext();

        for ( LogicalDependency<M> node = list.getFirst(); node != null; node = node.getNext() ) {
            removeLogicalDependency( node, context );
        }
        activation.setLogicalDependencies( null );
    }

    public static <M extends ModedAssertion<M>> void removeLogicalDependency(final LogicalDependency<M> node, final PropagationContext context) {
        final BeliefSet<M> beliefSet = ( BeliefSet ) node.getJustified();
        beliefSet.getBeliefSystem().delete( node, beliefSet, context );
    }
}
