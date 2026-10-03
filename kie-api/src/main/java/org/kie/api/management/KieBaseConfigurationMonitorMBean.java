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
package org.kie.api.management;

public interface KieBaseConfigurationMonitorMBean {

    /**
     * @return the alpha node hashing threshold
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public int getAlphaNodeHashingThreshold();

    public String getAssertBehaviour();

    /**
     * @return the composite key depth
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public int getCompositeKeyDepth();

    public String getEventProcessingMode();

    /**
     * @return the maximum number of threads
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public int getMaxThreads();

    /**
     * @return the sequential agenda setting
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public String getSequentialAgenda();

    /**
     * @return whether left beta memory is indexed
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isIndexLeftBetaMemory();

    /**
     * @return whether right beta memory is indexed
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isIndexRightBetaMemory();

    /**
     * @return whether TMS is maintained
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isMaintainTms();

    public boolean isMBeansEnabled();

    public boolean isRemoveIdentities();

    /**
     * @return whether sequential mode is enabled
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isSequential();

    /**
     * @return whether alpha nodes are shared
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isShareAlphaNodes();

    /**
     * @return whether beta nodes are shared
     * @deprecated since 8.35.0, this configuration option is no longer used. Will be removed in a future version.
     */
    @Deprecated(since = "8.35.0", forRemoval = true)
    public boolean isShareBetaNodes();

}
