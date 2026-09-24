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
package org.drools.kiesession.entrypoints;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.TypeDeclaration;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.base.TraitHelper;
import org.drools.core.common.ClassAwareObjectStore;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.EqualityKey;
import org.drools.core.common.IdentityObjectStore;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectStoreWrapper;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.kie.api.conf.KieBaseMutabilityOption;
import org.kie.api.prototype.PrototypeFactInstance;
import org.kie.api.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;
import static org.drools.base.reteoo.PropertySpecificUtil.allSetBitMask;
import static org.drools.base.reteoo.PropertySpecificUtil.calculatePositiveMask;

public class NamedEntryPoint implements InternalWorkingMemoryEntryPoint, PropertyChangeListener {

    protected static final Logger log = LoggerFactory.getLogger(NamedEntryPoint.class);

    protected static final Class<?>[] ADD_REMOVE_PROPERTY_CHANGE_LISTENER_ARG_TYPES = new Class[]{PropertyChangeListener.class};

    /** The arguments used when adding/removing a property change listener. */
    protected final Object[] addRemovePropertyChangeListenerArgs = new Object[]{this};

    private ObjectStore objectStore;

    protected transient InternalRuleBase ruleBase;

    protected EntryPointId     entryPoint;
    protected EntryPointNode entryPointNode;

    protected ReteEvaluator reteEvaluator;

    protected FactHandleFactory         factHandleFactory;
    protected PropagationContextFactory pctxFactory;

    protected ReentrantLock lock;

    protected Set<InternalFactHandle> dynamicFacts = null;

    private boolean isEqualityBehaviour = false;

    protected NamedEntryPoint() {
        lock = null;
        reteEvaluator = null;
    }

    public NamedEntryPoint(InternalRuleBase ruleBase, 
            ReteEvaluator reteEvaluator,
            FactHandleFactory factHandleFactory,
            EntryPointId entryPoint,
            EntryPointNode entryPointNode) {
        this.ruleBase = ruleBase;
        this.reteEvaluator = reteEvaluator;
        this.entryPoint = entryPoint;
        this.entryPointNode = entryPointNode;
        this.lock = reteEvaluator.getRuleSessionConfiguration().isThreadSafe() ? new ReentrantLock() : null;
        this.factHandleFactory = factHandleFactory;

        RuleBaseConfiguration conf = this.ruleBase.getRuleBaseConfiguration();
        this.pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
        this.isEqualityBehaviour = RuleBaseConfiguration.AssertBehaviour.EQUALITY.equals(conf.getAssertBehaviour());

        this.objectStore = createObjectStore(entryPoint, conf, reteEvaluator);
    }

    protected ObjectStore createObjectStore(EntryPointId entryPoint, RuleBaseConfiguration conf, ReteEvaluator reteEvaluator) {
        boolean useClassAwareStore = isEqualityBehaviour || conf.getOption(KieBaseMutabilityOption.KEY).isMutabilityEnabled();
        return useClassAwareStore ?
                new ClassAwareObjectStore( isEqualityBehaviour, this.lock ) :
                new IdentityObjectStore();
    }

    public void lock() {
        if (lock != null) {
            lock.lock();
        }
    }

    public void unlock() {
        if (lock != null) {
            lock.unlock();
        }
    }

    public void reset() {
        this.objectStore.clear();
        if (TruthMaintenanceSystemFactory.present()) {
            TruthMaintenanceSystemFactory.get().clearTruthMaintenanceSystem(this);
        }
    }

    public ObjectStore getObjectStore() {
        return this.objectStore;
    }

    public EntryPointNode getEntryPointNode() {
        return this.entryPointNode;
    }

    public FactHandleFactory getHandleFactory() {
        return factHandleFactory;
    }

    /**
     * @see org.drools.core.WorkingMemory
     */
    public FactHandle insert(final Object object) {
        return insert(object, /* Not-Dynamic */
                false,
                null,
                null);
    }

    public FactHandle insert(final Object object,
                             final boolean dynamic) {
        return insert(object, dynamic, null, null);
    }

    public FactHandle insert(final Object object,
                             final boolean dynamic,
                             final RuleImpl rule,
                             final TerminalNode terminalNode) {
        if ( object == null ) {
            // you cannot assert a null object
            return null;
        }

        try {
            this.reteEvaluator.startOperation(ReteEvaluator.InternalOperationType.INSERT);

            ObjectTypeConf typeConf = getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( this.entryPoint, object );

            final PropagationContext propagationContext = this.pctxFactory.createPropagationContext(this.reteEvaluator.getNextPropagationIdCounter(),
                    PropagationContext.Type.INSERTION,
                    rule,
                    terminalNode,
                    null,
                    entryPoint);
            if ( this.reteEvaluator.isSequential() ) {
                InternalFactHandle handle = createHandle( object, typeConf );
                propagationContext.setFactHandle(handle);
                insert( handle, object, rule, typeConf, propagationContext );
                return handle;
            }

            InternalFactHandle handle;
            try {
                lock();

                // check if the object already exists in the WM
                handle = this.objectStore.getHandleForObject( object );

                if ( typeConf.isTMSEnabled() ) {
                    if ( handle != null && handle.getEqualityKey().getStatus() == EqualityKey.STATED ) {
                        // it's already stated, so just return the handle
                        return handle;
                    }

                    handle = TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem(this).insertOnTms(object, typeConf, propagationContext, handle, this::createHandle);
                } else {
                    // TMS not enabled for this object type
                    if ( handle != null ) {
                        return handle;
                    }
                    handle = createHandle( object, typeConf );
                }

                propagationContext.setFactHandle(handle);

                // if the dynamic parameter is true or if the user declared the fact type with the meta tag:
                // @propertyChangeSupport
                if ( dynamic || typeConf.isDynamic() ) {
                    addPropertyChangeListener( handle, dynamic );
                }

                insert( handle, object, rule, typeConf, propagationContext );
            } finally {
                unlock();
            }
            return handle;
        } finally {
            this.reteEvaluator.endOperation(ReteEvaluator.InternalOperationType.INSERT);
        }

    }

    public void insert(InternalFactHandle handle) {
        Object object = handle.getObject();
        ObjectTypeConf typeConf = getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( this.entryPoint, object );
        insert( handle, object, null, null, typeConf );
    }

    public void insert(InternalFactHandle handle,
                       Object object,
                       RuleImpl rule,
                       TerminalNode terminalNode,
                       ObjectTypeConf typeConf) {
        PropagationContext pctx = pctxFactory.createPropagationContext(this.reteEvaluator.getNextPropagationIdCounter(), PropagationContext.Type.INSERTION,
                rule, terminalNode, handle, entryPoint);
        insert( handle, object, rule, typeConf, pctx );
    }

    public void insert(final InternalFactHandle handle,
                       final Object object,
                       final RuleImpl rule,
                       ObjectTypeConf typeConf,
                       PropagationContext pctx) {
        this.ruleBase.executeQueuedActions();

        this.objectStore.addHandle( handle, object );
        this.entryPointNode.assertObject( handle, pctx, typeConf, this.reteEvaluator );

        this.reteEvaluator.getRuleRuntimeEventSupport().fireObjectInserted(pctx, handle, object, this.reteEvaluator);
    }

    public FactHandle insertAsync(Object object) {
        ObjectTypeConf typeConf = getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( this.entryPoint, object );

        PropagationContext pctx = this.pctxFactory.createPropagationContext(this.reteEvaluator.getNextPropagationIdCounter(),
                PropagationContext.Type.INSERTION,
                null, null, null, entryPoint);
        InternalFactHandle handle = createHandle( object, typeConf );
        pctx.setFactHandle(handle);

        this.entryPointNode.assertObject( handle, pctx, typeConf, this.reteEvaluator );
        this.reteEvaluator.getRuleRuntimeEventSupport().fireObjectInserted(pctx, handle, object, this.reteEvaluator);
        return handle;
    }

    public void update(final FactHandle factHandle, final Object object) {
        update( (InternalFactHandle) factHandle,
                object,
                allSetBitMask(),
                Object.class,
                null );
    }

    public void update(FactHandle handle, Object object, String... modifiedProperties) {
        BitMask mask = calculateUpdateBitMask(ruleBase, object, modifiedProperties);
        update( (InternalFactHandle) handle, object, mask, object.getClass(), null);
    }

    public static BitMask calculateUpdateBitMask(InternalRuleBase ruleBase, Object object, String[] modifiedProperties) {
        String modifiedTypeName;
        List<String> accessibleProperties;
        boolean isPropertyReactive;

        if (object instanceof PrototypeFactInstance p) {
            accessibleProperties = new ArrayList<>(p.getPrototype().getFieldNames());
            modifiedTypeName = p.getPrototype().getFullName();
            isPropertyReactive = !accessibleProperties.isEmpty();
        } else {
            Class<?> modifiedClass = object.getClass();
            modifiedTypeName = modifiedClass.getName();
            TypeDeclaration typeDeclaration = ruleBase.getOrCreateExactTypeDeclaration( modifiedClass );
            isPropertyReactive = typeDeclaration.isPropertyReactive();
            accessibleProperties = isPropertyReactive ? typeDeclaration.getAccessibleProperties() : null;
        }

        return isPropertyReactive ?
                calculatePositiveMask( modifiedTypeName, asList(modifiedProperties), accessibleProperties ) :
                AllSetBitMask.get();
    }

    public void update(final FactHandle factHandle,
                       final Object object,
                       final BitMask mask,
                       final Class<?> modifiedClass,
                       final InternalMatch internalMatch) {
        update((InternalFactHandle) factHandle, object, mask, modifiedClass, internalMatch);
    }

    public InternalFactHandle update(InternalFactHandle handle,
                                     final Object object,
                                     final BitMask mask,
                                     final Class<?> modifiedClass,
                                     final InternalMatch internalMatch) {
        lock();
        try {
            this.reteEvaluator.startOperation(ReteEvaluator.InternalOperationType.UPDATE);
            try {
                this.ruleBase.executeQueuedActions();

                // the handle might have been disconnected, so reconnect if it has
                if (handle.isDisconnected()) {
                    InternalFactHandle reconnectedHandle = this.objectStore.reconnect(handle);
                    if (reconnectedHandle == null) {
                        handle.setDisconnected(false);
                        insert(handle, object, null, null, getObjectTypeConfigurationRegistry().getObjectTypeConf(object));
                        return handle;
                    }
                    handle = reconnectedHandle;
                }

                final Object originalObject = handle.getObject();
                final boolean changedObject = originalObject != object;

                if (!handle.getEntryPointId().equals( entryPoint )) {
                    throw new IllegalArgumentException("Invalid Entry Point. You updated the FactHandle on entry point '" + handle.getEntryPointId() + "' instead of '" + getEntryPointId() + "'");
                }

                if (handle.isExpired()) {
                    // let an expired event potentially (re)enters the objectStore, but make sure that it will be clear at the end of the inference cycle
                    ((DefaultEventHandle)handle).setPendingRemoveFromStore(true);
                }

                final ObjectTypeConf typeConf = changedObject ?
                        getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf(this.entryPoint, object) :
                        getObjectTypeConfigurationRegistry().getObjectTypeConf(object);

                if (changedObject || isEqualityBehaviour) {
                    this.objectStore.updateHandle(handle, object);
                }

                this.factHandleFactory.increaseFactHandleRecency(handle);

                final PropagationContext propagationContext = pctxFactory.createPropagationContext(this.reteEvaluator.getNextPropagationIdCounter(), PropagationContext.Type.MODIFICATION,
                                                                                                   internalMatch == null ? null : internalMatch.getRule(),
                                                                                                   internalMatch == null ? null : SuperCacheFixer.asTerminalNode(internalMatch.getTuple()),
                                                                                                   handle, entryPoint, mask, modifiedClass, null);

                if (typeConf.isTMSEnabled()) {
                    TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem(this).updateOnTms(handle, object, internalMatch);
                }

                beforeUpdate(handle, object, internalMatch, originalObject, propagationContext);

                update(handle, object, originalObject, typeConf, propagationContext);
            } finally {
                this.reteEvaluator.endOperation(ReteEvaluator.InternalOperationType.UPDATE);
            }
        } finally {
            unlock();
        }
        return handle;
    }

    protected void beforeUpdate(InternalFactHandle handle, Object object, InternalMatch internalMatch, Object originalObject, PropagationContext propagationContext) {
    }

    public void update(InternalFactHandle handle, Object object, Object originalObject, ObjectTypeConf typeConf, PropagationContext propagationContext) {
        this.entryPointNode.modifyObject( handle, propagationContext, typeConf, this.reteEvaluator );
        this.reteEvaluator.getRuleRuntimeEventSupport().fireObjectUpdated(propagationContext, handle, originalObject, object, this.reteEvaluator);
    }

    public void retract(final FactHandle handle) {
        delete( handle );
    }

    public void delete(final FactHandle handle) {
        delete( handle, null, null );
    }

    public void delete(final FactHandle handle, FactHandle.State fhState) {
        delete( handle, null, null, fhState );
    }

    public void delete(FactHandle factHandle,
                       RuleImpl rule,
                       TerminalNode terminalNode) {
        delete(factHandle, rule, terminalNode, FactHandle.State.ALL);
    }

    public void delete(FactHandle factHandle,
                       RuleImpl rule,
                       TerminalNode terminalNode,
                       FactHandle.State fhState) {
        if ( factHandle == null ) {
            throw new IllegalArgumentException( "FactHandle cannot be null " );
        }

        lock();
        try {
            this.reteEvaluator.startOperation(ReteEvaluator.InternalOperationType.DELETE);
            try {
                this.ruleBase.executeQueuedActions();

                InternalFactHandle handle = (InternalFactHandle) factHandle;

                if (handle.getId() == -1) {
                    // can't retract an already retracted handle
                    return;
                }

                // the handle might have been disconnected, so reconnect if it has
                if (handle.isDisconnected()) {
                    handle = this.objectStore.reconnect(handle);
                }
                if (handle == null) {
                    log.warn("The factHandle doesn't exist so cannot be deleted. " + factHandle.toExternalForm());
                    return;
                }

                if (!handle.getEntryPointId().equals( entryPoint )) {
                    throw new IllegalArgumentException("Invalid Entry Point. You updated the FactHandle on entry point '" + handle.getEntryPointId() + "' instead of '" + getEntryPointId() + "'");
                }

                EqualityKey key = handle.getEqualityKey();
                if (fhState.isStated()) {
                    deleteStated(rule, terminalNode, handle, key);
                }
                if (fhState.isLogical()) {
                    deleteLogical(key);
                }
            } finally {
                this.reteEvaluator.endOperation(ReteEvaluator.InternalOperationType.DELETE);
            }
        } finally {
            unlock();
        }
    }

    private void deleteStated( RuleImpl rule, TerminalNode terminalNode, InternalFactHandle handle, EqualityKey key ) {
        if ( key != null && key.getStatus() == EqualityKey.JUSTIFIED ) {
            return;
        }

        beforeDestroy(rule, terminalNode, handle);

        final Object object = handle.getObject();

        final ObjectTypeConf typeConf = getObjectTypeConfigurationRegistry().getObjectTypeConf( object );

        if( typeConf.isDynamic() ) {
            removePropertyChangeListener( handle, true );
        }

        PropagationContext propagationContext = delete( handle, object, typeConf, rule, terminalNode );

        deleteFromTMS( handle, key, typeConf, propagationContext );

        this.factHandleFactory.destroyFactHandle( handle );
    }

    protected void beforeDestroy(RuleImpl rule, TerminalNode terminalNode, InternalFactHandle handle) {

    }

    private void deleteFromTMS( InternalFactHandle handle, EqualityKey key, ObjectTypeConf typeConf, PropagationContext propagationContext ) {
        if ( typeConf.isTMSEnabled() && key != null ) { // key can be null if we're expiring an event that has been already deleted
            TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem(this).deleteFromTms( handle, key, propagationContext );
        }
    }

    private void deleteLogical(EqualityKey key) {
        if ( key != null && key.getStatus() == EqualityKey.JUSTIFIED ) {
            TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem(this).delete( key.getLogicalFactHandle() );
        }
    }

    @Override
    public PropagationContext delete(InternalFactHandle handle, Object object, ObjectTypeConf typeConf, RuleImpl rule, TerminalNode terminalNode) {
        return delete(handle, object, typeConf, rule, terminalNode, false);
    }

    @Override
    public PropagationContext immediateDelete(InternalFactHandle handle, Object object, ObjectTypeConf typeConf, RuleImpl rule, TerminalNode terminalNode) {
        return delete(handle, object, typeConf, rule, terminalNode, true);
    }

    private PropagationContext delete(InternalFactHandle handle, Object object, ObjectTypeConf typeConf, RuleImpl rule, TerminalNode terminalNode, boolean immediate) {
        final PropagationContext propagationContext = pctxFactory.createPropagationContext( this.reteEvaluator.getNextPropagationIdCounter(), PropagationContext.Type.DELETION,
                rule, terminalNode,
                handle, this.entryPoint );

        if (immediate) {
            this.entryPointNode.immediateDeleteObject( handle, propagationContext, typeConf, this.reteEvaluator );
        } else {
            this.entryPointNode.retractObject( handle, propagationContext, typeConf, this.reteEvaluator );
        }

        afterRetract(handle, rule, terminalNode);

        this.objectStore.removeHandle( handle );

        this.reteEvaluator.getRuleRuntimeEventSupport().fireObjectRetracted(propagationContext, handle, object, this.reteEvaluator);
        return propagationContext;
    }

    protected void afterRetract(InternalFactHandle handle, RuleImpl rule, TerminalNode terminalNode) {

    }

    public void removeFromObjectStore(InternalFactHandle handle) {
        this.objectStore.removeHandle( handle );
        ObjectTypeConf typeConf = getObjectTypeConfigurationRegistry().getObjectTypeConf( handle.getObject() );
        deleteFromTMS( handle, handle.getEqualityKey(), typeConf, null );
    }

    public void addPropertyChangeListener(final InternalFactHandle handle, final boolean dynamicFlag ) {
        Object object = handle.getObject();
        try {
            final Method method = object.getClass().getMethod( "addPropertyChangeListener",
                    NamedEntryPoint.ADD_REMOVE_PROPERTY_CHANGE_LISTENER_ARG_TYPES );

            method.invoke( object,
                    this.addRemovePropertyChangeListenerArgs );

            if( dynamicFlag ) {
                if( dynamicFacts == null ) {
                    dynamicFacts = new HashSet<>();
                }
                dynamicFacts.add( handle );
            }
        } catch ( final NoSuchMethodException e ) {
            log.error( "Warning: Method addPropertyChangeListener not found" + " on the class " + object.getClass() + " so Drools will be unable to process JavaBean" + " PropertyChangeEvents on the asserted Object" );
        } catch ( final IllegalArgumentException e ) {
            log.error( "Warning: The addPropertyChangeListener method" + " on the class " + object.getClass() + " does not take" + " a simple PropertyChangeListener argument" + " so Drools will be unable to process JavaBean"
                    + " PropertyChangeEvents on the asserted Object" );
        } catch ( final IllegalAccessException e ) {
            log.error( "Warning: The addPropertyChangeListener method" + " on the class " + object.getClass() + " is not public" + " so Drools will be unable to process JavaBean" + " PropertyChangeEvents on the asserted Object" );
        } catch ( final InvocationTargetException e ) {
            log.error( "Warning: The addPropertyChangeListener method" + " on the class " + object.getClass() + " threw an InvocationTargetException" + " so Drools will be unable to process JavaBean"
                    + " PropertyChangeEvents on the asserted Object: " + e.getMessage() );
        } catch ( final SecurityException e ) {
            log.error( "Warning: The SecurityManager controlling the class " + object.getClass() + " did not allow the lookup of a" + " addPropertyChangeListener method" + " so Drools will be unable to process JavaBean"
                    + " PropertyChangeEvents on the asserted Object: " + e.getMessage() );
        }
    }

    protected void removePropertyChangeListener(final FactHandle handle, final boolean removeFromSet ) {
        Object object = ((InternalFactHandle) handle).getObject();
        try {
            if ( dynamicFacts != null && removeFromSet ) {
                dynamicFacts.remove( handle );
            }

            if ( object != null ) {
                final Method mehod = object.getClass().getMethod( "removePropertyChangeListener",
                        NamedEntryPoint.ADD_REMOVE_PROPERTY_CHANGE_LISTENER_ARG_TYPES );

                mehod.invoke( object,
                        this.addRemovePropertyChangeListenerArgs );
            }
        } catch ( final NoSuchMethodException e ) {
            // The removePropertyChangeListener method on the class
            // was not found so Drools will be unable to
            // stop processing JavaBean PropertyChangeEvents
            // on the retracted Object
        } catch ( final IllegalArgumentException e ) {
            throw new RuntimeException( "Warning: The removePropertyChangeListener method on the class " + object.getClass() + " does not take a simple PropertyChangeListener argument so Drools will be unable to stop processing JavaBean"
                    + " PropertyChangeEvents on the retracted Object" );
        } catch ( final IllegalAccessException e ) {
            throw new RuntimeException( "Warning: The removePropertyChangeListener method on the class " + object.getClass() + " is not public so Drools will be unable to stop processing JavaBean PropertyChangeEvents on the retracted Object" );
        } catch ( final InvocationTargetException e ) {
            throw new RuntimeException( "Warning: The removePropertyChangeL istener method on the class " + object.getClass() + " threw an InvocationTargetException so Drools will be unable to stop processing JavaBean"
                    + " PropertyChangeEvents on the retracted Object: " + e.getMessage() );
        } catch ( final SecurityException e ) {
            throw new RuntimeException( "Warning: The SecurityManager controlling the class " + object.getClass() + " did not allow the lookup of a removePropertyChangeListener method so Drools will be unable to stop processing JavaBean"
                    + " PropertyChangeEvents on the retracted Object: " + e.getMessage() );
        }
    }

    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return entryPointNode.getTypeConfReg();
    }

    public InternalRuleBase getKnowledgeBase() {
        return ruleBase;
    }

    public FactHandle getFactHandle(Object object) {
        return this.objectStore.getHandleForObject( object );
    }

    public EntryPointId getEntryPoint() {
        return this.entryPoint;
    }

    @Override
    public ReteEvaluator getReteEvaluator() {
        return this.reteEvaluator;
    }

    public Object getObject(FactHandle factHandle) {
        return this.objectStore.getObjectForHandle((InternalFactHandle)factHandle);
    }

    @SuppressWarnings("unchecked")
    public <T extends FactHandle> Collection<T> getFactHandles() {
        return new ObjectStoreWrapper( this.objectStore, null, ObjectStoreWrapper.FACT_HANDLE );
    }

    @SuppressWarnings("unchecked")
    public <T extends FactHandle> Collection<T> getFactHandles(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( this.objectStore, filter, ObjectStoreWrapper.FACT_HANDLE );
    }

    public Collection<?> getObjects() {
        return new ObjectStoreWrapper( this.objectStore, null, ObjectStoreWrapper.OBJECT );
    }

    public Collection<?> getObjects(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( this.objectStore, filter, ObjectStoreWrapper.OBJECT );
    }

    public String getEntryPointId() {
        return this.entryPoint.getEntryPointId();
    }

    public long getFactCount() {
        return this.objectStore.size();
    }

    private InternalFactHandle createHandle(final Object object,
                                            ObjectTypeConf typeConf) {
        return this.factHandleFactory.newFactHandle( object, typeConf, this.reteEvaluator, this );
    }

    public void propertyChange(final PropertyChangeEvent event) {
        final Object object = event.getSource();
        FactHandle handle = getFactHandle( object );
        if ( handle == null ) {
            throw new RuntimeException( "Update error: handle not found for object: " + object + ". Is it in the working memory?" );
        }
        update( handle, object, event.getPropertyName() );
    }

    public void dispose() {
        if ( dynamicFacts != null ) {
            // first we check for facts that were inserted into the working memory
            // using the old API and setting a per instance dynamic flag and remove the
            // session from the listeners list in the bean
            for ( InternalFactHandle handle : dynamicFacts ) {
                removePropertyChangeListener( handle, false );
            }
            dynamicFacts = null;
        }

        for ( ObjectTypeConf conf : getObjectTypeConfigurationRegistry().values() ) {
            // then, we check if any of the object types were configured using the
            // @propertyChangeSupport annotation, and clean them up
            if ( conf.isDynamic() ) {
                // it is enough to iterate the facts on the concrete object type nodes
                // only, as the facts will always be in their concrete object type nodes
                // even if they were also asserted into higher level OTNs as well
                ObjectTypeNode otn = conf.getConcreteObjectTypeNode();
                if (otn != null) {
                    Iterator<InternalFactHandle> it = otn.getFactHandlesIterator((InternalWorkingMemory) reteEvaluator);
                    while (it.hasNext()) {
                        removePropertyChangeListener(it.next(), false);
                    }
                }
            }
        }

        reset();
    }

    public TraitHelper getTraitHelper() {
        throw new UnsupportedOperationException("In order to use traits you must add the drools-traits module to your classpath");
    }

    public PropagationContextFactory getPctxFactory() {
        return pctxFactory;
    }

    @Override
    public String toString() {
        return entryPoint.toString();
    }

    private Object ruleUnit;

    @Override
    public Object getRuleUnit() {
        return ruleUnit;
    }

    @Override
    public void setRuleUnit(Object ruleUnit) {
        this.ruleUnit = ruleUnit;
    }
}
