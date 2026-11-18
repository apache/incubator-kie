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
package org.drools.core.event.rule.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.kie.api.runtime.rule.RuleFlowGroup;

public class SerializableRuleFlowGroup implements RuleFlowGroup, Externalizable {
    
    private String name;

    // This should be used just for deserialization purposes.
    public SerializableRuleFlowGroup() { }
    
    SerializableRuleFlowGroup(RuleFlowGroup ruleFlowGroup) {
        this.name = ruleFlowGroup.getName();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF( this.name );
    }
    
    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        this.name = in.readUTF();
    }
    
    public String getName() {
        return this.name;
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public void activate() {
        throw new UnsupportedOperationException();
    }

}
