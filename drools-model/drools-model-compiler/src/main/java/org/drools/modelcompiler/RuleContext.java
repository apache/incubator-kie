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
package org.drools.modelcompiler;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.drools.base.base.ObjectType;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.accessor.GlobalExtractor;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.model.Global;
import org.drools.model.Variable;

public class RuleContext {

    /** Index into the {@code int[]} stored in {@link #sequenceVarIndexes}: the sequence index (raw, before skip conversion). */
    static final int SEQ_VAR_SEQ_IDX    = 0;
    /** Index into the {@code int[]} stored in {@link #sequenceVarIndexes}: the filter/step index within that sequence. */
    static final int SEQ_VAR_FILTER_IDX = 1;


    private final KiePackagesBuilder builder;
    private final InternalKnowledgePackage pkg;
    private final RuleImpl rule;

    private final Map<Variable, Declaration> declarations = new HashMap<>();

    private Map<Variable, Declaration> queryDeclarations;
    private Map<Variable, Accumulate> accumulateSource;

    private Map<Variable, int[]> sequenceVarIndexes;

    private int patternIndex = -1;
    private int seqIndex = -1;
    private boolean needStreamMode = false;

    private Deque<Set<Variable>> variablesInOrCondition;

    public RuleContext( KiePackagesBuilder builder, InternalKnowledgePackage pkg, RuleImpl rule ) {
        this.builder = builder;
        this.pkg = pkg;
        this.rule = rule;
    }

    /**
     * All KiePackage known to the KiePackagesBuilder
     */
    public Collection<InternalKnowledgePackage> getKnowledgePackages() {
        return builder.getKiePackages();
    }

    public InternalKnowledgePackage getPkg() {
        return pkg;
    }

    public RuleImpl getRule() {
        return rule;
    }

    int getNextPatternIndex() {
        return ++patternIndex;
    }

    int nextSeqIndex() {
        return ++seqIndex;
    }

    int getSequenceCount() {
        return seqIndex + 1;
    }

    void startOrCondition() {
        if (variablesInOrCondition == null) {
            variablesInOrCondition = new ArrayDeque<>();
        }
        variablesInOrCondition.addLast(new HashSet<>());
    }

    void endOrCondition() {
        variablesInOrCondition.removeLast();
    }

    void registerPattern( Variable variable, Pattern pattern ) {
        if (variablesInOrCondition != null && !variablesInOrCondition.isEmpty() && !variablesInOrCondition.getLast().add(variable)) {
            // allow to overwrite varibles that have been defined in a different or branch
            declarations.put(variable, pattern.getDeclaration());
        } else {
            declarations.computeIfAbsent(variable, k -> pattern.getDeclaration());
        }
    }

    Pattern getPattern( Variable variable ) {
        Declaration declaration = declarations.get( variable );
        return declaration == null ? null : declaration.getPattern();
    }

    Declaration getDeclaration( Variable variable ) {
        if (variable == null) {
            return null;
        }
        if ( variable.isFact() ) {
            Declaration declaration = declarations.get( variable );
            if (declaration == null) {
                declaration = getQueryDeclaration( variable );
            }
            return declaration;
        } else {
            Global global = (( Global ) variable);
            ObjectType objectType = builder.getObjectType( global );
            ReadAccessor globalExtractor = new GlobalExtractor( global.getName(), objectType );
            return new Declaration( global.getName(), globalExtractor, new Pattern( 0, objectType ) );
        }
    }

    Declaration getQueryDeclaration( Variable variable ) {
        return queryDeclarations == null ? null : queryDeclarations.get( variable );
    }

    void addQueryDeclaration(Variable variable, Declaration declaration) {
        if ( queryDeclarations == null) {
            queryDeclarations = new HashMap<>();
        }
        queryDeclarations.put( variable, declaration );
    }

    void addDeclaration( Variable variable, Declaration declaration ) {
        declarations.put( variable, declaration );
    }

    void addSequenceVarIndex( Variable variable, int seqIndex, int filterIndex ) {
        if (sequenceVarIndexes == null) {
            sequenceVarIndexes = new HashMap<>();
        }
        sequenceVarIndexes.put( variable, new int[]{ seqIndex, filterIndex } );
    }

    Map<Variable, int[]> getSequenceVarIndexes() {
        return sequenceVarIndexes == null ? Map.of() : sequenceVarIndexes;
    }

    void addGroupByDeclaration( Variable groupKeyVar, Declaration declaration ) {
        addDeclaration( groupKeyVar, declaration );
    }

    Accumulate getAccumulateSource( Variable variable) {
        return accumulateSource == null ? null : accumulateSource.get( variable );
    }

    void addAccumulateSource(Variable variable, Accumulate accumulate) {
        if (accumulateSource == null) {
            accumulateSource = new HashMap<>();
        }
        accumulateSource.put( variable, accumulate );
    }

    public ClassLoader getClassLoader() {
        return builder.getClassLoader();
    }

    public boolean needsStreamMode() {
        return needStreamMode;
    }

    public void setNeedStreamMode() {
        this.needStreamMode = true;
    }

    public Map<String, Declaration> getDeclarations() {
        Map<String, Declaration> decls = new HashMap<>();
        declarations.forEach( ( var, decl) -> decls.put( var.getName(), decl ) );
        return decls;
    }
}
