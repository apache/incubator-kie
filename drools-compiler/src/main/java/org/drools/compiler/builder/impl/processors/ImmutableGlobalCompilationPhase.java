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
package org.drools.compiler.builder.impl.processors;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.compiler.builder.impl.GlobalVariableContext;
import org.drools.compiler.compiler.GlobalError;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.drl.ast.descr.GlobalDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImmutableGlobalCompilationPhase extends AbstractPackageCompilationPhase {
    protected static final transient Logger logger = LoggerFactory.getLogger(ImmutableGlobalCompilationPhase.class);

    private final GlobalVariableContext globalVariableContext;

    public ImmutableGlobalCompilationPhase(PackageRegistry pkgRegistry, PackageDescr packageDescr, GlobalVariableContext globalVariableContext) {
        super(pkgRegistry, packageDescr);
        this.globalVariableContext = globalVariableContext;
    }

    public final void process() {
        InternalKnowledgePackage pkg = pkgRegistry.getPackage();
        Set<String> existingGlobals = new HashSet<>(pkg.getGlobals().keySet());

        for (final GlobalDescr global : packageDescr.getGlobals()) {
            final String identifier = global.getIdentifier();
            existingGlobals.remove(identifier);
            String className = global.getType();

            try {
                Type globalType = pkgRegistry.getTypeResolver().resolveParametrizedType(className);
                if (globalType instanceof Class && ((Class<?>) globalType).isPrimitive()) {
                    this.results.add(new GlobalError(global, " Primitive types are not allowed in globals : " + className));
                    return;
                }
                addGlobal(pkg, identifier, globalType);
            } catch (final ClassNotFoundException e) {
                this.results.add(new GlobalError(global, e.getMessage()));
                logger.warn("ClassNotFoundException occurred!", e);
            }
        }

        for (String toBeRemoved : existingGlobals) {
            removeGlobal(pkg, toBeRemoved);
        }
    }

    protected void addGlobal(InternalKnowledgePackage pkg, String identifier, Type globalType) {
        pkg.addGlobal(identifier, globalType);
        globalVariableContext.addGlobal(identifier, globalType);
    }

    protected void removeGlobal(InternalKnowledgePackage pkg, String toBeRemoved) {
        // default to no-op. This has only effect with asset filters.
    }
}
