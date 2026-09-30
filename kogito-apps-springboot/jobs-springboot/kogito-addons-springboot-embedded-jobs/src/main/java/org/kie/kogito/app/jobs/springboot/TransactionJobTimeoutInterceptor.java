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
package org.kie.kogito.app.jobs.springboot;

import java.util.concurrent.Callable;

import org.kie.kogito.app.jobs.api.JobTimeoutExecution;
import org.kie.kogito.app.jobs.api.JobTimeoutInterceptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class TransactionJobTimeoutInterceptor implements JobTimeoutInterceptor {

    private final TransactionTemplate transactionTemplate;

    public TransactionJobTimeoutInterceptor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Callable<JobTimeoutExecution> chainIntercept(Callable<JobTimeoutExecution> callable) {
        return new Callable<JobTimeoutExecution>() {

            @Override
            public JobTimeoutExecution call() throws Exception {
                return transactionTemplate.execute(status -> {
                    SpringBootTransactionRollbackMarker.setCurrentTransactionStatus(status);
                    try {
                        return callable.call();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        SpringBootTransactionRollbackMarker.clearCurrentTransactionStatus();
                    }
                });
            }

        };
    }

}
