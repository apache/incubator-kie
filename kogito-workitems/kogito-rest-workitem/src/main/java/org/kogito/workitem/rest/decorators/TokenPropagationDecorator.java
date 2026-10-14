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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.auth.AuthTokenProvider;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitems.impl.ConfigResolverHolder;
import org.kogito.workitem.rest.auth.AuthDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.mutiny.ext.web.client.HttpRequest;

enum AccessTokenAcquisitionStrategy {
    NONE("none"),
    CONFIGURED("configured"),
    PROPAGATED("propagated");

    private String name;

    AccessTokenAcquisitionStrategy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AccessTokenAcquisitionStrategy fromName(String strategyName) {
        return Arrays.stream(AccessTokenAcquisitionStrategy.values())
                .filter(strategy -> strategy.getName().equals(strategyName))
                .findFirst()
                .orElse(NONE);
    }
}

public class TokenPropagationDecorator implements AuthDecorator {

    private static final Logger logger = LoggerFactory.getLogger(TokenPropagationDecorator.class);

    public static final String ACCESS_TOKEN_ACQUISITION_STRATEGY = "AccessTokenAcquisitionStrategy";

    private static final String REST_SERVICE_CALL_TASK_ID = "RestServiceCallTaskId";

    @Override
    public void decorate(KogitoWorkItem item, Map<String, Object> parameters, HttpRequest<?> request) {
        Optional<String> bearerToken = getBearerToken(item, parameters);

        bearerToken.ifPresentOrElse(
                token -> {
                    logger.debug("Rest workItem `{}`: Bearer token available, request will be sent with authentication", item.getNodeInstance().getId());
                    request.bearerTokenAuthentication(token);
                },
                () -> logger.debug("Rest workItem `{}`: No Bearer Token available, request will be sent without authentication", item.getNodeInstance().getId()));
    }

    Optional<String> getBearerToken(KogitoWorkItem item, Map<String, Object> parameters) {
        Object strategyParam = parameters.get(ACCESS_TOKEN_ACQUISITION_STRATEGY);

        if (!(strategyParam instanceof String)) {
            logger.debug("No token acquisition strategy specified, skipping authentication");
            return Optional.empty();
        }

        String strategyName = (String) strategyParam;
        AccessTokenAcquisitionStrategy strategy = AccessTokenAcquisitionStrategy.fromName(strategyName);

        return switch (strategy) {
            case PROPAGATED -> getPropagatedTokenFromHeaders(item);
            case CONFIGURED -> getConfiguredToken(item, parameters);
            default -> {
                logger.debug("Strategy {} is NONE or unknown, skipping", strategyName);
                yield Optional.empty();
            }
        };
    }

    private Optional<String> getPropagatedTokenFromHeaders(KogitoWorkItem item) {
        org.jbpm.process.instance.ProcessInstance jbpmProcessInstance =
                (org.jbpm.process.instance.ProcessInstance) item.getProcessInstance();

        org.kie.kogito.internal.process.runtime.KogitoProcessRuntime processRuntime =
                ((org.jbpm.process.instance.InternalProcessRuntime) jbpmProcessInstance.getKnowledgeRuntime().getProcessRuntime())
                        .getKogitoProcessRuntime();

        ProcessConfig processConfig = processRuntime.getApplication().config().get(ProcessConfig.class);
        AuthTokenProvider authTokenProvider = processConfig.authTokenProvider();

        Optional<String> token = authTokenProvider.getAuthToken();
        if (token.isPresent()) {
            logger.debug("Using propagated token from AuthTokenProvider");
        } else {
            logger.debug("No token available from AuthTokenProvider");
        }
        return token;
    }

    private Optional<String> getConfiguredToken(KogitoWorkItem item, Map<String, Object> parameters) {
        String processId = item.getProcessInstance().getProcessId();
        String taskName = (String) parameters.getOrDefault(REST_SERVICE_CALL_TASK_ID, item.getName());

        if (processId == null || taskName == null) {
            logger.debug("Process ID or task name is null, cannot build configuration key");
            return Optional.empty();
        }

        String configKey = String.format("kogito.processes.%s.%s.access_token", processId, taskName);

        Optional<String> configToken = ConfigResolverHolder.getConfigResolver()
                .getConfigProperty(configKey, String.class);

        if (configToken.isPresent()) {
            logger.debug("Using configured token from ConfigResolver with key: {}", configKey);
            return configToken;
        } else {
            logger.debug("No token found in ConfigResolver for key: {}", configKey);
            return Optional.empty();
        }
    }
}
