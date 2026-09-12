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
package org.drools.mvel.integrationtests.phreak.sequencing;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.MutableTypeConstraint;
import org.kie.api.runtime.rule.FactHandle;

public class AlphaConstraint extends MutableTypeConstraint {
    private Predicate1 predicate1;

    public AlphaConstraint(Predicate1 predicate1) {
        this.predicate1 = predicate1;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return new Declaration[0];
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {

    }

    @Override
    public MutableTypeConstraint clone() {
        return new AlphaConstraint(predicate1);
    }

    @Override
    public boolean isTemporal() {
        return false;
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        return predicate1.test(handle.getObject());
    }

    @Override
    public boolean isAllowedCachedLeft(Object context, FactHandle handle) {
        throw new UnsupportedOperationException("AlphaConstraint does not support cached context evaluation");
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, Object context) {
        throw new UnsupportedOperationException("AlphaConstraint does not support cached context evaluation");
    }

    @Override
    public Object createContext() {
        throw new UnsupportedOperationException("AlphaConstraint does not support cached context evaluation");
    }
}
