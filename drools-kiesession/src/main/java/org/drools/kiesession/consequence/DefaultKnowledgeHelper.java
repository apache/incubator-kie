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
package org.drools.kiesession.consequence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.drools.base.beliefsystem.Mode;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.traits.CoreWrapper;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.rule.Declaration;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.WorkingMemory;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalRuleFlowGroup;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.core.process.AbstractProcessContext;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.util.bitmask.BitMask;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.NodeInstanceContainer;
import org.kie.api.runtime.process.ProcessContext;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;

import static org.drools.base.reteoo.PropertySpecificUtil.allSetButTraitBitMask;
import static org.drools.base.reteoo.PropertySpecificUtil.onlyTraitBitSetMask;

public class DefaultKnowledgeHelper implements KnowledgeHelper, Externalizable {

    private static final long                         serialVersionUID = 510l;

    protected InternalMatch internalMatch;
    private Tuple tuple;

    protected ReteEvaluator reteEvaluator;
    private StatefulKnowledgeSessionForRHS wrappedEvaluator;

    private KnowledgeHelper tmsKnowledgeHelper;

    public DefaultKnowledgeHelper() { }

    public DefaultKnowledgeHelper(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        internalMatch = (InternalMatch) in.readObject();
        tuple = (TupleImpl) in.readObject();
        reteEvaluator = (ReteEvaluator) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(internalMatch);
        out.writeObject( tuple );
        out.writeObject(reteEvaluator);
    }

    public void setActivation(final InternalMatch internalMatch) {
        this.internalMatch = internalMatch;
        this.tuple = internalMatch.getTuple();
    }

    public InternalMatch getActivation() {
        return internalMatch;
    }

    public void reset() {
        this.internalMatch = null;
        this.tuple = null;
    }

    public void blockMatch(Match act) {
        executeOnTMS().blockMatch(act);
    }

    public void unblockAllMatches(Match act) {
        executeOnTMS().unblockAllMatches(act);
    }

    public FactHandle insertAsync( final Object object ) {
        return toStatefulKnowledgeSession().insertAsync( object );
    }

    public FactHandle insert(final Object object) {
        return insert( object, false );
    }

    @Deprecated(since = "5.0.1", forRemoval = false)
    public FactHandle insert(final Object object, final boolean dynamic) {
        return ((InternalWorkingMemoryEntryPoint) this.reteEvaluator.getDefaultEntryPoint())
                .insert(object, dynamic, this.internalMatch.getRule(), SuperCacheFixer.asTerminalNode(this.internalMatch.getTuple()));
    }

    @Override
    public FactHandle insertLogical(Object object, Mode belief) {
        return insertLogical( object, belief );
    }

    @Override
    public FactHandle insertLogical(Object object, Mode... beliefs) {
        return insertLogical( object, beliefs );
    }

    @Override
    public FactHandle insertLogical(final Object object) {
        return insertLogical( object, (Object) null );
    }

    @Override
    public FactHandle insertLogical(Object object, Object value) {
        return executeOnTMS().insertLogical(object, value);
    }

    @Override
    public FactHandle insertLogical(EntryPoint ep, Object object) {
        return executeOnTMS().insertLogical(ep, object);
    }

    public FactHandle bolster( final Object object ) {
        return bolster( object, null );
    }

    public FactHandle bolster( final Object object, final Object value ) {
        return executeOnTMS().bolster(object, value);
    }

    private KnowledgeHelper executeOnTMS() {
        if (!TruthMaintenanceSystemFactory.present()) {
            TruthMaintenanceSystemFactory.throwExceptionForMissingTms();
        }
        if (tmsKnowledgeHelper == null) {
            reteEvaluator.enableTMS();
            tmsKnowledgeHelper = reteEvaluator.createKnowledgeHelper();
        }
        if (internalMatch != tmsKnowledgeHelper.getActivation()) {
            tmsKnowledgeHelper.setActivation(internalMatch);
        }
        return tmsKnowledgeHelper;
    }

    public void cancelMatch(Match act) {
        InternalMatch match = (InternalMatch) act;
        ((RuleTerminalNode)match.getTerminalNode()).cancelMatch( match);
    }

    public FactHandle getFactHandle(Object object) {
        FactHandle handle = getFactHandleFromWM( object );

        if ( handle == null ) {
            if ( object instanceof CoreWrapper ) {
                handle = getFactHandleFromWM( ((CoreWrapper) object).getCore() );
            }
            if ( handle == null && reteEvaluator.getKnowledgeBase().getRuleBaseConfiguration().getAssertBehaviour() == RuleBaseConfiguration.AssertBehaviour.EQUALITY ) {
                FactHandle modifiedFh = tuple.getFactHandle();
                while (modifiedFh == null || modifiedFh.getObject() != object) {
                    tuple = tuple.getParent();
                    modifiedFh = tuple.getFactHandle();
                }
                handle = modifiedFh;
            }
            if ( handle == null ) {
                throw new RuntimeException( "Update error: handle not found for object: " + object + ". Is it in the working memory?" );
            }
        }
        return handle;
    }
    
    public FactHandle getFactHandle(FactHandle handle) {
        FactHandle handleFromWM = getFactHandleFromWM( handle.getObject() );
        return handleFromWM != null ? handleFromWM : handle;
    }
    
    public void update(final FactHandle handle,
                       final Object newObject){
        InternalFactHandle h = (InternalFactHandle) handle;
        h.getEntryPoint(reteEvaluator).update( h,
                                  newObject,
                                  onlyTraitBitSetMask(),
                                  newObject.getClass(),
                                  this.internalMatch);
    }

    public void update(final FactHandle handle) {
        update( handle, Long.MAX_VALUE );
    }

    public void update( final FactHandle handle, BitMask mask, Class<?> modifiedClass ) {
        InternalFactHandle h = (InternalFactHandle) handle;

        ((InternalWorkingMemoryEntryPoint) h.getEntryPoint(reteEvaluator)).update( h,
                                                                      ((InternalFactHandle)handle).getObject(),
                                                                      mask,
                                                                      modifiedClass,
                                                                      this.internalMatch);
        if ( h.isTraitOrTraitable() ) {
            toStatefulKnowledgeSession().updateTraits( h, mask, modifiedClass, this.internalMatch);
        }
    }

    public void update( Object object ) {
        update(object, allSetButTraitBitMask(), Object.class);
    }

    public void update(Object object, BitMask mask, Class<?> modifiedClass) {
        update(getFactHandle(object), mask, modifiedClass);
    }

    public void retract(Object object) {
        delete( getFactHandle( object ) );
    }

    public void retract(final FactHandle handle) {
        delete( handle );
    }

    public void delete(Object object) {
        delete( getFactHandle( object ) );
    }

    public void delete(Object object, FactHandle.State fhState) {
        delete( getFactHandle( object ), fhState );
    }

    public void delete(FactHandle handle) {
        delete(handle, FactHandle.State.ALL);
    }

    public void delete(FactHandle handle, FactHandle.State fhState ) {
        Object o = ((InternalFactHandle) handle).getObject();
        if ( ((InternalFactHandle) handle).isTraiting() ) {
            delete( ((Thing) o).getCore() );
            return;
        }

        ((InternalFactHandle) handle).getEntryPoint(reteEvaluator).delete(handle,
                                                             this.internalMatch.getRule(),
                                                             SuperCacheFixer.asTerminalNode(this.internalMatch.getTuple()),
                                                             fhState);
    }

    public RuleImpl getRule() {
        return this.internalMatch.getRule();
    }

    public Tuple getTuple() {
        return this.tuple;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return ((RuleTerminalNode)this.tuple.getSink()).getRequiredDeclarations();
    }

    public WorkingMemory getWorkingMemory() {
        return toStatefulKnowledgeSession();
    }

    public KieRuntime getKnowledgeRuntime() {
        return toStatefulKnowledgeSession();
    }

    public StatefulKnowledgeSessionForRHS toStatefulKnowledgeSession() {
        if (wrappedEvaluator != null) {
            return wrappedEvaluator;
        }
        if (reteEvaluator instanceof StatefulKnowledgeSessionImpl) {
            wrappedEvaluator = new StatefulKnowledgeSessionForRHS((StatefulKnowledgeSessionImpl) reteEvaluator);
            return wrappedEvaluator;
        }
        throw new UnsupportedOperationException("Operation not supported when using a lightweight session");
    }

    public InternalMatch getMatch() {
        return this.internalMatch;
    }

    public void setFocus(final String focus) {
        toStatefulKnowledgeSession().setFocus( focus );
    }

    public void activateRuleFlowGroup(final String name) {
        toStatefulKnowledgeSession().getAgenda().getRuleFlowGroup( name ).activate();
    }

    public Object get(final Declaration declaration) {
        return declaration.getValue(reteEvaluator, tuple );
    }

    public Declaration getDeclaration(final String identifier) {
        return this.internalMatch.getTerminalNode().getSubRule().getOuterDeclarations().get(identifier);
    }

    public void halt() {
        this.toStatefulKnowledgeSession().halt();
    }

    public EntryPoint getEntryPoint(String id) {
        return this.reteEvaluator.getEntryPoint(id);
    }

    public Channel getChannel(String id) {
        return toStatefulKnowledgeSession().getChannels().get(id);
    }

    public Map<String, Channel> getChannels() {
        return Collections.unmodifiableMap( toStatefulKnowledgeSession().getChannels() );
    }

    protected InternalFactHandle getFactHandleFromWM(final Object object) {
        return getFactHandleFromWM(reteEvaluator, object);
    }

    public static InternalFactHandle getFactHandleFromWM(ReteEvaluator reteEvaluator, final Object object) {
        for ( EntryPoint ep : reteEvaluator.getEntryPoints() ) {
            InternalFactHandle handle = (InternalFactHandle) ep.getFactHandle( object );
            if( handle != null ) {
                return handle;
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getContext(Class<T> contextClass) {
        if (ProcessContext.class.equals(contextClass)) {
            String ruleflowGroupName = getMatch().getRule().getRuleFlowGroup();
            if (ruleflowGroupName != null) {
                Map<Object, String> nodeInstances = ((InternalRuleFlowGroup) toStatefulKnowledgeSession().getAgenda().getRuleFlowGroup(ruleflowGroupName)).getNodeInstances();
                if (!nodeInstances.isEmpty()) {
                    if (nodeInstances.size() > 1) {
                        // TODO
                        throw new UnsupportedOperationException(
                            "Not supporting multiple node instances for the same ruleflow group");
                    }
                    Map.Entry<Object, String> entry = nodeInstances.entrySet().iterator().next();
                    ProcessInstance processInstance = toStatefulKnowledgeSession().getProcessInstance((String) entry.getKey());
                    AbstractProcessContext context = createProcessContext();
                    context.setProcessInstance(processInstance);
                    String nodeInstance = entry.getValue();
                    String[] nodeInstanceIds = nodeInstance.split(":");
                    NodeInstanceContainer container = (WorkflowProcessInstance) processInstance;
                    for (int i = 0; i < nodeInstanceIds.length; i++) {
                        for (NodeInstance subNodeInstance: container.getNodeInstances()) {
                            if ( sameNodeInstance( subNodeInstance, nodeInstanceIds[i] ) ) {
                                if (i == nodeInstanceIds.length - 1) {
                                    context.setNodeInstance(subNodeInstance);
                                    break;
                                } else {
                                    container = (NodeInstanceContainer) subNodeInstance;
                                }
                            }
                        }
                    }
                    return (T) context;
                }
            }
        }
        return null;
    }

    protected AbstractProcessContext createProcessContext() {
        return new org.drools.core.process.ProcessContext(toStatefulKnowledgeSession().getKnowledgeRuntime());
    }

    protected boolean sameNodeInstance( NodeInstance subNodeInstance, String nodeInstanceId ) {
        return subNodeInstance.getId().equals( nodeInstanceId );
    }

    public KieRuntime getKieRuntime() {
        return getKnowledgeRuntime();
    }


    /* Trait helper methods */

    public <T, K> T don( Thing<K> core, Class<T> trait, boolean logical, Mode... modes ) {
        return don( core.getCore(), trait, logical, modes );
    }

    public <T, K> T don( K core, Class<T> trait ) {
        return don( core, trait, false );
    }

    public <T, K> T don( Thing<K> core, Class<T> trait ) {
        return don( core.getCore(), trait );
    }

    public <T, K> T don( K core, Collection<Class<? extends Thing>> traits ) {
        return don( core, traits, false );
    }

    public <T,K> Thing<K> shed( Thing<K> thing, Class<T> trait ) {
        return shed( (TraitableBean<K, ? extends TraitableBean>) thing.getCore(), trait );
    }

    public <T, K> T don( K core, Collection<Class<? extends Thing>> traits, Mode... modes ) {
        return don( core, traits, true, modes );
    }

    public <T, K> T don( K core, Collection<Class<? extends Thing>> traits, boolean logical ) {
        return don( core, traits, logical, null );
    }

    public <T, K> T don( K core, Class<T> trait, boolean logical ) {
        return don( core, trait, logical, null );
    }

    public <T, K> T don( K core, Class<T> trait, Mode... modes ) {
        return don( core, trait, true, modes );
    }

    @Override
    public <T, K, X extends TraitableBean> Thing<K> shed( TraitableBean<K, X> core, Class<T> trait ) {
        return toStatefulKnowledgeSession().shed(this.internalMatch, core, trait);
    }

    private <T, K> T don( K core, Collection<Class<? extends Thing>> traits, boolean b, Mode... modes ) {
        return toStatefulKnowledgeSession().don(this.internalMatch, core, traits, b, modes);
    }

    private <T, K> T don( K core, Class<T> trait, boolean b, Mode... modes ) {
        return toStatefulKnowledgeSession().don(this.internalMatch, core, trait, b, modes);
    }

    public ClassLoader getProjectClassLoader() {
        return ((InternalKnowledgeBase)getKieRuntime().getKieBase()).getRootClassLoader();
    }
}
