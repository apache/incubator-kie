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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.kie.kogito.app.audit.api.DataAuditContext;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceAssignmentTO;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceAttachmentTO;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceCommentTO;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceDeadlineTO;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceStateTO;
import org.kie.kogito.app.audit.graphql.type.UserTaskInstanceVariableTO;
import org.kie.kogito.app.audit.jpa.queries.mapper.UserTaskInstanceAssignmentTOMapper;
import org.kie.kogito.app.audit.jpa.queries.mapper.UserTaskInstanceDeadlineTOMapper;
import org.kie.kogito.app.audit.spi.GraphQLSchemaQuery;
import org.kie.kogito.app.audit.spi.GraphQLSchemaQueryProvider;

public class JPAGraphQLSchemaUserTaskInstancesQueryProvider implements GraphQLSchemaQueryProvider {

    @Override
    public List<GraphQLSchemaQuery> queries(DataAuditContext dataAuditContext) {
        return List.of(
                new JPASimpleNamedQuery<UserTaskInstanceStateTO>("GetAllUserTaskInstanceState",
                        UserTaskInstanceStateTO.class),
                new JPASimpleNamedQuery<UserTaskInstanceAttachmentTO>("GetAllUserTaskInstanceAttachments",
                        UserTaskInstanceAttachmentTO.class),
                new JPASimpleNamedQuery<UserTaskInstanceCommentTO>("GetAllUserTaskInstanceComments",
                        UserTaskInstanceCommentTO.class),
                new JPASimpleNamedQuery<UserTaskInstanceVariableTO>("GetAllUserTaskInstanceVariables",
                        UserTaskInstanceVariableTO.class),
                new JPAComplexNamedQuery<UserTaskInstanceAssignmentTO, Object[]>("GetAllUserTaskInstanceAssignments",
                        new UserTaskInstanceAssignmentTOMapper()),
                new JPAComplexNamedQuery<UserTaskInstanceDeadlineTO, Object[]>("GetAllUserTaskInstanceDeadlines",
                        new UserTaskInstanceDeadlineTOMapper()));
    }

    public OffsetDateTime toDateTime(Date date) {
        return (date != null) ? OffsetDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC")) : null;
    }
}
