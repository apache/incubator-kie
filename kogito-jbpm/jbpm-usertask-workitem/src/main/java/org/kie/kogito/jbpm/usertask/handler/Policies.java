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
package org.kie.kogito.jbpm.usertask.handler;

import java.util.Collection;
import java.util.Collections;

import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.process.workitem.Policy;

public class Policies {

    public static Policy[] of(String user, Collection<String> roles) {
        return user == null ? new Policy[0] : new Policy[] { SecurityPolicy.of(IdentityProviders.of(user, roles)) };
    }

    public static Policy[] of(String user) {
        return of(user, Collections.emptyList());
    }

    private Policies() {
    }

}
