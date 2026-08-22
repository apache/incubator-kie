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
package org.drools.compiler.rule.builder;

import org.drools.base.base.ValueType;
import org.drools.base.base.extractors.SelfReferenceClassFieldReader;
import org.drools.base.base.field.ObjectFieldImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.time.Interval;
import org.drools.core.base.extractors.ConstantValueReader;
import org.drools.core.common.DefaultEventHandle;
import org.kie.api.runtime.rule.FactHandle;

import static org.drools.core.common.InternalFactHandle.dummyFactHandleOf;

/**
 * An EvaluatorWrapper is used when executing MVEL expressions
 * that have operator calls rewritten as:
 * 
 * operator.evaluate( leftArg, rightArg )
 * 
 */
public class EvaluatorWrapper implements Evaluator {

    private static final long                          serialVersionUID = 520L;

    private static final SelfReferenceClassFieldReader extractor        = new SelfReferenceClassFieldReader( Object.class );

    private Evaluator                                  evaluator;

    private Declaration                                leftBinding;
    private Declaration                                rightBinding;

    private boolean                                    selfLeft;
    private boolean                                    selfRight;

    private String                                     bindingName;

    private transient boolean                          rightLiteral;

    private transient Long                             leftTimestamp;
    private transient Long                             rightTimestamp;

    public EvaluatorWrapper(Evaluator evaluator,
                            Declaration leftBinding,
                            Declaration rightBinding) {
        this.evaluator = evaluator;
        this.leftBinding = leftBinding;
        this.rightBinding = rightBinding;
        init();
    }

    private void init() {
        selfLeft = leftBinding == null || leftBinding.getIdentifier().equals("this");
        selfRight = rightBinding == null || rightBinding.getIdentifier().equals("this");
    }

    /**
     * This method is called when operators are rewritten as function calls. For instance,
     * 
     * x after y
     * 
     * Is rewritten as
     * 
     * after.evaluate( _workingMemory_, x, y )
     * 
     * @return
     */
    public boolean evaluate(GlobalResolver globalResolver, Object left, Object right) {
        Object leftValue = leftTimestamp != null ? leftTimestamp : left;
        Object rightValue = rightTimestamp != null ? rightTimestamp : right;

        return rightLiteral ?
                evaluator.evaluate( globalResolver,
                                    new ConstantValueReader( leftValue ),
                                    dummyFactHandleOf( leftValue ),
                                    new ObjectFieldImpl( rightValue ) ) :
                evaluator.evaluate( globalResolver,
                                    new ConstantValueReader( leftValue ),
                                    dummyFactHandleOf( leftValue ),
                                    new ConstantValueReader( rightValue ),
                                    dummyFactHandleOf( rightValue ) );
    }

    /**
     * @return
     * @see Evaluator#getValueType()
     */
    public ValueType getValueType() {
        return evaluator.getValueType();
    }

    /**
     * @return
     * @see Evaluator#getOperator()
     */
    public org.kie.api.runtime.rule.Operator getOperator() {
        return evaluator.getOperator();
    }

    /**
     * @return
     * @see Evaluator#getCoercedValueType()
     */
    public ValueType getCoercedValueType() {
        return evaluator.getCoercedValueType();
    }

    public boolean evaluate(GlobalResolver globalResolver,
                            ReadAccessor extractor,
                            FactHandle factHandle,
                            FieldValue value) {
        return evaluator.evaluate( globalResolver,
                                   extractor,
                                   factHandle,
                                   value );
    }

    public boolean evaluate(GlobalResolver globalResolver,
                            ReadAccessor leftExtractor,
                            FactHandle left,
                            ReadAccessor rightExtractor,
                            FactHandle right) {
        return evaluator.evaluate( globalResolver,
                                   leftExtractor,
                                   left,
                                   rightExtractor,
                                   right );
    }

    /**
     * @return
     * @see Evaluator#isTemporal()
     */
    public boolean isTemporal() {
        return evaluator.isTemporal();
    }

    /**
     * @return
     * @see Evaluator#getInterval()
     */
    public Interval getInterval() {
        return evaluator.getInterval();
    }

    public void loadHandles(FactHandle[] handles, FactHandle rightHandle) {
        FactHandle localLeftHandle = selfLeft ? null : getFactHandle(leftBinding, handles);

        FactHandle localRightHandle = selfRight ? rightHandle : getFactHandle(rightBinding, handles);
        this.rightLiteral = localRightHandle == null;

        if (isTemporal()) {
            if (localLeftHandle == null) {
                localLeftHandle = rightHandle;
            }
            leftTimestamp = localLeftHandle instanceof DefaultEventHandle ? ((DefaultEventHandle) localLeftHandle).getStartTimestamp() : null;
            rightTimestamp = localRightHandle instanceof DefaultEventHandle ? ((DefaultEventHandle) localRightHandle).getStartTimestamp() : null;
        }
    }

    @Override
    public String toString() {
        return this.evaluator.toString();
    }


    public static SelfReferenceClassFieldReader getExtractor() {
        return extractor;
    }

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName( String bindingName ) {
        this.bindingName = bindingName;
    }

    private static FactHandle getFactHandle( Declaration declaration,
                                             FactHandle[] handles ) {
        return handles != null && handles.length > declaration.getObjectIndex() ? handles[declaration.getObjectIndex()] : null;
    }
}
