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
package org.drools.core.reteoo;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Pattern;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.InternalRuleBase;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.actions.Delete;
import org.drools.core.phreak.actions.Insert;
import org.drools.core.phreak.actions.Update;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.BitMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node that is an entry point into the Rete network.
 *
 * As we move the design to support network partitions and concurrent processing
 * of parts of the network, we also need to support multiple, independent entry
 * points and this class represents that.
 *
 * It replaces the function of the Rete Node class in previous designs.
 *
 * @see ObjectTypeNode
 */
public class EntryPointNode extends ObjectSource implements ObjectSink {


    private static final long               serialVersionUID = 510l;

    protected static final transient Logger log = LoggerFactory.getLogger(EntryPointNode.class);

    /**
     * The entry point ID for this node
     */
    private EntryPointId                      entryPoint;

    /**
     * The object type nodes under this node
     */
    protected Map<ObjectType, ObjectTypeNode> objectTypeNodes;

    protected ObjectTypeNode queryNode;

    private ObjectTypeNode activationNode;

    private ObjectTypeConfigurationRegistry typeConfReg;

    private boolean parallelExecution = false;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public EntryPointNode() {

    }

    public EntryPointNode(final int id,
                          final ObjectSource objectSource,
                          final BuildContext context) {
        this( id,
              context.getPartitionId(),
              objectSource,
              context.getCurrentEntryPoint() ); // irrelevant for this node, since it overrides sink management
    }

    public EntryPointNode(final int id,
                          final RuleBasePartitionId partitionId,
                          final ObjectSource objectSource,
                          final EntryPointId entryPoint) {
        super( id,
               partitionId,
               objectSource,
               999,
               999); // irrelevant for this node, since it overrides sink management
        this.entryPoint = entryPoint;
        this.objectTypeNodes = new ConcurrentHashMap<>();

        hashcode = calculateHashCode();
        typeConfReg = new ObjectTypeConfigurationRegistry( objectSource.getRuleBase() );
    }

    // ------------------------------------------------------------
    // Instance methods
    // ------------------------------------------------------------

    public void setupParallelExecution(InternalRuleBase kbase) {
        parallelExecution = true;
    }

    public ObjectTypeConfigurationRegistry getTypeConfReg() {
        return typeConfReg;
    }

    public int getType() {
        return NodeTypeEnums.EntryPointNode;
    }

    /**
     * @return the entryPoint
     */
    public EntryPointId getEntryPoint() {
        return entryPoint;
    }

    public ObjectTypeNode getQueryNode() {
        if ( queryNode == null ) {
            this.queryNode = objectTypeNodes.get( ClassObjectType.DroolsQuery_ObjectType );
        }
        return this.queryNode;
    }

    public void assertActivation(final InternalFactHandle factHandle,
                            final PropagationContext context,
                            final ReteEvaluator reteEvaluator) {
        if ( activationNode == null ) {
            this.activationNode = objectTypeNodes.get( ClassObjectType.Match_ObjectType );
        }

        if ( activationNode != null ) {
            // There may be no queries defined
            this.activationNode.propagateAssert(factHandle, context, reteEvaluator);
        }
    }

    public void retractActivation(final InternalFactHandle factHandle,
                            final PropagationContext context,
                            final ReteEvaluator reteEvaluator) {
        if ( activationNode == null ) {
            this.activationNode = objectTypeNodes.get( ClassObjectType.Match_ObjectType );
        }

        if ( activationNode != null ) {
            // There may be no queries defined
            this.activationNode.retractObject(factHandle, context, reteEvaluator);
        }
    }

    public void modifyActivation(final InternalFactHandle factHandle,
                            final PropagationContext context,
                            final ReteEvaluator reteEvaluator) {
         if ( activationNode == null ) {
             this.activationNode = objectTypeNodes.get( ClassObjectType.Match_ObjectType );
         }

         if ( activationNode != null ) {
             ModifyPreviousTuples modifyPreviousTuples = new ModifyPreviousTuples( factHandle.detachLinkedTuples() );

             // There may be no queries defined
             this.activationNode.modifyObject( factHandle, modifyPreviousTuples, context, reteEvaluator );
             modifyPreviousTuples.retractTuples(context, reteEvaluator);
         }

     }

    public void assertObject(final InternalFactHandle handle,
                             final PropagationContext context,
                             final ObjectTypeConf objectTypeConf,
                             final ReteEvaluator reteEvaluator) {
        if ( log.isTraceEnabled() ) {
            log.trace("Insert {}", handle.toString());
        }

        if ( parallelExecution || !reteEvaluator.isThreadSafe() ) {
            // In case of parallel execution the CompositePartitionAwareObjectSinkAdapter
            // used by the OTNs will take care of enqueueing this insertion on the propagation queues
            // of the different agendas
            Insert.execute( handle, context, reteEvaluator, objectTypeConf );
        } else {
            reteEvaluator.addPropagation( new Insert( handle, context, reteEvaluator, objectTypeConf ) );
        }
    }


    public void modifyObject(final InternalFactHandle handle,
                             final PropagationContext pctx,
                             final ObjectTypeConf objectTypeConf,
                             final ReteEvaluator reteEvaluator) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Update {}", handle.toString()  );
        }

        if (reteEvaluator.isThreadSafe()) {
            reteEvaluator.addPropagation( new Update( handle, pctx, objectTypeConf ) );
        } else {
            Update.execute( handle, pctx, objectTypeConf, reteEvaluator );
        }
    }

    public static void removeRightTuplesMatchingOTN( PropagationContext pctx, ReteEvaluator reteEvaluator, ModifyPreviousTuples modifyPreviousTuples, ObjectTypeNode node, int partition ) {
        // remove any right tuples that matches the current OTN before continue the modify on the next OTN cache entry
        TupleImpl rightTuple = modifyPreviousTuples.peekRightTuple(partition);
        while ( rightTuple != null &&
                ((BaseNode) rightTuple.getSink()).getObjectTypeNode() == node ) {
            modifyPreviousTuples.removeRightTuple(partition);

            modifyPreviousTuples.doRightDelete(pctx, reteEvaluator, rightTuple);

            rightTuple = modifyPreviousTuples.peekRightTuple(partition);
        }


        while ( true ) {
            TupleImpl      leftTuple = modifyPreviousTuples.peekLeftTuple(partition);
            ObjectTypeNode otn       = null;
            if (leftTuple != null) {
                Sink leftTupleSink = leftTuple.getSink();
                if (NodeTypeEnums.isTerminalNode(leftTupleSink)) {
                    otn = ((RuleTerminalNode)leftTupleSink).getObjectTypeNode();
                } else {
                    otn = ((LeftTupleSource)leftTupleSink).getLeftTupleSource().getObjectTypeNode();
                }
            }

            if ( otn == node ) {
                modifyPreviousTuples.removeLeftTuple(partition);
                modifyPreviousTuples.doDeleteObject( pctx, reteEvaluator, leftTuple );
            } else {
                break;
            }
        }
    }

    public void modifyObject(InternalFactHandle factHandle,
                                   ModifyPreviousTuples modifyPreviousTuples,
                                   PropagationContext context,
                                   ReteEvaluator reteEvaluator) {
        // this method was silently failing, so I am now throwing an exception to make
        // sure no one calls it by mistake
        throw new UnsupportedOperationException( "This method should NEVER EVER be called" );
    }

    /**
     * This is the entry point into the network for all asserted Facts. Iterates a cache
     * of matching <code>ObjectTypdeNode</code>s asserting the Fact. If the cache does not
     * exist it first iterates and builds the cache.
     *
     * @param factHandle
     *            The FactHandle of the fact to assert
     * @param context
     *            The <code>PropagationContext</code> of the <code>WorkingMemory</code> action
     * @param reteEvaluator
     *            The working memory session.
     */
    public void assertObject(final InternalFactHandle factHandle,
                             final PropagationContext context,
                             final ReteEvaluator reteEvaluator) {
        // this method was silently failing, so I am now throwing an exception to make
        // sure no one calls it by mistake
        throw new UnsupportedOperationException( "This method should NEVER EVER be called" );
    }

    /**
     * Retract a fact object from this <code>RuleBase</code> and the specified
     * <code>WorkingMemory</code>.
     *
     * @param handle
     *            The handle of the fact to retract.
     * @param reteEvaluator
     *            The working memory session.
     */
    public void retractObject(InternalFactHandle handle, PropagationContext context,
                              ObjectTypeConf objectTypeConf, ReteEvaluator reteEvaluator) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Delete {}", handle.toString()  );
        }

        reteEvaluator.addPropagation(new Delete(this, handle, context, objectTypeConf));
    }

    public void immediateDeleteObject(InternalFactHandle handle, PropagationContext context,
                                      ObjectTypeConf objectTypeConf, ReteEvaluator reteEvaluator) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Delete {}", handle.toString()  );
        }

        Delete.execute(reteEvaluator, this, handle, context, objectTypeConf);
    }

    public void propagateRetract(InternalFactHandle handle, PropagationContext context, ObjectTypeConf objectTypeConf, ReteEvaluator reteEvaluator) {
        ObjectTypeNode[] cachedNodes = objectTypeConf.getObjectTypeNodes();

        if ( cachedNodes == null ) {
            // it is  possible that there are no ObjectTypeNodes for an  object being retracted
            return;
        }

        for ( ObjectTypeNode cachedNode : cachedNodes ) {
            cachedNode.retractObject( handle,
                                      context,
                                      reteEvaluator );
        }

        if (handle.isEvent()) {
            ((DefaultEventHandle) handle).unscheduleAllJobs(reteEvaluator);
        }
    }

    /**
     * Adds the <code>ObjectSink</code> so that it may receive
     * <code>Objects</code> propagated from this <code>ObjectSource</code>.
     *
     * @param objectSink
     *            The <code>ObjectSink</code> to receive propagated
     *            <code>Objects</code>. Rete only accepts <code>ObjectTypeNode</code>s
     *            as parameters to this method, though.
     */
    public void addObjectSink(final ObjectSink objectSink) {
        final ObjectTypeNode node = (ObjectTypeNode) objectSink;
        this.objectTypeNodes.put(node.getObjectType(),
                                 node);
    }

    public void removeObjectSink(final ObjectSink objectSink) {
        final ObjectTypeNode node = (ObjectTypeNode) objectSink;
        this.objectTypeNodes.remove( node.getObjectType() );
    }

    public void removeObjectType(ObjectType objectType) {
        this.objectTypeNodes.remove( objectType );
    }

    public void attach() {
        doAttach(null);
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        this.source.addObjectSink( this );
        if (context != null ) {
            for ( InternalWorkingMemory workingMemory : context.getWorkingMemories() ) {
                workingMemory.updateEntryPointsCache();
            }
        }
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        return false;
    }

    public Map<ObjectType, ObjectTypeNode> getObjectTypeNodes() {
        return this.objectTypeNodes;
    }

    private int calculateHashCode() {
        return this.entryPoint.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        return (((NetworkNode)object).getType() == NodeTypeEnums.EntryPointNode && this.hashCode() == object.hashCode() &&
                this.entryPoint.equals( ( (EntryPointNode) object ).entryPoint ) );
    }

    public void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory workingMemory) {
        final ObjectTypeNode node = (ObjectTypeNode) sink;
        final ObjectType newObjectType = node.getObjectType();
        WorkingMemoryEntryPoint wmEntryPoint = workingMemory.getEntryPoint( this.entryPoint.getEntryPointId() );

        for ( ObjectTypeConf objectTypeConf : wmEntryPoint.getObjectTypeConfigurationRegistry().values() ) {
            if ( objectTypeConf.getConcreteObjectTypeNode() != null && newObjectType.isAssignableFrom( objectTypeConf.getConcreteObjectTypeNode().getObjectType() ) ) {
                objectTypeConf.resetCache();
                ObjectTypeNode sourceNode = objectTypeConf.getConcreteObjectTypeNode();
                Iterator<InternalFactHandle> it = sourceNode.getFactHandlesIterator(workingMemory);
                while ( it.hasNext() ) {
                    sink.assertObject( it.next(), context, workingMemory );
                }
            }
        }
    }

    public String toString() {
        return "[EntryPointNode(" + this.id + ") " + this.entryPoint + " ]";
    }

    public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                       ModifyPreviousTuples modifyPreviousTuples,
                                       PropagationContext context,
                                       ReteEvaluator reteEvaluator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BitMask calculateDeclaredMask(Pattern pattern, ObjectType modifiedType, List<String> settableProperties) {
        throw new UnsupportedOperationException();
    }

}
