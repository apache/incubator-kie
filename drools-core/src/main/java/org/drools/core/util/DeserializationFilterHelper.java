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
package org.drools.core.util;

import java.io.ObjectInputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeserializationFilterHelper {

    private static final Logger logger = LoggerFactory.getLogger(DeserializationFilterHelper.class);

    private static final List<String> DEFAULT_ALLOWED_PREFIXES = List.of(
            "java.lang.", "java.util.", "java.math.", "java.time.");

    private static final Set<String> DEFAULT_ALLOWED_CLASSES = Set.of(
            "org.drools.base.reteoo.InitialFactImpl",
            "org.drools.base.util.LinkedListEntry",
            "org.drools.tms.SimpleMode",
            "org.drools.tms.DefeasibleMode",
            "org.drools.traits.core.factmodel.AbstractTriple",
            "org.drools.traits.core.factmodel.BitMaskKey",
            "org.drools.traits.core.factmodel.ExternalizableLinkedHashMap",
            "org.drools.traits.core.factmodel.Key",
            "org.drools.traits.core.factmodel.NullTraitType",
            "org.drools.traits.core.factmodel.ThingProxyImplPlaceHolder",
            "org.drools.traits.core.factmodel.TraitFieldDefaultValue",
            "org.drools.traits.core.factmodel.TraitFieldImpl",
            "org.drools.traits.core.factmodel.TraitFieldImpl$DefaultValueHierarchy",
            "org.drools.traits.core.factmodel.TraitFieldImpl$TypeComparator",
            "org.drools.traits.core.factmodel.TraitFieldTMSImpl",
            "org.drools.traits.core.factmodel.TraitProxyImpl",
            "org.drools.traits.core.factmodel.TraitRegistryImpl$CachingHierarcyEncoderImpl",
            "org.drools.traits.core.factmodel.TraitTypeMapImpl",
            "org.drools.traits.core.factmodel.TripleBasedBean",
            "org.drools.traits.core.factmodel.TripleBasedStruct",
            "org.drools.traits.core.factmodel.TripleFactoryImpl",
            "org.drools.traits.core.factmodel.TripleImpl",
            "org.drools.traits.core.factmodel.TripleStore",
            "org.drools.traits.core.factmodel.TypeCache",
            "org.drools.traits.core.factmodel.TypeHierarchy",
            "org.drools.traits.core.factmodel.TypeWrapper",
            "org.drools.traits.core.util.AbstractBitwiseHierarchyImpl",
            "org.drools.traits.core.util.AbstractBitwiseHierarchyImpl$HierCodeComparator",
            "org.drools.traits.core.util.AbstractCodedHierarchyImpl",
            "org.drools.traits.core.util.CodedHierarchyImpl",
            "org.drools.traits.core.util.HierarchyEncoderImpl",
            "org.drools.traits.core.util.HierarchyEncoderImpl$ImmutableBitSet",
            "org.drools.traits.core.util.HierNode",
            "org.drools.util.bitmask.EmptyButLastBitMask",
            "org.drools.util.bitmask.LongBitMask",
            "org.drools.util.bitmask.SingleLongBitMask");

    private DeserializationFilterHelper() {
    }

    public static boolean isClassAllowed(String className) {
        if (DEFAULT_ALLOWED_CLASSES.contains(className)) {
            return true;
        }
        for (String prefix : DEFAULT_ALLOWED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : getUserAllowedPrefixes()) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeserializationFilterEnabled() {
        return Boolean.parseBoolean(
                System.getProperty(KeyStoreConstants.PROP_ENABLE_DESER_FILTER, "true"));
    }

    public static ObjectInputFilter createDeserializationFilter() {
        return filterInfo -> {
            Class<?> clazz = filterInfo.serialClass();
            if (clazz == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            if (clazz.isPrimitive()) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();
                while (componentType.isArray()) {
                    componentType = componentType.getComponentType();
                }
                if (componentType.isPrimitive()) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                clazz = componentType;
            }
            String className = clazz.getName();
            if (isClassAllowed(className)) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            logger.warn("Deserialization of class '{}' was rejected by the ObjectInputFilter. "
                    + "If this is a legitimate fact class, add it to the allowlist via "
                    + "-D{}=<pattern>",
                    className, KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS);
            return ObjectInputFilter.Status.REJECTED;
        };
    }

    private static List<String> getUserAllowedPrefixes() {
        String patterns = System.getProperty(KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS, "");
        if (patterns.isEmpty()) {
            return List.of();
        }
        List<String> prefixes = new ArrayList<>();
        for (String pattern : patterns.split(";")) {
            String trimmed = pattern.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.endsWith(".*")) {
                    prefixes.add(trimmed.substring(0, trimmed.length() - 1));
                } else if (trimmed.endsWith("*")) {
                    prefixes.add(trimmed.substring(0, trimmed.length() - 1));
                } else {
                    prefixes.add(trimmed);
                }
            }
        }
        return prefixes;
    }
}
