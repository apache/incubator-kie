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
package org.kie.kogito.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.NotAuthorizedException;
import org.kie.kogito.process.workitem.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security policy that delivers <code>IdentityProvider</code> to allow to security
 * related policy enforcement.
 *
 */
public class SecurityPolicy implements Policy {

    static final String NOT_AUTHORIZED_MESSAGE_TEMPLATE = "Identity '%s' with roles '%s' is not allowed to access workItem '%s'";

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityPolicy.class);

    private IdentityProvider identity;

    /**
     * Creates new SecurityPolicy for given name, roles
     *
     */
    public static SecurityPolicy of(String name, Collection<String> roles) {
        return new SecurityPolicy(IdentityProviders.of(name, roles));
    }

    /**
     * Creates new SecurityPolicy for given identity provider
     * 
     * @param identity non null identity provider
     * @return new instance of SecurityPolicy
     */
    public static SecurityPolicy of(IdentityProvider identity) {
        Objects.requireNonNull(identity);
        return new SecurityPolicy(identity);
    }

    protected SecurityPolicy(IdentityProvider identity) {
        this.identity = identity;
    }

    public String getUser() {
        return identity.getName();
    }

    public Collection<String> getRoles() {
        return identity.getRoles();
    }

    @Override
    public void enforce(KogitoWorkItem workItem) {
        if (workItem.getActualOwner() != null && workItem.getActualOwner().equals(getUser())) {
            return;
        }

        String actualOwner = workItem.getActualOwner();
        String actualOwners = (String) workItem.getParameter("ActorId");
        String actualRoles = (String) workItem.getParameter("GroupId");
        String excludedOwner = (String) workItem.getParameter("ExcludedOwnerId");
        if (actualOwners != null || actualRoles != null) {
            List<String> owners = actualOwners != null ? new ArrayList<>(List.of(actualOwners.split(","))) : new ArrayList<>();
            List<String> excluded = excludedOwner != null ? new ArrayList<>(List.of(excludedOwner.split(","))) : new ArrayList<>();
            owners.removeAll(excluded);
            List<String> roles = actualRoles != null ? List.of(actualRoles.split(",")) : new ArrayList<>();
            List<String> userRoles = new ArrayList<>(identity.getRoles());
            userRoles.retainAll(roles);
            LOGGER.debug("enforcing identity {} and roles {} with potential owners {} and potential groups {} and exclude groups {} on workItem {}",
                    identity.getName(), identity.getRoles(), owners, roles, excluded, workItem.getStringId());
            if (!owners.contains(identity.getName()) && userRoles.isEmpty()) {
                LOGGER.debug("not authorized with owner {} against identity {}", actualOwner, identity.getName());
                throw new NotAuthorizedException(NOT_AUTHORIZED_MESSAGE_TEMPLATE.formatted(identity.getName(), identity.getRoles(), workItem.getStringId()));
            } else if (userRoles.isEmpty() && actualOwner != null && !identity.getName().equals(actualOwner)) {
                LOGGER.debug("identity {} with roles {} not authorized in {}", identity.getName(), identity.getRoles(), roles);
                throw new NotAuthorizedException(NOT_AUTHORIZED_MESSAGE_TEMPLATE.formatted(identity.getName(), identity.getRoles(), workItem.getStringId()));
            }
        }
    }

    @Override
    public String toString() {
        return "SecurityPolicy [identity=" + identity.getName() + ", roles=" + identity.getRoles() + "]";
    }

}
