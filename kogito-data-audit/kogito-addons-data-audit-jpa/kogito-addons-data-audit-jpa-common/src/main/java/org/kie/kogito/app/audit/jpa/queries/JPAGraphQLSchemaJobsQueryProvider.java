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
package org.kie.kogito.app.audit.jpa.queries;

import java.util.List;

import org.kie.kogito.app.audit.api.DataAuditContext;
import org.kie.kogito.app.audit.graphql.type.JobExecutionTO;
import org.kie.kogito.app.audit.spi.GraphQLSchemaQuery;
import org.kie.kogito.app.audit.spi.GraphQLSchemaQueryProvider;

public class JPAGraphQLSchemaJobsQueryProvider implements GraphQLSchemaQueryProvider {

    private static final String JOB_PROCESS_ID_COL = "o1.process_id";
    private static final String JOB_PROCESS_VERSION_COL = "o1.process_version";
    private static final String JOB_ROOT_PROCESS_ID_COL = "o1.root_process_id";
    private static final String JOB_ROOT_PROCESS_VERSION_COL = "o1.root_process_version";

    @Override
    public List<GraphQLSchemaQuery> queries(DataAuditContext dataAuditContext) {
        return List.of(
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllScheduledJobs", "GetAllScheduledJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetJobById", JobExecutionTO.class),
                new JPASimpleNamedQuery<JobExecutionTO>("GetJobHistoryById", JobExecutionTO.class),
                new JPASimpleNamedQuery<JobExecutionTO>("GetJobHistoryByProcessInstanceId", JobExecutionTO.class),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllPendingJobs", "GetAllPendingJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllEligibleJobsForExecution", "GetAllEligibleJobsForExecution",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllEligibleJobsForRetry", "GetAllEligibleJobsForRetry",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllJobs", "GetAllJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllCompletedJobs", "GetAllCompletedJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllInErrorJobs", "GetAllInErrorJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllCancelledJobs", "GetAllCancelledJobs",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetAllJobsByStatus", "GetAllJobsByStatus",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL),
                new JPASimpleNamedQuery<JobExecutionTO>("GetJobByProcessInstanceId", "GetJobByProcessInstanceId",
                        JobExecutionTO.class, JOB_PROCESS_ID_COL, JOB_PROCESS_VERSION_COL, JOB_ROOT_PROCESS_ID_COL, JOB_ROOT_PROCESS_VERSION_COL));
    }
}
