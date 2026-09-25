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
package org.kie.api.runtime.process;

import java.util.Collection;

/**
 * A node instance container is a container that can contain
 * (zero or more) node instances.
 */
public interface NodeInstanceContainer {

    /**
     * Returns all node instances that are currently active
     * within this container.
     *
     * @return the list of node instances currently active
     */
    Collection<NodeInstance> getNodeInstances();

    /**
     * Returns all node instances that are currently active
     * within this container and are serializable
     *
     * @return the list of serializable node instances currently active
     * @since 10.1.0
     * @apiNote When implementing custom node instances, override this method to exclude
     *          non-serializable instances if your process needs to support persistence.
     *          The default implementation returns all node instances.
     */
    default Collection<NodeInstance> getSerializableNodeInstances() {
        return getNodeInstances(); // defaulting to getNodeInstances to avoid breaking
    }

    /**
     * Returns the node instance with the given id, or <code>null</code>
     * if the node instance cannot be found.
     *
     * @param nodeInstanceId
     * @return the node instance with the given id
     */
    NodeInstance getNodeInstance(long nodeInstanceId);

}
