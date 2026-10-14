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
package org.kogito.workitem.rest.decorators;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.kie.kogito.process.workitem.KogitoWorkItem;

import io.vertx.mutiny.ext.web.client.HttpRequest;

public abstract class AbstractParamsDecorator implements ParamsDecorator {

    @Override
    public void decorate(KogitoWorkItem item, Map<String, Object> parameters, HttpRequest<?> request) {
        Iterator<Entry<String, Object>> iter = parameters.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, Object> entry = iter.next();
            String key = entry.getKey();
            if (isHeaderParameter(key)) {
                request.putHeader(toHeaderKey(key), entry.getValue().toString());
                iter.remove();
            } else if (isQueryParameter(key)) {
                request.addQueryParam(toQueryKey(key), entry.getValue().toString());
                iter.remove();
            }
        }
    }

    protected String toHeaderKey(String key) {
        return key;
    }

    protected String toQueryKey(String key) {
        return key;
    }

    protected abstract boolean isHeaderParameter(String key);

    protected abstract boolean isQueryParameter(String key);
}
