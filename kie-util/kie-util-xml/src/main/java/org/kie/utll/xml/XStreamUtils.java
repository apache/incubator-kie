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
package org.kie.utll.xml;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.WildcardTypePermission;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.thoughtworks.xstream.XStream.setupDefaultSecurity;

public class XStreamUtils {

    private static final String[] ALLOWLISTED_PACKAGES = new String[] {
        "org.kie.soup.project.datamodel.imports.Import",
        "org.drools.workbench.models.datamodel.imports.Import",
        "org.drools.core.command.**",
        "org.drools.core.runtime.impl.ExecutionResultImpl",
        "org.drools.core.runtime.rule.impl.FlatQueryResults",
        "org.drools.traits.core.factmodel.Fact",
        "org.drools.traits.core.factmodel.Field",
        "org.drools.core.common.DefaultFactHandle",
        "org.drools.base.rule.KieModuleMetaInfo",
        "org.drools.base.rule.TypeMetaInfo",
        "org.drools.core.runtime.help.impl.XStreamXMLTest$Message"
    };

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    public static XStream createXStream() {
        return internalCreateXStream( new XStream() );
    }

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    public static XStream createXStream(HierarchicalStreamDriver hierarchicalStreamDriver) {
        return internalCreateXStream( new XStream(hierarchicalStreamDriver) );
    }

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    public static XStream createXStream(HierarchicalStreamDriver hierarchicalStreamDriver, ClassLoader classLoader) {
        return internalCreateXStream( new XStream(null, hierarchicalStreamDriver, new ClassLoaderReference( classLoader )) );
    }

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    public static XStream createXStream(ReflectionProvider reflectionProvider ) {
        return internalCreateXStream( new XStream(reflectionProvider) );
    }

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    public static XStream createXStream(ReflectionProvider reflectionProvider, Function<MapperWrapper, MapperWrapper> mapper ) {
        return internalCreateXStream( new XStream(reflectionProvider) {
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return mapper.apply( next );
            }
        });
    }

    /**
     * Vulnerable to CVE-210137285 variants. Do not use. Will be removed in the next few days!
     * @deprecated in favor of {@link #createTrustingXStream()} and {@link #createNonTrustingXStream()}
     */
    @Deprecated
    private static XStream internalCreateXStream( XStream xstream ) {
        setupDefaultSecurity(xstream);
        xstream.addPermission( new WildcardTypePermission( new String[] {
                "java.**", "javax.**", "org.kie.**", "org.drools.**", "org.jbpm.**", "org.optaplanner.**"
        } ) );
        return xstream;
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream() {
        return internalCreateTrustingXStream( new XStream() );
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream(HierarchicalStreamDriver hierarchicalStreamDriver) {
        return internalCreateTrustingXStream( new XStream(hierarchicalStreamDriver) );
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream(HierarchicalStreamDriver hierarchicalStreamDriver, ClassLoader classLoader) {
        return internalCreateTrustingXStream( new XStream(null, hierarchicalStreamDriver, new ClassLoaderReference( classLoader )) );
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream(ReflectionProvider reflectionProvider ) {
        return internalCreateTrustingXStream( new XStream(reflectionProvider) );
    }
    
    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream(ReflectionProvider reflectionProvider, HierarchicalStreamDriver hierarchicalStreamDriver ) {
        return internalCreateTrustingXStream( new XStream(reflectionProvider, hierarchicalStreamDriver) );
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    public static XStream createTrustingXStream(ReflectionProvider reflectionProvider, Function<MapperWrapper, MapperWrapper> mapper ) {
        return internalCreateTrustingXStream( new XStream(reflectionProvider) {
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return mapper.apply( next );
            }
        });
    }

    /**
     * Only use for XML or JSON that comes from a 100% trusted source.
     * The XML/JSON must be as safe as executable java code.
     * Otherwise, you MUST use {@link #createNonTrustingXStream()}.
     */
    private static XStream internalCreateTrustingXStream( XStream xstream ) {
        setupDefaultSecurity(xstream);
        // Presumes the XML content comes from a trusted source!
        xstream.addPermission(new AnyTypePermission());
        return xstream;
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream() {
        return internalCreateNonTrustingXStream( new XStream() );
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(HierarchicalStreamDriver hierarchicalStreamDriver) {
        return internalCreateNonTrustingXStream( new XStream(hierarchicalStreamDriver) );
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(HierarchicalStreamDriver hierarchicalStreamDriver, ClassLoader classLoader) {
        return internalCreateNonTrustingXStream( new XStream(null, hierarchicalStreamDriver, new ClassLoaderReference( classLoader )) );
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(HierarchicalStreamDriver hierarchicalStreamDriver,
                                                   ClassLoader classLoader,
                                                   BiFunction<HierarchicalStreamDriver, ClassLoaderReference, XStream> builder) {
        return internalCreateNonTrustingXStream(builder.apply(hierarchicalStreamDriver, new ClassLoaderReference(classLoader)));
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(ReflectionProvider reflectionProvider ) {
        return internalCreateNonTrustingXStream( new XStream(reflectionProvider) );
    }
    
    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(ReflectionProvider reflectionProvider, HierarchicalStreamDriver hierarchicalStreamDriver ) {
        return internalCreateNonTrustingXStream( new XStream(reflectionProvider, hierarchicalStreamDriver) );
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(ReflectionProvider reflectionProvider,
                                                   HierarchicalStreamDriver hierarchicalStreamDriver,
                                                   UnaryOperator<MapperWrapper> mapper) {
        return internalCreateNonTrustingXStream(new XStream(reflectionProvider, hierarchicalStreamDriver) {
            @Override
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return mapper.apply(next);
            }
        });
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    public static XStream createNonTrustingXStream(ReflectionProvider reflectionProvider, Function<MapperWrapper, MapperWrapper> mapper ) {
        return internalCreateNonTrustingXStream( new XStream(reflectionProvider) {
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return mapper.apply( next );
            }
        });
    }

    /**
     * Use for XML or JSON that might not come from a trusted source (such as REST services payloads, ...).
     * Automatically allowlists all classes with an {@link XStreamAlias} annotation.
     * Often requires allowlisting additional domain specific classes, which you'll need to expose in your API's.
     */
    private static XStream internalCreateNonTrustingXStream( XStream xstream ) {
        setupDefaultSecurity(xstream);
        // TODO remove if setupDefaultSecurity already does this.
        // See comment in https://github.com/x-stream/xstream/pull/99
        xstream.addPermission( new AnyAnnotationTypePermission());
        xstream.addPermission( new WildcardTypePermission( ALLOWLISTED_PACKAGES ) );
        // Do not add root permissions for "java", "org.kie" or the like here because that creates a security problem.
        // For more information, see http://x-stream.github.io/security.html and various xstream dev list conversations.
        // Instead, embrace a allowlist approach and expose that in your API's.
        return xstream;
    }

}
