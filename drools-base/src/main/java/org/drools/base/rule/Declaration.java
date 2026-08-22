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
package org.drools.base.rule;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import org.drools.base.base.AccessorKeySupplier;
import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.common.DroolsObjectInputStream;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.accessor.AcceptsReadAccessor;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.accessor.TupleValueExtractor;
import org.kie.api.runtime.rule.FactHandle;

import static org.drools.util.ClassUtils.canonicalName;
import static org.drools.util.ClassUtils.convertFromPrimitiveType;

public class Declaration implements Externalizable, AcceptsReadAccessor, TupleValueExtractor {

    private static final long    serialVersionUID = 510l;

    /** The identifier for the variable. */
    private String               identifier;

    private String               bindingName;

    private ReadAccessor readAccessor;

    private Pattern              pattern;

    private boolean              internalFact;

    private transient Class<?>   declarationClass;

    private int xPathOffset = 0;

    public Declaration() {
        this( null, null, null );
    }

    /**
     * Construct.
     *
     * @param identifier
     *            The name of the variable.
     * @param pattern
     *            The pattern this variable is declared in
     */
    public Declaration(final String identifier, final Pattern pattern) {
        this( identifier, null, pattern, false );
    }

    /**
     * Construct.
     *
     * @param identifier
     *            The name of the variable.
     * @param extractor
     *            The extractor for this variable
     * @param pattern
     *            The pattern this variable is declared in
     */
    public Declaration(final String identifier, final ReadAccessor extractor, final Pattern pattern) {
        this( identifier, extractor, pattern, false );
    }

    /**
     *
     * @param identifier
     *            The name of the variable.
     * @param identifier
     *            The name of the variable.
     * @param extractor
     *            The extractor for this variable
     * @param internalFact
     *            True if this is an internal fact created by the engine, like a collection result
     *            of a collect CE
     */
    public Declaration(final String identifier, final ReadAccessor extractor, final Pattern pattern, final boolean internalFact) {
        this.identifier = identifier;
        this.readAccessor = extractor;
        this.pattern = pattern;
        this.internalFact = internalFact;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        identifier = (String) in.readObject();
        if (in instanceof DroolsObjectInputStream) {
            ((DroolsObjectInputStream) in).readExtractor(this::setReadAccessor);
        } else {
            readAccessor = (ReadAccessor) in.readObject();
        }
        pattern = (Pattern) in.readObject();
        internalFact = in.readBoolean();
        bindingName = (String) in.readObject();
        xPathOffset = in.readInt();

    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( identifier );

        if (readAccessor instanceof AccessorKeySupplier) {
            out.writeObject( ( (AccessorKeySupplier) readAccessor ).getAccessorKey() );
        } else {
            out.writeObject( readAccessor );
        }

        out.writeObject( pattern );
        out.writeBoolean( internalFact );
        out.writeObject( bindingName );
        out.writeInt(xPathOffset);
    }

    /**
     * Retrieve the variable's identifier.
     *
     * @return The variable's identifier.
     */
    public String getIdentifier() {
        return this.identifier;
    }

    public String getBindingName() {
        return bindingName != null ? bindingName : identifier;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    /**
     * Retrieve the <code>ValueType</code>.
     *
     * @return The ValueType.
     */
    @Override
    public ValueType getValueType() {
        return this.readAccessor.getValueType();
    }

    /**
     * Returns the index of the pattern
     *
     * @return the pattern
     */
    public Pattern getPattern() {
        return this.pattern;
    }

    public void setPattern(final Pattern pattern) {
        this.pattern = pattern;
    }

    public int getObjectIndex() {
        return pattern.getObjectIndex() + xPathOffset;
    }

    public int getTupleIndex() {
        return pattern.getTupleIndex() + xPathOffset;
    }

    public void setxPathOffset( int xPathOffset ) {
        this.xPathOffset = xPathOffset;
    }

    public int getxPathOffset() {
        return xPathOffset;
    }

    public boolean isFromXpathChunk() {
        return xPathOffset >= 1;
    }

    /**
     * Returns true if this declaration is a pattern declaration
     */
    public boolean isPatternDeclaration() {
        return ( this.pattern != null && this.pattern.getDeclaration() == this ) || this.getIdentifier().equals( "this" ) ;
    }

    public void setReadAccessor(ReadAccessor readAccessor) {
        this.readAccessor = readAccessor;
    }

    /**
     * Returns the Extractor expression
     */
    public ReadAccessor getExtractor() {
        return this.readAccessor;
    }

    public Class<?> getDeclarationClass() {
        if (declarationClass == null) {
            declarationClass = readAccessor != null ? readAccessor.getExtractToClass() : null;
        }
        return declarationClass;
    }

    public void setDeclarationClass( Class<?> declarationClass ) {
        this.declarationClass = declarationClass;
    }

    @Override
    public Object getValue(ValueResolver valueResolver, BaseTuple tuple) {
        return getValue( valueResolver, tuple.get( this ) );
    }

    public Object getValue(ValueResolver valueResolver, FactHandle fh) {
        return getValue( valueResolver, fh.getObject() );
    }

    public Object getValue(ValueResolver valueResolver,
                           final Object object) {
        return this.readAccessor.getValue(getGlobalExtractor(valueResolver), object);
    }

    private GlobalResolver getGlobalExtractor(ValueResolver valueResolver) {
        return valueResolver == null? null: valueResolver.getGlobalResolver();
    }

    public long getWholeNumberValue(ValueResolver valueResolver,
                             final Object object) {
        return this.readAccessor.getWholeNumberValue(getGlobalExtractor(valueResolver), object);
    }

    public double getDecimalValue(ValueResolver valueResolver,
                                 final Object object) {
        return this.readAccessor.getDecimalValue(getGlobalExtractor(valueResolver), object);
    }

    public boolean getBooleanValue(ValueResolver valueResolver,
                                   final Object object) {
        return this.readAccessor.getBooleanValue(getGlobalExtractor(valueResolver), object);
    }

    public int getHashCode(ValueResolver valueResolver,
                           final Object object) {
        return this.readAccessor.getHashCode(getGlobalExtractor(valueResolver), object);
    }

    public boolean isGlobal() {
        return this.readAccessor != null && this.readAccessor.isGlobal();
    }

    public Method getNativeReadMethod() {
        if ( this.readAccessor != null ) {
            return this.readAccessor.getNativeReadMethod();
        } else {
            // This only happens if there was an error else where, such as building the initial declaration binding
            // return getValue to avoid null pointers, so rest of drl can attempt to build
            try {
                return this.getClass().getDeclaredMethod("getValue",
                                                         ValueResolver.class, Object.class);
            } catch ( final Exception e ) {
                throw new RuntimeException( "This is a bug. Please report to development team: " + e.getMessage(),
                                            e );
            }
        }
    }

    public String getNativeReadMethodName() {
        return readAccessor != null ? readAccessor.getNativeReadMethodName() : "getValue";
    }

    private transient String cachedTypeName;
    public String getTypeName() {
        if (cachedTypeName == null) {
        // we assume that null extractor errors are reported else where
            cachedTypeName = ( getExtractor() != null && getDeclarationClass() != null )
                             ? canonicalName(getDeclarationClass())
                             : "java.lang.Object";
        }
        return cachedTypeName;
    }

    private transient String cachedBoxedTypeName;
    public String getBoxedTypeName() {
        if (cachedBoxedTypeName == null) {
            // we assume that null extractor errors are reported else where
            cachedBoxedTypeName = getExtractor() != null ? canonicalName(convertFromPrimitiveType(getDeclarationClass())) : "java.lang.Object";
        }
        return cachedBoxedTypeName;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public String toString() {
        return "(" + (this.readAccessor != null ? this.readAccessor.getValueType() : "null accessor") + ") " + this.identifier;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + this.pattern.getTupleIndex();
        result = PRIME * result + this.readAccessor.hashCode();
        result = PRIME * result + this.xPathOffset;
        return result;
    }


    public boolean equals(final Object object) {
        if ( this == object ) {
            return true;
        }

        if ( object == null || getClass() != object.getClass() ) {
            return false;
        }

        final Declaration other = (Declaration) object;

        return this.pattern.getPatternId() == other.pattern.getPatternId() &&
               this.readAccessor.equals(other.readAccessor) &&
               this.xPathOffset == other.xPathOffset;
    }

    public boolean isInternalFact() {
        return internalFact;
    }

    @Override
    public Declaration clone() {
        Declaration declr = new Declaration( this.identifier, this.readAccessor, this.pattern );
        declr.setBindingName(this.bindingName);
        declr.setxPathOffset(this.xPathOffset);
        return declr;
    }

    public Declaration cloneWithPattern(Pattern pattern) {
        Declaration declr = new Declaration( this.identifier, this.readAccessor, pattern );
        declr.setBindingName(this.bindingName);
        declr.setxPathOffset(this.xPathOffset);
        return declr;
    }
}
