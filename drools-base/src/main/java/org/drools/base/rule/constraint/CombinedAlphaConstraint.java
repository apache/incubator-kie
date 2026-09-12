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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import org.drools.base.base.ValueResolver;
import org.drools.base.rule.Declaration;
import org.kie.api.runtime.rule.FactHandle;

/**
 * ANDs two or more {@link AlphaNodeFieldConstraint}s into a single constraint.
 * Used by the sequencing runtime when a sequence step carries multiple expressions:
 * every expression must be satisfied for the step to match.
 */
public class CombinedAlphaConstraint implements AlphaNodeFieldConstraint {

    private AlphaNodeFieldConstraint[] constraints;

    public CombinedAlphaConstraint(AlphaNodeFieldConstraint[] constraints) {
        this.constraints = constraints;
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        for (AlphaNodeFieldConstraint c : constraints) {
            if (!c.isAllowed(handle, valueResolver)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return new Declaration[0];
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        // Sequence step constraints are always self-contained alpha constraints —
        // they carry no cross-pattern declaration references, so getRequiredDeclarations()
        // returns empty and LogicTransformer never calls this method.
    }

    @Override
    public CombinedAlphaConstraint clone() {
        AlphaNodeFieldConstraint[] cloned = new AlphaNodeFieldConstraint[constraints.length];
        for (int i = 0; i < constraints.length; i++) {
            cloned[i] = (AlphaNodeFieldConstraint) constraints[i].clone();
        }
        return new CombinedAlphaConstraint(cloned);
    }

    @Override
    public AlphaNodeFieldConstraint cloneIfInUse() {
        return clone();
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
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(constraints);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        constraints = (AlphaNodeFieldConstraint[]) in.readObject();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CombinedAlphaConstraint)) return false;
        return Arrays.equals(constraints, ((CombinedAlphaConstraint) o).constraints);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(constraints);
    }
}
