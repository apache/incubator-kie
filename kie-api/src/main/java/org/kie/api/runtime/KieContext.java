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
package org.kie.api.runtime;

import org.kie.api.KieBase;

public interface KieContext {
    KieRuntime getKieRuntime();

    default KieBase getKieBase() {
        return getKieRuntime().getKieBase();
    }

    /**
     * Added for backwards compatibility.
     * Will be removed in the future.
     *
     * @return the KieRuntime instance
     * @deprecated since 6.0.0, use {@link #getKieRuntime()} instead. This method uses the old
     *             "Knowledge" naming convention from before the Kie rebrand. Will be removed in a future version.
     */
    @Deprecated(since = "6.0.0", forRemoval = true)
    KieRuntime getKnowledgeRuntime();
}
