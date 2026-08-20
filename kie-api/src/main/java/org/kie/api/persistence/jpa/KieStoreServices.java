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
package org.kie.api.persistence.jpa;

import org.kie.api.KieBase;
import org.kie.api.internal.utils.KieService;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;

public interface KieStoreServices extends KieService {

    KieSession newKieSession(KieBase kbase,
                             KieSessionConfiguration configuration,
                             Environment environment);

    /**
     * Deprecated use {@link  #loadKieSession(Long, KieBase, KieSessionConfiguration, Environment)} instead
     *
     * @param id the session id
     * @param kbase the KieBase
     * @param configuration the session configuration
     * @param environment the environment
     * @return the loaded KieSession
     * @deprecated since 6.3.0, use {@link #loadKieSession(Long, KieBase, KieSessionConfiguration, Environment)} instead.
     *             This overload uses an {@code int} session id; use the {@code Long}-based overload for a wider id range.
     *             Will be removed in a future version.
     */
    @Deprecated(since = "6.3.0", forRemoval = true)
    KieSession loadKieSession(int id,
                              KieBase kbase,
                              KieSessionConfiguration configuration,
                              Environment environment);

    KieSession loadKieSession(Long id,
            KieBase kbase,
            KieSessionConfiguration configuration,
            Environment environment);

}
