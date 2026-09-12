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
package org.drools.model.view;

import java.util.stream.Stream;

import org.drools.model.Condition;
import org.drools.model.Variable;

public class CombinedExprViewItem<T> extends AbstractExprViewItem<T> implements org.drools.model.SequenceStep {

    private final Condition.Type type;
    private final ViewItem[] expressions;

    public CombinedExprViewItem(Condition.Type type, ViewItem[] expressions) {
        super(getCombinedVariable(expressions));
        this.type = type;
        this.expressions = expressions;
    }

    public ViewItem[] getExpressions() {
        return expressions;
    }

    @Override
    public Variable<?>[] getVariables() {
        return Stream.of(expressions)
                     .flatMap( expr -> Stream.of(expr.getVariables()) )
                     .distinct()
                     .toArray(Variable[]::new);
    }

    @Override
    public Variable<T> getFirstVariable() {
        Variable<T> var = expressions[0].getFirstVariable();
        for (int i = 1; i < expressions.length; i++) {
            if (var != expressions[i].getFirstVariable()) {
                return null;
            }
        }
        return var;
    }

    @Override
    public Condition.Type getType() {
        return type;
    }

    private static Variable getCombinedVariable(ViewItem... expressions) {
        Variable var = null;
        for (ViewItem expression : expressions) {
            if (var == null) {
                var = expression.getFirstVariable();
            } else if (var != expression.getFirstVariable()) {
                return null;
            }
        }
        return var;
    }

    @Override
    public void setQueryExpression( boolean queryExpression ) {
        super.setQueryExpression( queryExpression );
        for (ViewItem expr : expressions) {
            if (expr instanceof AbstractExprViewItem) {
                (( AbstractExprViewItem ) expr).setQueryExpression( queryExpression );
            }
        }
    }
}
