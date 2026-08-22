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
package org.drools.base.rule.accessor;

import java.io.Serializable;

import org.drools.base.base.ValueType;
import org.drools.base.time.Interval;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Operator;

/**
 * A public interface to be implemented by all evaluators
 */
public interface Evaluator extends Serializable, org.kie.api.runtime.rule.Evaluator {

    /**
     * Returns the type of the values this evaluator operates upon.
     * 
     * @return
     */
    ValueType getValueType();

    /**
     * Returns the operator representation object for this evaluator
     * 
     * @return
     */
    Operator getOperator();
    
    /**
     * Returns the value type this evaluator will coerce
     * operands to, during evaluation. This is useful for
     * operators like "memberOf", that always convert to
     * Object when evaluating, independently of the source
     * operand value type.
     * 
     * @return
     */
    ValueType getCoercedValueType();
    
    /**
     * Evaluates the expression using the provided parameters.
     * 
     * This method is used when evaluating alpha-constraints,
     * i.e., a fact attribute against a constant value. 
     * For instance:
     * 
     * Person( name == "Bob" )
     * 
     * So, it uses a constant value "Bob" that is sent into
     * the method as the FieldValue (value), and compares it
     * to the value of the name field, read by using the 
     * extractor on the fact instance (object1).
     *  
     * @param valueResolver
     *        The current working memory 
     * @param extractor 
     *        The extractor used to get the field value from the object
     * @param factHandle
     *        The source object to evaluate, i.e., the fact
     * @param value
     *        The actual value to compare to, i.e., the constant value.
     * 
     * @return Returns true if evaluation is successful. false otherwise.
     */
    public boolean evaluate(GlobalResolver valueResolver,
                            ReadAccessor extractor,
                            FactHandle factHandle,
                            FieldValue value);

    /**
     * Evaluates the expression using the provided parameters.
     * 
     * This method is used for internal indexing and hashing, 
     * when drools needs to extract and evaluate both left and
     * right values at once.
     *  
     * For instance:
     * 
     * Person( name == $someName )
     * 
     * This method will be used to extract and evaluate both
     * the "name" attribute and the "$someName" variable at once.
     *
     * Note that in this method signature, 'left' means the left operand and 'right' means the right operand,
     *
     * @param valueResolver
     *        The current working memory
     * @param leftExtractor
     *        The extractor to read the left operand value. In the above example,
     *        the "name" attribute value.
     * @param leftOperandFact
     *        The object from where to extract the value. In the
     *        above example, that is the "Person" instance from where to
     *        extract the "name" attribute.
     * @param rightExtractor
     *        The extractor to read the right operand value. In the above example,
     *        the "$someName" variable value.
     * @param rightOperandFact
     *        The source object from where the value of the variable is
     *        extracted.
     *
     * @return Returns true if evaluation is successful. false otherwise.
     */
    public boolean evaluate(GlobalResolver valueResolver,
                            ReadAccessor leftExtractor,
                            FactHandle leftOperandFact,
                            ReadAccessor rightExtractor,
                            FactHandle rightOperandFact);

    /**
     * Returns true if this evaluator implements a temporal evaluation,
     * i.e., a time sensitive evaluation whose properties of matching
     * only events within an specific time interval can be used for
     * determining event expirations automatically. 
     * 
     * @return true if the evaluator is a temporal evaluator. 
     */
    boolean isTemporal();

    /**
     * In case this is a temporal evaluator, returns the interval 
     * in which this evaluator may match the target fact
     * 
     * @return
     */
    Interval getInterval();

}
