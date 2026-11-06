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

import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.InitialFact;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectTypeNode;

import java.util.Map;

public class SequenceBuilder
    implements
    ReteooComponentBuilder {

    /**
     * When the first pattern inside the sequence serves as the anchor — i.e. no explicit
     * outer anchor pattern was provided — this method promotes the first sequence pattern
     * to the LIAN role, wires the OTN, builds the LIAN through {@code utils.attachNode} so
     * that the partition ID is set correctly, and sets it as the context tuple source before
     * delegating to {@code buildSequenceNode}.
     */
    public void build(final BuildContext context,
                      final BuildUtils utils,
                      final RuleConditionElement rce) {
        final Sequence seq = (Sequence) rce;
        context.pushRuleComponent( rce );

        // If no anchor was provided, the context tuple source is either null (when
        // SequenceBuilder.requiresLeftActivation returned false) or the InitialFact LIAN
        // (when the GroupElement still injected one for another reason).  In either case,
        // promote the first sequence pattern to the anchor role.
        if (needsAnchorFromFirstPattern(context.getTupleSource())) {
            promoteFirstPatternToAnchor(context, utils, seq);
        }

        context.setTupleSource( utils.attachNode( context,
                                                  CoreComponentFactory.get()
                                                                      .getNodeFactoryService().buildSequenceNode(context.getNextNodeId(),
                                                                                                                 context.getTupleSource(),
                                                                                                                 seq,
                                                                                                                 context) ) );

        context.popRuleComponent();
    }

    /**
     * Returns {@code true} when the current tuple source does not represent a real
     * anchor (it is null or an InitialFact LIAN).
     */
    private static boolean needsAnchorFromFirstPattern(LeftTupleSource tupleSource) {
        if (tupleSource == null) {
            return true;
        }
        if (tupleSource.getType() != NodeTypeEnums.LeftInputAdapterNode) {
            return false;
        }
        ObjectTypeNode otn = ((LeftInputAdapterNode) tupleSource)
                .getObjectSource().getObjectTypeNode();
        return otn != null && otn.getObjectType().isAssignableTo(InitialFact.class);
    }

    /**
     * Builds/reuses the OTN for the first pattern's type, attaches a LIAN through
     * {@code utils.attachNode} (so partition IDs are set correctly), and installs it
     * as the context tuple source.  The {@code buildSequenceNode} call will then detect
     * a real anchor is in place via {@code needsAnchorFromFirstPattern == false}.
     */
    private static void promoteFirstPatternToAnchor(BuildContext context, BuildUtils utils, Sequence seq) {
        Pattern[] filters = seq.getFilters();
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException(
                    "Cannot build anchorless sequence: sequence has no patterns.");
        }

        ObjectType anchorType = filters[0].getObjectType();
        EntryPointNode epn = getOrCreateEntryPointNode(entryPointIdFor(filters[0], context), context);
        Map<ObjectType, ObjectTypeNode> existingOtns = epn.getObjectTypeNodes();

        // Reuse or create the OTN for the first pattern's type.
        ObjectTypeNode anchorOtn = existingOtns.get(anchorType);
        if (anchorOtn == null) {
            anchorOtn = CoreComponentFactory.get().getNodeFactoryService()
                    .buildObjectTypeNode(context.getNextNodeId(), epn, anchorType, context);
            anchorOtn = (ObjectTypeNode) utils.attachNode(context, anchorOtn);
        }

        // Build the LIAN through utils.attachNode so the partition ID is assigned.
        context.setObjectSource(anchorOtn);
        LeftInputAdapterNode anchorLian =
                CoreComponentFactory.get().getNodeFactoryService()
                        .buildLeftInputAdapterNode(context.getNextNodeId(), anchorOtn, context, false);
        // Flush at insert time so the sequencer is active before the next fact is inserted,
        // which lets A-then-B inserted before a single fireAllRules() fire (and keeps B-then-A from firing).
        anchorLian.enableStreamMode();
        context.setTupleSource((LeftTupleSource) utils.attachNode(context, anchorLian));
        context.setObjectSource(null);

        // Signal to buildSequenceNode that the first pattern serves as the anchor.
        context.setSequenceFirstPatternAsAnchor(true);
    }

    /**
     * The first pattern inside the sequence serves as the anchor (LeftTupleSource).
     * No synthetic InitialFact is needed — {@link #build} handles anchor promotion.
     */
    public boolean requiresLeftActivation(final BuildUtils utils,
                                          final RuleConditionElement rce) {
        return false;
    }

    /**
     * Returns the {@link EntryPointId} that governs a sequence step pattern.
     * When the pattern carries an explicit {@link EntryPointId} source (set by
     * {@code KiePackagesBuilder} when the user wrote {@code pattern(v, entryPoint("ep1"))}),
     * that id is used.  Otherwise the build-context's current entry point is used,
     * which is correct for the default-entry-point case and for rules where an outer
     * {@code EntryPointBuilder} already set the context before the sequence was reached.
     */
    static EntryPointId entryPointIdFor(Pattern pattern, BuildContext context) {
        if (pattern.getSource() instanceof EntryPointId) {
            return (EntryPointId) pattern.getSource();
        }
        return context.getCurrentEntryPoint();
    }

    /**
     * Returns the {@link EntryPointNode} for the given entry-point id, creating and
     * attaching it to the Rete if it does not exist yet.  Mirrors the logic in
     * {@link EntryPointBuilder} + {@link BuildUtils#attachNode} without requiring a
     * {@code BuildUtils} reference (which is not available in
     * {@link PhreakNodeFactory#buildSequenceNode}).
     */
    static EntryPointNode getOrCreateEntryPointNode(EntryPointId entryPointId, BuildContext context) {
        EntryPointNode epn = context.getRuleBase().getRete().getEntryPointNode(entryPointId);
        if (epn == null) {
            epn = CoreComponentFactory.get().getNodeFactoryService()
                    .buildEntryPointNode(context.getNextNodeId(),
                                         RuleBasePartitionId.MAIN_PARTITION,
                                         context.getRuleBase().getRete(),
                                         entryPointId);
            epn.attach(context);
        }
        return epn;
    }

}
