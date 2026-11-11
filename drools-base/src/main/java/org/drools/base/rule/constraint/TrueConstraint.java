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
package org.drools.base.rule.constraint;

import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueResolver;
import org.drools.base.rule.Declaration;
import org.kie.api.runtime.rule.FactHandle;

/**
 * A pass-through alpha constraint that accepts every fact of the matched type.
 * Used as a stand-in for sequence step patterns that carry no explicit expr().
 */
public class TrueConstraint implements AlphaNodeFieldConstraint {

    public static final TrueConstraint INSTANCE = new TrueConstraint();

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        return true;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return new Declaration[0];
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) { }

    @Override
    public TrueConstraint clone() {
        return INSTANCE;
    }

    @Override
    public AlphaNodeFieldConstraint cloneIfInUse() {
        return INSTANCE;
    }

    @Override
    public ConstraintType getType() {
        return ConstraintType.ALPHA;
    }

    @Override
    public boolean isTemporal() {
        return false;
    }

    @Override
    public void writeExternal(ObjectOutput out) { }

    @Override
    public void readExternal(ObjectInput in) { }
}
