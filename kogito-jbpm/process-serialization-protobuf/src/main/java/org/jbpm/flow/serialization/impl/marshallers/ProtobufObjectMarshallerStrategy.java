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
package org.jbpm.flow.serialization.impl.marshallers;

import java.io.IOException;

import org.jbpm.flow.serialization.JavaSerializationUtils;
import org.jbpm.flow.serialization.ObjectMarshallerStrategy;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerException;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;

public class ProtobufObjectMarshallerStrategy implements ObjectMarshallerStrategy {

    @Override
    public Integer order() {
        return 1;
    }

    @Override
    public boolean acceptForMarshalling(Object value) {
        return true;
    }

    @Override
    public boolean acceptForUnmarshalling(Any value) {
        return value.is(BytesValue.class);
    }

    @Override
    public Any marshall(Object unmarshalled) {
        try {
            byte[] bytes = JavaSerializationUtils.serialize(unmarshalled);
            return Any.pack(BytesValue.of(ByteString.copyFrom(bytes)));
        } catch (IOException e) {
            throw new ProcessInstanceMarshallerException("Not possible to unmarshall value: " + unmarshalled, e);
        }
    }

    @Override
    public Object unmarshall(Any data) {
        try {
            BytesValue storedValue = data.unpack(BytesValue.class);
            if (ByteString.EMPTY.equals(storedValue.getValue())) {
                return null;
            }
            return JavaSerializationUtils.deserialize(storedValue.getValue().toByteArray());
        } catch (IOException | ClassNotFoundException e) {
            throw new ProcessInstanceMarshallerException("Unexpected error during protobuf object unmarshalling", e);
        }
    }

}
