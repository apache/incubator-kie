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
package org.kie.kogito.usertask.impl.lifecycle;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.descriptors.UserTaskInstanceJobDescription;
import org.kie.kogito.usertask.UserTaskAssignmentStrategy;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.UserTaskInstanceNotAuthorizedException;
import org.kie.kogito.usertask.impl.DefaultUserTaskInstance;
import org.kie.kogito.usertask.lifecycle.UserTaskLifeCycle;
import org.kie.kogito.usertask.lifecycle.UserTaskState;
import org.kie.kogito.usertask.lifecycle.UserTaskState.TerminationType;
import org.kie.kogito.usertask.lifecycle.UserTaskTransition;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionException;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyMap;
import static org.drools.base.time.TimeUtils.parseTimeString;

public class WsHumanTaskLifeCycle implements UserTaskLifeCycle {

    private static final Logger LOG = LoggerFactory.getLogger(WsHumanTaskLifeCycle.class);

    private static final String WORKFLOW_ENGINE_USER = "WORKFLOW_ENGINE_USER";
    private static final String PARAMETER_USER = "USER";
    private static final String PARAMETER_NOTIFY = "NOTIFY";
    private static final String PARAMETER_DELEGATED_USER = "DELEGATED_USER";
    private static final String PARAMETER_FORWARDED_USERS = "FORWARDED_USERS";
    private static final String PARAMETER_NOMINATED_USERS = "NOMINATED_USERS";
    private static final String PARAMETER_SUSPEND_UNTIL = "suspendUntil";

    private static final String SKIPPABLE = "Skippable";
    private static final String SUSPEND_UNTIL = "SuspendUntil";
    private static final String SUSPENDED_TASK_JOB_ID = "SuspendedTaskJobId";
    private static final String PREVIOUS_STATUS = "PreviousStatus";

    public static final String ACTIVATE = "activate";
    public static final String NOMINATE = "nominate";
    public static final String CLAIM = "claim";
    public static final String DELEGATE = "delegate";
    public static final String RELEASE = "release";
    public static final String FORWARD = "forward";
    public static final String START = "start";
    public static final String STOP = "stop";
    public static final String COMPLETE = "complete";
    public static final String FAIL = "fail";
    public static final String FAULT = "fault";
    public static final String EXIT = "exit";
    public static final String SKIP = "skip";
    public static final String SUSPEND = "suspend";
    public static final String RESUME = "resume";

    public static final UserTaskState CREATED = UserTaskState.initalized();
    public static final UserTaskState READY = UserTaskState.of("Ready");
    public static final UserTaskState RESERVED = UserTaskState.of("Reserved");
    public static final UserTaskState INPROGRESS = UserTaskState.of("InProgress");
    public static final UserTaskState COMPLETED = UserTaskState.of("Completed", TerminationType.COMPLETED);
    public static final UserTaskState FAILED = UserTaskState.of("Failed", TerminationType.FAILED);
    public static final UserTaskState ERROR = UserTaskState.of("Error", TerminationType.ERROR);
    public static final UserTaskState EXITED = UserTaskState.of("Exited", TerminationType.EXITED);
    public static final UserTaskState OBSOLETE = UserTaskState.of("Obsolete", TerminationType.OBSOLETE);
    public static final UserTaskState SUSPENDED = UserTaskState.of("Suspended");

    private final UserTaskTransition T_CREATED_READY_ACTIVATE = new DefaultUserTransition(ACTIVATE, CREATED, READY, this::activate);
    private final UserTaskTransition T_CREATED_READY_NOMINATE = new DefaultUserTransition(NOMINATE, CREATED, READY, this::nominate);
    private final UserTaskTransition T_CREATED_OBSOLETE = new DefaultUserTransition(SKIP, CREATED, OBSOLETE, this::skip);
    private final UserTaskTransition T_CREATED_EXITED = new DefaultUserTransition(EXIT, CREATED, EXITED, this::exit);
    private final UserTaskTransition T_CREATED_ERROR = new DefaultUserTransition(FAULT, CREATED, ERROR, this::fault);

    private final UserTaskTransition T_READY_READY_FORWARD = new DefaultUserTransition(FORWARD, READY, READY, this::forward);
    private final UserTaskTransition T_READY_RESERVED_CLAIM = new DefaultUserTransition(CLAIM, READY, RESERVED, this::claim);
    private final UserTaskTransition T_READY_RESERVED_DELEGATE = new DefaultUserTransition(DELEGATE, READY, RESERVED, this::delegate);
    private final UserTaskTransition T_READY_INPROGRESS = new DefaultUserTransition(START, READY, INPROGRESS, this::start);
    private final UserTaskTransition T_READY_ERROR = new DefaultUserTransition(FAULT, READY, ERROR, this::fault);
    private final UserTaskTransition T_READY_EXITED = new DefaultUserTransition(EXIT, READY, EXITED, this::exit);
    private final UserTaskTransition T_READY_OBSOLETE = new DefaultUserTransition(SKIP, READY, OBSOLETE, this::skip);
    private final UserTaskTransition T_READY_SUSPENDED = new DefaultUserTransition(SUSPEND, READY, SUSPENDED, this::suspend);

    private final UserTaskTransition T_RESERVED_READY_RELEASE = new DefaultUserTransition(RELEASE, RESERVED, READY, this::release);
    private final UserTaskTransition T_RESERVED_READY_FORWARD = new DefaultUserTransition(FORWARD, RESERVED, READY, this::forward);
    private final UserTaskTransition T_RESERVED_INPROGRESS = new DefaultUserTransition(START, RESERVED, INPROGRESS, this::start);
    private final UserTaskTransition T_RESERVED_RESERVED_DELEGATE = new DefaultUserTransition(DELEGATE, RESERVED, RESERVED, this::delegate);
    private final UserTaskTransition T_RESERVED_ERROR = new DefaultUserTransition(FAULT, RESERVED, ERROR, this::fault);
    private final UserTaskTransition T_RESERVED_EXITED = new DefaultUserTransition(EXIT, RESERVED, EXITED, this::exit);
    private final UserTaskTransition T_RESERVED_OBSOLETE = new DefaultUserTransition(SKIP, RESERVED, OBSOLETE, this::skip);
    private final UserTaskTransition T_RESERVED_SUSPENDED = new DefaultUserTransition(SUSPEND, RESERVED, SUSPENDED, this::suspend);

    private final UserTaskTransition T_INPROGRESS_RESERVED_STOP = new DefaultUserTransition(STOP, INPROGRESS, RESERVED, this::stop);
    private final UserTaskTransition T_INPROGRESS_RESERVED_DELEGATE = new DefaultUserTransition(DELEGATE, INPROGRESS, RESERVED, this::delegate);
    private final UserTaskTransition T_INPROGRESS_READY_RELEASE = new DefaultUserTransition(RELEASE, INPROGRESS, READY, this::release);
    private final UserTaskTransition T_INPROGRESS_READY_FORWARD = new DefaultUserTransition(FORWARD, INPROGRESS, READY, this::forward);
    private final UserTaskTransition T_INPROGRESS_COMPLETED = new DefaultUserTransition(COMPLETE, INPROGRESS, COMPLETED, this::complete);
    private final UserTaskTransition T_INPROGRESS_FAILED = new DefaultUserTransition(FAIL, INPROGRESS, FAILED, this::fail);
    private final UserTaskTransition T_INPROGRESS_ERROR = new DefaultUserTransition(FAULT, INPROGRESS, ERROR, this::fault);
    private final UserTaskTransition T_INPROGRESS_EXITED = new DefaultUserTransition(EXIT, INPROGRESS, EXITED, this::exit);
    private final UserTaskTransition T_INPROGRESS_OBSOLETE = new DefaultUserTransition(SKIP, INPROGRESS, OBSOLETE, this::skip);
    private final UserTaskTransition T_INPROGRESS_SUSPENDED = new DefaultUserTransition(SUSPEND, INPROGRESS, SUSPENDED, this::suspend);

    private final UserTaskTransition T_SUSPENDED_READY = new DefaultUserTransition(RESUME, SUSPENDED, READY, this::resume);
    private final UserTaskTransition T_SUSPENDED_RESERVED = new DefaultUserTransition(RESUME, SUSPENDED, RESERVED, this::resume);
    private final UserTaskTransition T_SUSPENDED_INPROGRESS = new DefaultUserTransition(RESUME, SUSPENDED, INPROGRESS, this::resume);
    private final UserTaskTransition T_SUSPENDED_EXITED = new DefaultUserTransition(EXIT, SUSPENDED, EXITED, this::resumeAndExit);

    private List<UserTaskTransition> transitions;
    private Set<UserTaskTransition> engineOnlyTransitions;
    private Set<UserTaskTransition> adminOnlyTransitions;

    public WsHumanTaskLifeCycle() {
        transitions = List.of(
                T_CREATED_READY_ACTIVATE,
                T_CREATED_READY_NOMINATE,
                T_CREATED_OBSOLETE,
                T_CREATED_EXITED,
                T_CREATED_ERROR,
                T_READY_READY_FORWARD,
                T_READY_RESERVED_CLAIM,
                T_READY_RESERVED_DELEGATE,
                T_READY_INPROGRESS,
                T_READY_ERROR,
                T_READY_EXITED,
                T_READY_OBSOLETE,
                T_READY_SUSPENDED,
                T_RESERVED_READY_RELEASE,
                T_RESERVED_READY_FORWARD,
                T_RESERVED_INPROGRESS,
                T_RESERVED_RESERVED_DELEGATE,
                T_RESERVED_ERROR,
                T_RESERVED_EXITED,
                T_RESERVED_OBSOLETE,
                T_RESERVED_SUSPENDED,
                T_INPROGRESS_RESERVED_STOP,
                T_INPROGRESS_RESERVED_DELEGATE,
                T_INPROGRESS_READY_RELEASE,
                T_INPROGRESS_READY_FORWARD,
                T_INPROGRESS_COMPLETED,
                T_INPROGRESS_FAILED,
                T_INPROGRESS_ERROR,
                T_INPROGRESS_EXITED,
                T_INPROGRESS_OBSOLETE,
                T_INPROGRESS_SUSPENDED,
                T_SUSPENDED_READY,
                T_SUSPENDED_RESERVED,
                T_SUSPENDED_INPROGRESS,
                T_SUSPENDED_EXITED);
        engineOnlyTransitions = Set.of(T_CREATED_READY_ACTIVATE, T_SUSPENDED_EXITED);
        adminOnlyTransitions = Set.of(T_CREATED_READY_NOMINATE, T_CREATED_OBSOLETE, T_CREATED_EXITED, T_CREATED_ERROR);
    }

    @Override
    public List<UserTaskTransition> allowedTransitions(UserTaskInstance userTaskInstance, IdentityProvider identity) {
        checkPermission(userTaskInstance, identity);
        boolean isAdmin = isAdmin(userTaskInstance, identity);
        String previousStatus = Optional.ofNullable(userTaskInstance.getMetadata().get(PREVIOUS_STATUS))
                .map(Object::toString)
                .orElse(null);
        return transitions.stream()
                .filter(t -> t.source().equals(userTaskInstance.getStatus()))
                .filter(t -> !engineOnlyTransitions.contains(t))
                .filter(t -> !adminOnlyTransitions.contains(t) || isAdmin)
                .filter(t -> !t.id().equals(SKIP) || "true".equals(userTaskInstance.getMetadata().get(SKIPPABLE)))
                .filter(t -> !t.id().equals(RESUME) || t.target().getName().equals(previousStatus))
                .toList();
    }

    @Override
    public String startTransition() {
        return ACTIVATE;
    }

    @Override
    public String reassignTransition() {
        return FORWARD;
    }

    @Override
    public String abortTransition() {
        return EXIT;
    }

    @Override
    public Optional<UserTaskTransitionToken> transition(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        checkPermission(userTaskInstance, identityProvider);
        UserTaskTransition transition = transitions.stream()
                .filter(t -> t.source().equals(userTaskInstance.getStatus()) && t.id().equals(token.transitionId()) && t.target().equals(token.target()))
                .findFirst()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition from " + userTaskInstance.getStatus()));
        return transition.executor().execute(userTaskInstance, token, identityProvider);
    }

    @Override
    public UserTaskTransitionToken newCompleteTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken(COMPLETE, userTaskInstance.getStatus(), null, data);
    }

    @Override
    public UserTaskTransitionToken newAbortTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken(FAIL, userTaskInstance.getStatus(), null, data);
    }

    @Override
    public UserTaskTransitionToken newTransitionToken(String transitionId, UserTaskInstance userTaskInstance, Map<String, Object> data) {
        if (transitionId.equals(ACTIVATE) && userTaskInstance.getPotentialUsers().isEmpty() && userTaskInstance.getPotentialGroups().isEmpty()) {
            return null;
        }
        return newTransitionToken(transitionId, userTaskInstance.getStatus(), (String) userTaskInstance.getMetadata().get(PREVIOUS_STATUS), data);
    }

    public UserTaskTransitionToken newTransitionToken(String transitionId, UserTaskState state, String previousState, Map<String, Object> data) {
        var transition = transitions.stream()
                .filter(e -> e.source().equals(state) && e.id().equals(transitionId) && (!transitionId.equals(RESUME) || e.target().getName().equals(previousState)))
                .findAny()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition " + transitionId + " from " + state));

        Map<String, Object> normalizedData = new HashMap<>();
        data.forEach((k, v) -> {
            if (PARAMETER_USER.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_USER, v);
            } else if (PARAMETER_NOTIFY.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_NOTIFY, v);
            } else if (PARAMETER_DELEGATED_USER.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_DELEGATED_USER, v);
            } else if (PARAMETER_FORWARDED_USERS.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_FORWARDED_USERS, v);
            } else if (PARAMETER_NOMINATED_USERS.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_NOMINATED_USERS, v);
            } else if (PARAMETER_SUSPEND_UNTIL.equalsIgnoreCase(k)) {
                normalizedData.put(PARAMETER_SUSPEND_UNTIL, v);
            } else {
                normalizedData.put(k, v);
            }
        });
        return new DefaultUserTaskTransitionToken(transition.id(), transition.source(), transition.target(), normalizedData);
    }

    public Optional<UserTaskTransitionToken> activate(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        userTaskInstance.startNotCompletedDeadlines();
        userTaskInstance.startNotCompletedReassignments();

        String user = assignStrategy(userTaskInstance, identityProvider);
        if (user != null) {
            return Optional.of(newTransitionToken(CLAIM, READY, null, Map.of(PARAMETER_USER, user)));
        }
        userTaskInstance.startNotStartedDeadlines();
        userTaskInstance.startNotStartedReassignments();
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> nominate(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (!isAdmin(userTaskInstance, identityProvider)) {
            throw new UserTaskTransitionException("User is not allowed to nominate");
        }

        if (token.data().containsKey(PARAMETER_NOMINATED_USERS)) {
            try {
                userTaskInstance.setPotentialUsers(new HashSet<>((List<String>) token.data().get(PARAMETER_NOMINATED_USERS)));
            } catch (ClassCastException e) {
                throw new UserTaskTransitionException("Illegal format for nominated users");
            }
        } else {
            throw new UserTaskTransitionException("Nominated user(s) are not specified");
        }

        return activate(userTaskInstance, token, identityProvider);
    }

    public Optional<UserTaskTransitionToken> claim(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_USER)) {
            userTaskInstance.setActualOwner((String) token.data().get(PARAMETER_USER));
        } else {
            userTaskInstance.setActualOwner(identityProvider.getName());
        }
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> delegate(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_DELEGATED_USER)) {
            var potentialUsers = new HashSet<>(userTaskInstance.getPotentialUsers());
            potentialUsers.add((String) token.data().get(PARAMETER_DELEGATED_USER));
            userTaskInstance.setPotentialUsers(potentialUsers);
            userTaskInstance.setActualOwner((String) token.data().get(PARAMETER_DELEGATED_USER));
        } else {
            throw new UserTaskTransitionException("Delegated user not specified");
        }
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> forward(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_FORWARDED_USERS)) {
            Set<String> newPotentialUsers;
            try {
                newPotentialUsers = new HashSet<>((List<String>) token.data().get(PARAMETER_FORWARDED_USERS));
            } catch (ClassCastException e) {
                throw new UserTaskTransitionException("Illegal format for forwarded users");
            }
            var existingPotentialUsers = new HashSet<>(userTaskInstance.getPotentialUsers());
            existingPotentialUsers.remove(identityProvider.getName());
            existingPotentialUsers.addAll(newPotentialUsers);
            userTaskInstance.setPotentialUsers(existingPotentialUsers);
            userTaskInstance.setActualOwner(null);
        } else {
            throw new UserTaskTransitionException("Forwarded user(s) not specified");
        }
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> start(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_USER)) {
            userTaskInstance.setActualOwner((String) token.data().get(PARAMETER_USER));
        } else {
            userTaskInstance.setActualOwner(identityProvider.getName());
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> release(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        userTaskInstance.setActualOwner(null);
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> stop(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> complete(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        token.data().forEach(userTaskInstance::setOutput);
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        userTaskInstance.stopNotCompletedDeadlines();
        userTaskInstance.stopNotCompletedReassignments();
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> fault(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_NOTIFY)) {
            userTaskInstance.getMetadata().put(PARAMETER_NOTIFY, token.data().get(PARAMETER_NOTIFY));
        }
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        userTaskInstance.stopNotCompletedDeadlines();
        userTaskInstance.stopNotCompletedReassignments();
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> exit(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_NOTIFY)) {
            userTaskInstance.getMetadata().put(PARAMETER_NOTIFY, token.data().get(PARAMETER_NOTIFY));
        }
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        userTaskInstance.stopNotCompletedDeadlines();
        userTaskInstance.stopNotCompletedReassignments();
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> skip(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (CREATED.equals(userTaskInstance.getStatus()) && !isAdmin(userTaskInstance, identityProvider)) {
            throw new UserTaskTransitionException("User is not allowed to skip");
        }
        if (!Boolean.parseBoolean((String) userTaskInstance.getMetadata().get(SKIPPABLE))) {
            throw new UserTaskTransitionException("Usertask cannot be skipped");
        }
        if (token.data().containsKey(PARAMETER_NOTIFY)) {
            userTaskInstance.getMetadata().put(PARAMETER_NOTIFY, token.data().get(PARAMETER_NOTIFY));
        }
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        userTaskInstance.stopNotCompletedDeadlines();
        userTaskInstance.stopNotCompletedReassignments();
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> fail(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (token.data().containsKey(PARAMETER_NOTIFY)) {
            userTaskInstance.getMetadata().put(PARAMETER_NOTIFY, token.data().get(PARAMETER_NOTIFY));
        }
        token.data().forEach(userTaskInstance::setOutput);
        userTaskInstance.stopNotStartedDeadlines();
        userTaskInstance.stopNotStartedReassignments();
        userTaskInstance.stopNotCompletedDeadlines();
        userTaskInstance.stopNotCompletedReassignments();
        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> suspend(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        if (userTaskInstance.getStatus() != null) {
            var suspendUntil = token.data().get(PARAMETER_SUSPEND_UNTIL) != null ? (String) token.data().get(PARAMETER_SUSPEND_UNTIL) : (String) userTaskInstance.getMetadata().get(SUSPEND_UNTIL);

            if (suspendUntil != null) {
                ZonedDateTime expirationTime = parseTemporalValue(suspendUntil);

                UserTaskInstanceJobDescription jobDescription = UserTaskInstanceJobDescription.newUserTaskInstanceJobDescriptionBuilder()
                        .id(UUID.randomUUID().toString())
                        .expirationTime(ExactExpirationTime.of(expirationTime))
                        .userTaskInstanceId(userTaskInstance.getId())
                        .processId(userTaskInstance.getProcessInfo().getProcessId())
                        .processVersion(userTaskInstance.getProcessInfo().getProcessVersion())
                        .processInstanceId(userTaskInstance.getProcessInfo().getProcessInstanceId())
                        .rootProcessInstanceId(userTaskInstance.getProcessInfo().getRootProcessInstanceId())
                        .rootProcessId(userTaskInstance.getProcessInfo().getRootProcessId())
                        .rootProcessVersion(userTaskInstance.getProcessInfo().getRootProcessVersion())
                        .metadata(userTaskInstance.getMetadata())
                        .build();

                LOG.debug("Suspending usertask with id {} until {}", userTaskInstance.getId(), expirationTime);

                String jobId = ((DefaultUserTaskInstance) userTaskInstance).getJobsService().scheduleJob(jobDescription);
                userTaskInstance.getMetadata().put(SUSPENDED_TASK_JOB_ID, jobId);
            }

            userTaskInstance.getMetadata().put(PREVIOUS_STATUS, userTaskInstance.getStatus().getName());
        }
        return Optional.empty();
    }

    private static ZonedDateTime parseTemporalValue(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("suspendUntil cannot be null or empty");
        }

        try {
            var timestamp = ZonedDateTime.parse(input);
            var now = ZonedDateTime.now();
            if (timestamp.isAfter(now)) {
                return timestamp;
            }

            return parseDuration(input);
        } catch (DateTimeParseException e) {
            return parseDuration(input);
        }
    }

    private static ZonedDateTime parseDuration(String input) {
        try {
            var duration = Duration.parse(input);
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Invalid suspendUntil duration: " + input);
            }

            return ZonedDateTime.now().plus(duration);
        } catch (DateTimeParseException e) {
            return parseSimpleDuration(input);
        }
    }

    private static ZonedDateTime parseSimpleDuration(String input) {
        try {
            long millis = parseTimeString(input);
            if (millis <= 0) {
                throw new IllegalArgumentException("Invalid suspendUntil duration: " + input);
            }

            return ZonedDateTime.now().plus(Duration.ofMillis(millis));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid suspendUntil duration: " + input);
        }
    }

    private Optional<UserTaskTransitionToken> resume(UserTaskInstance userTaskInstance, UserTaskTransitionToken token, IdentityProvider identityProvider) {
        userTaskInstance.getMetadata().remove(PREVIOUS_STATUS);

        var suspendedTaskJobId = (String) userTaskInstance.getMetadata().get(SUSPENDED_TASK_JOB_ID);
        if (suspendedTaskJobId != null) {
            ((DefaultUserTaskInstance) userTaskInstance).getJobsService().cancelJob(suspendedTaskJobId);
            userTaskInstance.getMetadata().remove(SUSPENDED_TASK_JOB_ID);
        }

        return Optional.empty();
    }

    private Optional<UserTaskTransitionToken> resumeAndExit(UserTaskInstance userTaskInstance, UserTaskTransitionToken userTaskTransitionToken, IdentityProvider identityProvider) {
        if (!identityProvider.getName().equals(WORKFLOW_ENGINE_USER)) {
            throw new UserTaskInstanceNotAuthorizedException("user " + identityProvider.getName() + " not authorized to perform this operation on user task " + userTaskInstance.getId());
        }

        resume(userTaskInstance, userTaskTransitionToken, identityProvider);
        return exit(userTaskInstance, userTaskTransitionToken, identityProvider);
    }

    @Override
    public void handleTimer(UserTaskInstanceJobDescription jobDescription, UserTaskInstance userTaskInstance) {
        if (SUSPENDED.equals(userTaskInstance.getStatus()) && jobDescription.id().equals(userTaskInstance.getMetadata().get(SUSPENDED_TASK_JOB_ID))) {
            LOG.debug("Auto resuming suspended usertask with id:{} after expiration", userTaskInstance.getId());
            userTaskInstance.getMetadata().remove(SUSPENDED_TASK_JOB_ID);
            userTaskInstance.transition(RESUME, emptyMap(), IdentityProviders.of(WORKFLOW_ENGINE_USER));
        }
    }

    private String assignStrategy(UserTaskInstance userTaskInstance, IdentityProvider identityProvider) {
        UserTaskAssignmentStrategy assignmentStrategy = userTaskInstance.getUserTask().getAssignmentStrategy();
        return assignmentStrategy.computeAssignment(userTaskInstance, identityProvider).orElse(null);
    }

    private boolean isAdmin(UserTaskInstance userTaskInstance, IdentityProvider identityProvider) {
        return isAdmin(userTaskInstance, identityProvider.getName(), identityProvider.getRoles());
    }

    private boolean isAdmin(UserTaskInstance userTaskInstance, String user, Collection<String> roles) {
        return userTaskInstance.getAdminUsers().contains(user)
                || !Collections.disjoint(userTaskInstance.getAdminGroups(), roles);
    }

    private void checkPermission(UserTaskInstance userTaskInstance, IdentityProvider identityProvider) {
        this.checkPermission(userTaskInstance, identityProvider.getName(), identityProvider.getRoles());
    }

    private void checkPermission(UserTaskInstance userTaskInstance, String user, Collection<String> roles) {
        if (WORKFLOW_ENGINE_USER.equals(user)) {
            return;
        }

        if (isAdmin(userTaskInstance, user, roles)) {
            return;
        }

        if (userTaskInstance.getActualOwner() != null && userTaskInstance.getActualOwner().equals(user)) {
            return;
        }

        if (userTaskInstance.getExcludedUsers().contains(user)) {
            String message = String.format("User '%s' is not authorized to perform an operation on user task '%s'",
                    user, userTaskInstance.getId());
            throw new UserTaskInstanceNotAuthorizedException(message);
        }

        if (READY.equals(userTaskInstance.getStatus())
                || (SUSPENDED.equals(userTaskInstance.getStatus()) && READY.getName().equals(userTaskInstance.getMetadata().get(PREVIOUS_STATUS)))) {

            if (userTaskInstance.getPotentialUsers().contains(user)
                    && !userTaskInstance.getExcludedUsers().contains(user)) {
                return;
            }

            if (!Collections.disjoint(userTaskInstance.getPotentialGroups(), roles)) {
                return;
            }
        }

        throw new UserTaskInstanceNotAuthorizedException("user " + user + " with roles " + roles + " not authorized to perform an operation on user task " + userTaskInstance.getId());
    }

}
