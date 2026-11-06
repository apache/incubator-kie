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
package org.drools.core.reteoo.builder;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.DynamicFilterProto;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.base.rule.constraint.CombinedConstraint;
import org.drools.base.rule.constraint.Constraint;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.Sequencer;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.AsyncReceive;
import org.drools.base.rule.AsyncSend;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.EvalCondition;
import org.drools.base.rule.From;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.QueryElement;
import org.drools.base.rule.accessor.DataProvider;
import org.drools.base.time.impl.Timer;
import org.drools.core.common.BetaConstraints;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateRight;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.AlphaTerminalNode;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.ConditionalBranchEvaluator;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.ExistsRight;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.NotRight;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.ReactiveFromNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.SequenceNode;
import org.drools.core.reteoo.SequenceNode.AlphaAdapter;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.WindowNode;
import org.drools.core.rule.BehaviorRuntime;

public class PhreakNodeFactory implements NodeFactory, Serializable {

    private static final NodeFactory INSTANCE = new PhreakNodeFactory();

    public static NodeFactory getInstance() {
        return INSTANCE;
    }

    public EntryPointNode buildEntryPointNode(int id, ObjectSource objectSource, BuildContext context) {
        return new EntryPointNode(id, objectSource, context);
    }

    public EntryPointNode buildEntryPointNode(int id, RuleBasePartitionId partitionId, ObjectSource objectSource, EntryPointId entryPoint) {
        return new EntryPointNode(id, partitionId, objectSource, entryPoint);
    }


    public AlphaNode buildAlphaNode( int id, AlphaNodeFieldConstraint constraint, ObjectSource objectSource, BuildContext context ) {
        return new AlphaNode( id, constraint, objectSource, context );
    }

    public TerminalNode buildTerminalNode( int id, LeftTupleSource source, RuleImpl rule, GroupElement subrule, int subruleIndex, BuildContext context ) {
        return new RuleTerminalNode( id, source, rule, subrule, subruleIndex, context );
    }

    public ObjectTypeNode buildObjectTypeNode(int id, EntryPointNode objectSource, ObjectType objectType, BuildContext context) {
        return new ObjectTypeNode(id, objectSource, objectType, context);
    }

    public EvalConditionNode buildEvalNode(final int id,
                                           final LeftTupleSource tupleSource,
                                           final EvalCondition eval,
                                           final BuildContext context) {
        return new EvalConditionNode( id, tupleSource, eval, context );
    }

    public TupleToObjectNode buildRightInputNode(int id, LeftTupleSource leftInput, LeftTupleSource splitStart, BuildContext context) {
        LeftTupleSource startTupleSource = leftInput;

        while (startTupleSource.getLeftTupleSource() != splitStart) {
            startTupleSource = startTupleSource.getLeftTupleSource();
        }
        return new TupleToObjectNode(id, leftInput, startTupleSource, context );
    }
    public SequenceNode buildSequenceNode(final int id,
                                          final LeftTupleSource tupleSource,
                                          final Sequence seq,
                                          final BuildContext context) {

        Pattern[] allPatterns = seq.getFilters();

        // Whether the first pattern inside the sequence serves as the anchor.
        // SequenceBuilder.promoteFirstPatternToAnchor sets this flag before calling here
        // when no explicit outer anchor pattern was provided.
        final boolean firstPatternAsAnchor = context.isSequenceFirstPatternAsAnchor();
        // Reset the flag so it does not bleed into nested sequences.
        context.setSequenceFirstPatternAsAnchor(false);

        SequenceNode node = new SequenceNode(id, tupleSource, context);
        node.setSequencer(new Sequencer(seq));
        node.setFirstPatternAsAnchor(firstPatternAsAnchor);

        NodeFactory nFactory = CoreComponentFactory.get().getNodeFactoryService();

        // Build the OTN/AlphaAdapter ring for all sequence-step patterns.
        // The deduplication key is (entryPointId, objectType): two patterns with the
        // same ObjectType but on different entry points need separate OTNs and adapters.
        // Use LinkedHashSet to preserve insertion order (first-seen per key).
        Set<EpOtnKey> keySet = new LinkedHashSet<>();
        for (Pattern p : allPatterns) {
            keySet.add(new EpOtnKey(SequenceBuilder.entryPointIdFor(p, context), p.getObjectType()));
        }
        List<EpOtnKey> keyList = new ArrayList<>(keySet);

        Map<EpOtnKey, Integer> adapterIndexMap = new HashMap<>(keyList.size() * 2);
        ObjectSource[] otns = new ObjectSource[keyList.size()];
        for ( int i = 0; i < keyList.size(); i++ ) {
            EpOtnKey key = keyList.get(i);
            adapterIndexMap.put(key, i);
            EntryPointNode epn = SequenceBuilder.getOrCreateEntryPointNode(key.entryPointId, context);
            Map<ObjectType, ObjectTypeNode> existingOtns = epn.getObjectTypeNodes();
            ObjectTypeNode existing = existingOtns.get(key.objectType);
            if (existing != null) {
                // Reuse the existing OTN — building a fresh one and calling attach()
                // would call EntryPointNode.addObjectSink which is a Map.put, silently
                // replacing the existing OTN and cutting off all rules already wired to it.
                otns[i] = existing;
            } else {
                otns[i] = nFactory.buildObjectTypeNode(context.getNextNodeId(), epn, key.objectType, context);
                otns[i].attach(context);
            }
        }

        // For each source create an alpha adapter, which will map it to any filters on the stack.
        AlphaAdapter[] adapters = new AlphaAdapter[keyList.size()];
        for ( int i = 0; i < keyList.size(); i++ ) {
            adapters[i] = new AlphaAdapter(context.getNextNodeId(), otns[i],
                    context.getPartitionId(), node, i);

            adapters[i].attach(context);
        }
        node.setAlphaAdapters(adapters);


        // Create all the dynamic filters, these are added on demand to the stack.
        // filterIndex (i) is the pattern index — one per step.
        // adapterIndex is the index of this pattern's type in objectTypeList — one per distinct type.
        DynamicFilterProto[] filters = new DynamicFilterProto[allPatterns.length];
        for ( int i = 0; i < allPatterns.length; i++ ) {
            List<Constraint> constraints = allPatterns[i].getConstraints();
            if (constraints.isEmpty()) {
                throw new IllegalArgumentException(
                        "sequence step " + i + " has no constraint expression. " +
                        "Every sequence step must have at least one expr(). " +
                        "Add an expr() to the pattern, or use a dedicated fact type per step.");
            }
            AlphaNodeFieldConstraint combined;
            if (constraints.size() == 1) {
                combined = (AlphaNodeFieldConstraint) constraints.get(0);
            } else {
                AlphaNodeFieldConstraint[] all = constraints.stream()
                        .map(c -> (AlphaNodeFieldConstraint) c)
                        .toArray(AlphaNodeFieldConstraint[]::new);
                combined = new CombinedConstraint(all);
            }
            EpOtnKey key = new EpOtnKey(SequenceBuilder.entryPointIdFor(allPatterns[i], context), allPatterns[i].getObjectType());
            int adapterIndex = adapterIndexMap.get(key);
            filters[i] = new DynamicFilterProto(combined, adapterIndex);
        }

        node.setDynamicFilters(filters);

        return node;
    }

    /**
     * Composite key for the OTN deduplication map in {@link #buildSequenceNode}.
     * Two sequence-step patterns that share the same ObjectType but are declared on
     * different entry points must be wired to different OTNs, so both dimensions
     * are part of the key.
     */
    private static final class EpOtnKey {
        final EntryPointId entryPointId;
        final ObjectType   objectType;

        EpOtnKey(EntryPointId entryPointId, ObjectType objectType) {
            this.entryPointId = entryPointId;
            this.objectType   = objectType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EpOtnKey)) return false;
            EpOtnKey other = (EpOtnKey) o;
            return Objects.equals(entryPointId, other.entryPointId)
                && Objects.equals(objectType,   other.objectType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entryPointId, objectType);
        }
    }

    public JoinNode buildJoinNode( int id, LeftTupleSource leftInput, ObjectSource rightInput, BetaConstraints binder, BuildContext context ) {
        JoinRightAdapterNode joinRight = new JoinRightAdapterNode(id, rightInput, context);
        return new JoinNode(context.getNextNodeId(), leftInput, joinRight, binder, context );
    }

    public NotNode buildNotNode( int id, LeftTupleSource leftInput, ObjectSource rightInput, BetaConstraints binder, BuildContext context ) {
        NotRight notRight = new NotRight(id, rightInput, context);
        return new NotNode(context.getNextNodeId(), leftInput, notRight, binder, context );
    }

    public ExistsNode buildExistsNode( int id, LeftTupleSource leftInput, ObjectSource rightInput, BetaConstraints binder, BuildContext context ) {
        ExistsRight existsRight = new ExistsRight(id, rightInput, context);
        return new ExistsNode(context.getNextNodeId(), leftInput, existsRight, binder, context );
    }

    public AccumulateNode buildAccumulateNode(int id, LeftTupleSource leftInput, ObjectSource rightInput,
                                              AlphaNodeFieldConstraint[] resultConstraints, BetaConstraints sourceBinder,
                                              BetaConstraints resultBinder, Accumulate accumulate, BuildContext context) {
        AccumulateRight accRight = new AccumulateRight(id, rightInput, context);
        return new AccumulateNode(context.getNextNodeId(), leftInput, accRight, resultConstraints, sourceBinder, resultBinder, accumulate, context );
    }

    public LeftInputAdapterNode buildLeftInputAdapterNode( int id, ObjectSource objectSource, BuildContext context, boolean terminal ) {
        return terminal ? new AlphaTerminalNode( id, objectSource, context ) : new LeftInputAdapterNode( id, objectSource, context );
    }

    public TerminalNode buildQueryTerminalNode(int id, LeftTupleSource source, RuleImpl rule, GroupElement subrule, int subruleIndex, BuildContext context) {
        return new QueryTerminalNode( id, source, rule, subrule, subruleIndex, context );
    }

    public QueryElementNode buildQueryElementNode( int id, LeftTupleSource tupleSource, QueryElement qe, boolean tupleMemoryEnabled, boolean openQuery, BuildContext context ) {
        return new QueryElementNode( id, tupleSource, qe, tupleMemoryEnabled, openQuery, context );
    }

    public FromNode buildFromNode(int id, DataProvider dataProvider, LeftTupleSource tupleSource, AlphaNodeFieldConstraint[] alphaNodeFieldConstraints, BetaConstraints betaConstraints, boolean tupleMemoryEnabled, BuildContext context, From from) {
        return new FromNode( id, dataProvider, tupleSource, alphaNodeFieldConstraints, betaConstraints, tupleMemoryEnabled, context, from );
    }

    public ReactiveFromNode buildReactiveFromNode(int id, DataProvider dataProvider, LeftTupleSource tupleSource, AlphaNodeFieldConstraint[] alphaNodeFieldConstraints, BetaConstraints betaConstraints, boolean tupleMemoryEnabled, BuildContext context, From from) {
        return new ReactiveFromNode( id, dataProvider, tupleSource, alphaNodeFieldConstraints, betaConstraints, tupleMemoryEnabled, context, from );
    }

    public TimerNode buildTimerNode( int id,
                                     Timer timer,
                                     final String[] calendarNames,
                                     final Declaration[][]   declarations,
                                     LeftTupleSource tupleSource,
                                     BuildContext context ) {
        return new TimerNode( id, tupleSource, timer,calendarNames,declarations,  context );
    }

    public ConditionalBranchNode buildConditionalBranchNode(int id,
                                                            LeftTupleSource tupleSource,
                                                            ConditionalBranchEvaluator branchEvaluator,
                                                            BuildContext context) {
        return new ConditionalBranchNode( id, tupleSource, branchEvaluator, context );

    }

    public WindowNode buildWindowNode(int id,
                                      List<AlphaNodeFieldConstraint> constraints,
                                      List<BehaviorRuntime> behaviors,
                                      ObjectSource objectSource,
                                      BuildContext context) {
        return new WindowNode( id, constraints, behaviors, objectSource, context );
    }

    public AsyncSendNode buildAsyncSendNode( int id, DataProvider dataProvider, LeftTupleSource tupleSource, AlphaNodeFieldConstraint[] alphaNodeFieldConstraints, BetaConstraints betaConstraints, boolean tupleMemoryEnabled, BuildContext context, AsyncSend send) {
        return new AsyncSendNode( id, dataProvider, tupleSource, alphaNodeFieldConstraints, betaConstraints, tupleMemoryEnabled, context, send );
    }

    public AsyncReceiveNode buildAsyncReceiveNode( int id, AsyncReceive receive, LeftTupleSource tupleSource, AlphaNodeFieldConstraint[] alphaNodeFieldConstraints, BetaConstraints betaConstraints, BuildContext context ) {
        return new AsyncReceiveNode( id, tupleSource, receive, alphaNodeFieldConstraints, betaConstraints, context );
    }
}
