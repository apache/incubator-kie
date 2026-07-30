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
package org.kie.kogito.core.rules.incubation.quarkus.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnits;
import org.kie.kogito.incubation.common.EmptyMetaDataContext;
import org.kie.kogito.incubation.common.ExtendedDataContext;
import org.kie.kogito.incubation.common.ExtendedReferenceContext;
import org.kie.kogito.incubation.common.LocalId;
import org.kie.kogito.incubation.common.MapDataContext;
import org.kie.kogito.incubation.common.MetaDataContext;
import org.kie.kogito.incubation.common.ReferenceContext;
import org.kie.kogito.incubation.rules.InstanceQueryId;
import org.kie.kogito.incubation.rules.RuleUnitId;
import org.kie.kogito.incubation.rules.RuleUnitInstanceId;
import org.kie.kogito.incubation.rules.services.StatefulRuleUnitService;

class StatefulRuleUnitServiceImpl implements StatefulRuleUnitService {

    // CWE-470: validate class name is a legal Java FQCN before passing to loadClass
    private static final Pattern SAFE_CLASS_NAME = Pattern.compile("[\\w$]+(\\.[\\w$]+)*");

    private final RuleUnits ruleUnits;

    public StatefulRuleUnitServiceImpl(RuleUnits ruleUnits) {
        this.ruleUnits = ruleUnits;
    }

    @Override
    public MetaDataContext create(LocalId localId, ExtendedReferenceContext extendedDataContext) {
        RuleUnitId ruleUnitId;
        if (localId instanceof RuleUnitId) {
            ruleUnitId = (RuleUnitId) localId;
        } else
            throw new IllegalArgumentException("cannot parse rule unit id");

        ReferenceContext refCtx = extendedDataContext.data();
        if (!(refCtx instanceof RuleUnitData)) {
            throw new IllegalArgumentException("ExtendedReferenceContext#data must be a RuleUnitData");
        }
        RuleUnitData ruleUnitData = (RuleUnitData) refCtx;

        Class<RuleUnitData> aClass = toClass(ruleUnitId);
        RuleUnit<RuleUnitData> ruleUnit = ruleUnits.create(aClass);
        RuleUnitInstance<RuleUnitData> instance = ruleUnit.createInstance(ruleUnitData);
        String instanceId = UUID.randomUUID().toString();
        ruleUnits.register(instanceId, instance);
        RuleUnitInstanceId ruleUnitInstanceId = ruleUnitId.instances().get(instanceId);
        return MapDataContext.of(Map.of("id", ruleUnitInstanceId.asLocalUri().path()));
    }

    private Class<RuleUnitData> toClass(RuleUnitId ruleUnitId) {
        String className = ruleUnitId.ruleUnitId();
        if (!SAFE_CLASS_NAME.matcher(className).matches()) {
            throw new IllegalArgumentException("Invalid rule unit class name: " + className);
        }
        try {
            return (Class<RuleUnitData>) Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + className, e);
        }
    }

    @Override
    public MetaDataContext dispose(LocalId localId) {
        RuleUnitInstanceId ruleUnitInstanceId;
        if (localId instanceof RuleUnitInstanceId) {
            ruleUnitInstanceId = (RuleUnitInstanceId) localId;
        } else
            throw new IllegalArgumentException("cannot parse rule unit id");
        RuleUnitInstance<?> instance = ruleUnits.getRegisteredInstance(ruleUnitInstanceId.ruleUnitInstanceId());
        if (instance == null)
            throw new IllegalArgumentException("Unknown instance " + localId);
        instance.close();
        return EmptyMetaDataContext.Instance;
    }

    @Override
    public MetaDataContext fire(LocalId localId) {
        RuleUnitInstanceId ruleUnitInstanceId;
        if (localId instanceof RuleUnitInstanceId) {
            ruleUnitInstanceId = (RuleUnitInstanceId) localId;
        } else
            throw new IllegalArgumentException("cannot parse rule unit id");
        RuleUnitInstance<?> instance = ruleUnits.getRegisteredInstance(ruleUnitInstanceId.ruleUnitInstanceId());
        instance.fire();
        return EmptyMetaDataContext.Instance;
    }

    @Override
    public Stream<ExtendedDataContext> query(LocalId localId, ExtendedReferenceContext params) {
        RuleUnitInstanceId ruleUnitInstanceId;
        // must add a QueryId for instances!
        InstanceQueryId queryId;
        if (localId instanceof InstanceQueryId) {
            queryId = (InstanceQueryId) localId;
            ruleUnitInstanceId = queryId.ruleUnitInstanceId();
        } else {
            throw new IllegalArgumentException(
                    "Not a valid instance query id " + localId);
        }

        RuleUnitInstance<?> instance = ruleUnits.getRegisteredInstance(ruleUnitInstanceId.ruleUnitInstanceId());
        if (instance == null)
            throw new IllegalArgumentException("Unknown instance " + localId);
        List<Map<String, Object>> results = instance.executeQuery(queryId.queryId()).toList();

        return results.stream().map(r -> ExtendedDataContext.of(EmptyMetaDataContext.Instance, MapDataContext.of(r)));

    }

}
