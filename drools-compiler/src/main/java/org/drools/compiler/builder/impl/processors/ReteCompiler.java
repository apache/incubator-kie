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

import java.util.ArrayList;
import java.util.Collection;

import org.drools.base.RuleBase;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.compiler.builder.impl.AssetFilter;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.kie.internal.builder.ResourceChange;

public class ReteCompiler extends AbstractPackageCompilationPhase {
    private final AssetFilter assetFilter;
    private RuleBase kBase;

    public ReteCompiler(PackageRegistry pkgRegistry, PackageDescr packageDescr, RuleBase kBase, AssetFilter assetFilter) {
        super(pkgRegistry, packageDescr);
        this.kBase = kBase;
        this.assetFilter = assetFilter;
    }

    @Override
    public void process() {
        if (this.kBase != null) {
            InternalKnowledgePackage pkg = pkgRegistry.getPackage();
            Collection<RuleImpl> rulesToBeAdded = new ArrayList<>();
            for (RuleDescr ruleDescr : packageDescr.getRules()) {
                if (filterAccepts(ResourceChange.Type.RULE, ruleDescr.getNamespace(), ruleDescr.getName())) {
                    rulesToBeAdded.add(pkg.getRule(ruleDescr.getName()));
                }
            }
            if (!rulesToBeAdded.isEmpty()) {
                this.kBase.addRules(rulesToBeAdded);
            }
        }
    }

    private boolean filterAccepts(ResourceChange.Type type, String namespace, String name) {
        return assetFilter == null || !AssetFilter.Action.DO_NOTHING.equals(assetFilter.accept(type, namespace, name));
    }

}
