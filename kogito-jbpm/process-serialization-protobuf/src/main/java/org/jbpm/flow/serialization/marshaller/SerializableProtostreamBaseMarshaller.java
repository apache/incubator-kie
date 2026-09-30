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
package org.jbpm.flow.serialization.marshaller;

import java.io.IOException;
import java.io.Serializable;

import org.infinispan.protostream.MessageMarshaller;
import org.jbpm.flow.serialization.JavaSerializationUtils;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerException;

public class SerializableProtostreamBaseMarshaller implements MessageMarshaller<Serializable> {

    @Override
    public Class<? extends Serializable> getJavaClass() {
        return Serializable.class;
    }

    @Override
    public String getTypeName() {
        return "kogito.Serializable";
    }

    @Override
    public Serializable readFrom(ProtoStreamReader reader) throws IOException {
        byte[] data = reader.readBytes("data");
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return (Serializable) JavaSerializationUtils.deserialize(data);
        } catch (ClassNotFoundException e) {
            throw new ProcessInstanceMarshallerException("Unexpected error while trying to unmarshall object", e);
        }
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Serializable serializable) throws IOException {
        try {
            writer.writeBytes("data", JavaSerializationUtils.serialize(serializable));
        } catch (IOException e) {
            throw new ProcessInstanceMarshallerException("Not possible to marshall value: " + serializable, e);
        }
    }

}
