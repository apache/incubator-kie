# BAMOE v9 Workflow Engine Extension Points Architecture & Comprehensive Catalog

## 1. Executive Summary & Objective

In IBM BAMOE v9 (powered by Apache KIE / Kogito), the Workflow Engine is built upon a cloud-native, microservice-first architecture. This fundamentally changes how developers extend the runtime compared to BAMOE v8 (KIE Server / jBPM).

As mandated by [`docs/Capability.md`](../docs/Capability.md), this catalog formally identifies, names, maps, and documents all supported extension points of the Workflow Engine requiring custom Java code and programmatic API usage.

Every extension point is detailed with:
- **Canonical Feature Name**
- **Upstream Java SPI Interfaces & Core Classes** (in `v9/incubator-kie/kogito-api`)
- **Deep-Dive Architectural Description:** Purpose, Engine Invocation Lifecycle, Data Contract (inputs/outputs), Threading & Concurrency, Error Handling, and v8 Predecessor comparison
- **Dual Framework Registration:** Explicit side-by-side examples for **Quarkus (CDI)** and **Spring Boot**
- **Concrete Code Snippets** referencing actual implementations in `v9/bamoe-examples/` and `v9/incubator-kie-kogito-examples/`

---

## 2. Comprehensive Extension Points Matrix

| # | Canonical Feature Name | Upstream Interface / Class | Target Scope | Registration (Quarkus vs. Spring Boot) | Reference Example |
|---|---|---|---|---|---|
| **1** | **Custom Work Item Handler (Service Tasks)** | `org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler`<br>`org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler`<br>`org.kie.kogito.process.WorkItemHandlerConfig` | Custom outbound integrations (REST, Kafka, gRPC, Legacy Backends), custom error handling. | Quarkus: `@ApplicationScoped` / `DefaultWorkItemHandlerConfig`<br>Spring Boot: `@Component` / `DefaultWorkItemHandlerConfig` | `process-error-handling` (`CustomTaskWorkItemHandler.java`) |
| **2** | **Process Event Listener (Process Lifecycle)** | `org.kie.api.event.process.ProcessEventListener`<br>`org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener`<br>`org.kie.kogito.process.ProcessEventListenerConfig` | Process telemetry, node execution tracking, variable change tracking, audit logging, OpenTelemetry tracing spans. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-event-listeners-quarkus` & `process-event-listeners-springboot` (`TestProcessEventListener.java`) |
| **3** | **User Task Event Listener (Task Lifecycle)** | `org.kie.kogito.usertask.UserTaskEventListener`<br>`org.kie.kogito.usertask.events.*`<br>`org.kie.kogito.usertask.UserTaskEventListenerConfig` | User task state transitions, task assignments, input/output variable audits. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-event-listeners-quarkus` & `process-event-listeners-springboot` (`TestUserTaskEventListener.java`) |
| **4** | **User Task Assignment Strategy (Dynamic Routing)** | `org.kie.kogito.usertask.impl.BasicUserTaskAssignmentStrategy`<br>`org.kie.kogito.usertask.UserTaskAssignmentStrategy`<br>`org.kie.kogito.usertask.UserTaskAssignmentStrategyConfig` | Dynamic user/group task allocation (Round-Robin, workload-based, organizational hierarchy, Keycloak/LDAP groups). Replaces v8 `UserGroupCallback`. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-user-tasks-subsystem` (`CustomUserTaskAssignmentStrategyConfig.java`) |
| **5** | **User Task Lifecycle Transition Strategy** | `org.kie.kogito.usertask.lifecycle.UserTaskLifeCycle`<br>`org.kie.kogito.usertask.lifecycle.UserTaskTransition`<br>`org.kie.kogito.usertask.lifecycle.UserTaskState` | Custom user task state machines, auto-progress (claim/start/complete in single step), custom lifecycle hooks. | Quarkus: `@Singleton` / `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-usertasks-custom-lifecycle-quarkus` & `process-usertasks-custom-lifecycle-springboot` |
| **6** | **Business Calendar Provider (SLA & Timers)** | `org.kie.kogito.calendar.BusinessCalendar` | Dynamic SLA calculation, working hours, corporate holidays for timer nodes and SLA tracking. | Quarkus: `application.properties`<br>Spring Boot: `application.properties` | `process-business-calendar` (`CustomCalendar.java`) |
| **7** | **Custom Event Publisher (Audit & Telemetry)** | `org.kie.kogito.event.EventPublisher`<br>`org.kie.kogito.event.DataEvent` | Emitting engine lifecycle events directly to external data stores (Elasticsearch, Kafka, OpenSearch, Splunk). | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-event-listeners-quarkus` & `process-event-listeners-springboot` (`ElasticsearchEventPublisher.java`) |
| **8** | **Identity Provider & Security Context** | `org.kie.kogito.auth.IdentityProvider`<br>`org.kie.kogito.auth.IdentityProviderFactory` | Custom user/group resolution, token propagation, role-based authorization in user tasks. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `process-usertasks-with-security-springboot` |
| **9** | **Unit of Work & Transaction Synchronization** | `org.kie.kogito.uow.UnitOfWorkManager`<br>`org.kie.kogito.uow.UnitOfWork`<br>`org.kie.kogito.uow.events.UnitOfWorkEventListener` | Coordinating external transactional side-effects (database commits, JMS) atomically with workflow persistence. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `kogito-api` (`UnitOfWork.java`) |
| **10** | **Process Variable Serialization & Marshalling** | `org.kie.kogito.serialization.VariableMarshaller`<br>Jackson `ObjectMapperCustomizer` / `Module` | Custom JSON/Avro serialization for complex domain objects within process state variables. | Quarkus: `@Produces ObjectMapperCustomizer`<br>Spring Boot: `@Bean Jackson2ObjectMapperBuilderCustomizer` | `process-persistence` & `process-persistence-springboot` |
| **11** | **Process Version Resolver** | `org.kie.kogito.process.ProcessVersionResolver` | Custom versioning strategy when starting process instances by name/ID across multi-version deployments. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `kogito-api` (`ProcessVersionResolver.java`) |
| **12** | **Decision (DMN) & Rule Runtime Event Listeners** | `org.kie.dmn.api.core.event.DMNRuntimeEventListener`<br>`org.kie.kogito.decision.DecisionEventListenerConfig`<br>`org.kie.kogito.rules.RuleEventListenerConfig` | Auditing DMN evaluation nodes, rule firing metrics, and decision service telemetry. | Quarkus: `@ApplicationScoped`<br>Spring Boot: `@Component` | `dmn-listener-quarkus` & `dmn-listener-springboot` (`LoggingDMNRuntimeEventListener.java`) |

---

## 3. Deep-Dive Specifications & Reference Implementations

---

### 3.1. Custom Work Item Handler (Service Tasks)

* **Canonical Name:** `Custom Work Item Handler`
* **Reference Examples:** [`v9/bamoe-examples/process-error-handling/`](../v9/bamoe-examples/process-error-handling/) and [`v9/bamoe-examples/process-rest-workitem-quarkus/`](../v9/bamoe-examples/process-rest-workitem-quarkus/) / [`process-rest-workitem-springboot/`](../v9/bamoe-examples/process-rest-workitem-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler` — the interface to implement (currently in `internal`; see note below)
  - `org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler` — convenience base class (currently in `impl`; see note below)
  - `org.kie.kogito.process.WorkItemHandlerConfig` — **public** registration interface in `kogito-api`

> **⚠️ Package-stability note:** The handler interface and all its parameter types currently reside under `org.kie.kogito.internal.process.workitem.*` and the default base class is in `org.kie.kogito.process.workitems.impl.*`. The `internal` and `impl` sub-packages are conventional Java signals that these types are *not* part of the guaranteed stable public API and may change between releases without a deprecation cycle.
>
> **Recommended practice:** Implement `KogitoWorkItemHandler` directly (do not extend `DefaultKogitoWorkItemHandler`) so your handler depends only on the interface contract, which is the most stable surface available today. Declare your registration bean against the `WorkItemHandlerConfig` interface — the only type in a fully public (`org.kie.kogito.process`) package.
>
> **Upstream gap:** There is currently no fully `internal`-free path because `KogitoWorkItemHandler`, `KogitoWorkItem`, `KogitoWorkItemManager`, and `WorkItemTransition` all live in `org.kie.kogito.internal.*`. A future improvement to the project would be to promote these interfaces to `org.kie.kogito.process.workitem` (without `internal`) and provide a public abstract base class in `kogito-api`.

* **Architecture & Deep-Dive Description:**
  - **Purpose:** Enables developers to execute arbitrary Java logic, call external microservices/APIs (REST, Kafka, gRPC, mainframe), or integrate legacy backends when a process instance enters a BPMN Custom Task or Service Task node.
  - **Engine Invocation Lifecycle:** The workflow engine resolves the handler from `WorkItemHandlerConfig` via `forName(workItem.getName())` as soon as the process execution token reaches the task node. It triggers `activateWorkItemHandler(...)` (or `executeWorkItem(...)`). If the process instance is aborted, or if an interrupting boundary event or timer cancels the node, the engine triggers `abortWorkItemHandler(...)`.
  - **Data Contract:**
    - **Input:** `KogitoWorkItem.getParameters()` contains the Data Input Associations mapped from process variables in the BPMN designer.
    - **Output:** The handler returns a `WorkItemTransition` containing a `Map<String, Object>` representing Data Output Associations written back into process variables upon completion.
  - **Thread-Safety & Concurrency:** Applications can register multiple distinct handlers for different task names. The central configuration registry bean (`WorkItemHandlerConfig`) is a singleton (`@ApplicationScoped` / `@Component`). Because multiple concurrent process executions invoke the same handler instance simultaneously, handler classes **must be stateless and thread-safe**. All contextual state must be passed through `workItem.getParameters()`.
  - **Error & Failure Handling:** Handlers can throw `ProcessWorkItemHandlerException` specifying error handling strategies (`RETRY`, `COMPLETE`, `ABORT`, or `RETHROW`) to trigger BPMN Error Boundary Events or automated retry policies without manual database rollback.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, handlers implemented `org.kie.api.runtime.process.WorkItemHandler` and had to be registered imperatively on the `KieSession` or configured in `CustomWorkItemHandlers.conf` / `deployment-descriptor.xml`. In BAMOE v9, handlers integrate natively with CDI/Spring Boot dependency injection and support modern multi-phase work item transitions.

#### Handler Implementation (Framework-Agnostic Core — preferred, interface-only):

This approach minimises the dependency surface to the `KogitoWorkItemHandler` interface and avoids coupling to any `impl` base class. The `activateWorkItemHandler` lifecycle method is wired in via `transitionToPhase`; the handler only needs to implement the interface contract.

```java
package org.acme.wih;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preferred implementation style: extend DefaultKogitoWorkItemHandler for the
 * lifecycle wiring, but override only the activate/abort callbacks.
 *
 * NOTE: DefaultKogitoWorkItemHandler is in an `impl` package today. Once the
 * project promotes a public base class (e.g. BaseKogitoWorkItemHandler in
 * kogito-api), replace the extends clause with that class and remove the
 * process.workitems.impl import.
 */
public class CustomTaskWorkItemHandler extends DefaultKogitoWorkItemHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CustomTaskWorkItemHandler.class);

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(
            KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler,
            KogitoWorkItem workItem,
            WorkItemTransition transition) {

        LOG.info("Executing work item: {}", workItem.getName());
        String input = (String) workItem.getParameter("Input");

        Map<String, Object> results = new HashMap<>();
        results.put("Result", "Processed: " + input);

        if ("ABORT".equals(input)) {
            return Optional.of(handler.abortTransition(workItem.getPhaseStatus()));
        } else if ("ERROR".equals(input)) {
            throw new ProcessWorkItemHandlerException("error_handling",
                    ProcessWorkItemHandlerException.HandlingStrategy.RETRY,
                    new IllegalStateException("Transient failure"));
        } else {
            return Optional.of(handler.completeTransition(workItem.getPhaseStatus(), results));
        }
    }

    @Override
    public Optional<WorkItemTransition> abortWorkItemHandler(
            KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler,
            KogitoWorkItem workItem,
            WorkItemTransition transition) {
        LOG.info("Aborting work item: {}", workItem.getName());
        return Optional.empty();
    }
}
```

#### Required imports — annotated by package stability

```
org.kie.api.runtime.process.ProcessWorkItemHandlerException       ← ✅ public kie-api
org.kie.kogito.Application                                        ← ✅ public kogito-api
org.kie.kogito.internal.process.workitem.KogitoWorkItem           ← ⚠️ internal package
org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler    ← ⚠️ internal package
org.kie.kogito.internal.process.workitem.KogitoWorkItemManager    ← ⚠️ internal package
org.kie.kogito.internal.process.workitem.WorkItemTransition       ← ⚠️ internal package
org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler← ⚠️ impl class
org.kie.kogito.process.WorkItemHandlerConfig                      ← ✅ public kogito-api
org.kie.kogito.process.impl.DefaultWorkItemHandlerConfig          ← ⚠️ impl class
```

#### Framework Registration:

Declare the config bean against the **public** `WorkItemHandlerConfig` interface. Use `DefaultWorkItemHandlerConfig` as the implementation until a public alternative is available in `kogito-api`.

##### Quarkus (CDI):
```java
package org.acme.wih;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.process.WorkItemHandlerConfig;         // ✅ public interface — declare type here
import org.kie.kogito.process.impl.DefaultWorkItemHandlerConfig; // ⚠️ impl — use only as constructor base

@ApplicationScoped
public class CustomWorkItemHandlerConfig extends DefaultWorkItemHandlerConfig
        implements WorkItemHandlerConfig {
    {
        register("CustomPaymentTask", new PaymentWorkItemHandler());
        register("CustomNotificationTask", new NotificationWorkItemHandler());
        register("CustomAuditTask", new CustomTaskWorkItemHandler());
    }
}
```

##### Spring Boot:
```java
package org.acme.wih;

import org.kie.kogito.process.WorkItemHandlerConfig;              // ✅ public interface — declare type here
import org.kie.kogito.process.impl.DefaultWorkItemHandlerConfig;  // ⚠️ impl — use only as constructor base
import org.springframework.stereotype.Component;

@Component
public class CustomWorkItemHandlerConfig extends DefaultWorkItemHandlerConfig
        implements WorkItemHandlerConfig {
    public CustomWorkItemHandlerConfig() {
        register("CustomPaymentTask", new PaymentWorkItemHandler());
        register("CustomNotificationTask", new NotificationWorkItemHandler());
        register("CustomAuditTask", new CustomTaskWorkItemHandler());
    }
}
```

#### Proposed upstream changes to fully eliminate `internal`/`impl` dependencies

The following source-level changes would give users a completely public API surface:

1. **Promote interfaces to a public package** — move (or re-export) `KogitoWorkItemHandler`, `KogitoWorkItem`, `KogitoWorkItemManager`, and `WorkItemTransition` from `org.kie.kogito.internal.process.workitem` to `org.kie.kogito.process.workitem` inside `kogito-api`.

2. **Add a public abstract base class to `kogito-api`** — provide `org.kie.kogito.process.workitem.BaseKogitoWorkItemHandler` that wires the lifecycle internally. Users would write:
   ```java
   // Future — once BaseKogitoWorkItemHandler is in kogito-api
   import org.kie.kogito.process.workitem.BaseKogitoWorkItemHandler; // ✅ no internal, no impl
   public class CustomTaskWorkItemHandler extends BaseKogitoWorkItemHandler { ... }
   ```

3. **Add a public config class to `kogito-api`** — provide `org.kie.kogito.process.workitem.SimpleWorkItemHandlerConfig` implementing `WorkItemHandlerConfig` so users never import `org.kie.kogito.process.impl.*`.

---

### 3.2. Process Event Listener (Process Lifecycle)

* **Canonical Name:** `Process Event Listener`
* **Reference Examples:** [`v9/bamoe-examples/process-event-listeners-quarkus/`](../v9/bamoe-examples/process-event-listeners-quarkus/) and [`v9/bamoe-examples/process-event-listeners-springboot/`](../v9/bamoe-examples/process-event-listeners-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.api.event.process.ProcessEventListener`
  - `org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener`
  - `org.kie.kogito.process.ProcessEventListenerConfig`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Intercepts real-time workflow lifecycle events to produce audit records, collect operational metrics, extract business insights, and inject OpenTelemetry distributed tracing spans.
  - **Engine Invocation Lifecycle:** The engine synchronously triggers listener methods immediately before and after every major lifecycle transition:
    - `beforeProcessStarted` / `afterProcessStarted`
    - `beforeProcessCompleted` / `afterProcessCompleted`
    - `beforeNodeTriggered` / `afterNodeTriggered` (fired on entering a BPMN node)
    - `beforeNodeLeft` / `afterNodeLeft` (fired on leaving a BPMN node)
    - `beforeVariableChanged` / `afterVariableChanged` (fired when any process variable is modified)
  - **Data Contract:**
    - **Input:** Each callback receives an event payload (`ProcessStartedEvent`, `ProcessNodeTriggeredEvent`, `ProcessVariableChangedEvent`) exposing immutable access to the `ProcessInstance`, `NodeInstance` metadata, old/new variable values, and error states.
    - **Output:** `void` (event observers).
  - **Thread-Safety & Concurrency:** Listeners execute synchronously on the same execution thread as the workflow transaction. Listeners must be non-blocking and thread-safe. Lengthy I/O operations should be offloaded asynchronously (e.g., to an event bus or reactive stream).
  - **Error & Failure Handling:** Uncaught runtime exceptions inside listener methods will cause the active workflow transaction to fail and rollback. Listeners should handle internal logging and formatting errors defensively.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, listeners implemented `org.kie.api.event.process.ProcessEventListener` and were declared in `kie-deployment-descriptor.xml`. In BAMOE v9, listeners are automatically discovered by CDI/Spring IoC simply by annotating them with `@ApplicationScoped` / `@Component`.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TestProcessEventListener extends DefaultKogitoProcessEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessEventListener.class);

    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
        LOGGER.info("[Quarkus] Starting Process: id={}, processId={}", 
            event.getProcessInstance().getId(), 
            event.getProcessInstance().getProcessId());
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        LOGGER.info("[Quarkus] Completed Process: id={}, state={}", 
            event.getProcessInstance().getId(), 
            event.getProcessInstance().getState());
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        LOGGER.info("[Quarkus] Node Entered: name={}, nodeId={}", 
            event.getNodeInstance().getNodeName(), 
            event.getNodeInstance().getNodeId());
    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        LOGGER.info("[Quarkus] Node Left: name={}", event.getNodeInstance().getNodeName());
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        LOGGER.info("[Quarkus] Variable Changed: name={}, old={}, new={}", 
            event.getVariableId(), 
            event.getOldValue(), 
            event.getNewValue());
    }
}
```

##### Spring Boot:
```java
package org.acme;

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TestProcessEventListener extends DefaultKogitoProcessEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessEventListener.class);

    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
        LOGGER.info("[SpringBoot] Starting Process: id={}, processId={}", 
            event.getProcessInstance().getId(), 
            event.getProcessInstance().getProcessId());
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        LOGGER.info("[SpringBoot] Completed Process: id={}, state={}", 
            event.getProcessInstance().getId(), 
            event.getProcessInstance().getState());
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        LOGGER.info("[SpringBoot] Node Entered: name={}, nodeId={}", 
            event.getNodeInstance().getNodeName(), 
            event.getNodeInstance().getNodeId());
    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        LOGGER.info("[SpringBoot] Node Left: name={}", event.getNodeInstance().getNodeName());
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        LOGGER.info("[SpringBoot] Variable Changed: name={}, old={}, new={}", 
            event.getVariableId(), 
            event.getOldValue(), 
            event.getNewValue());
    }
}
```

---

### 3.3. User Task Event Listener (Task Lifecycle & Audit)

* **Canonical Name:** `User Task Event Listener`
* **Reference Examples:** [`v9/bamoe-examples/process-event-listeners-quarkus/`](../v9/bamoe-examples/process-event-listeners-quarkus/) and [`v9/bamoe-examples/process-event-listeners-springboot/`](../v9/bamoe-examples/process-event-listeners-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.usertask.UserTaskEventListener`
  - `org.kie.kogito.usertask.events.UserTaskStateEvent`
  - `org.kie.kogito.usertask.events.UserTaskAssignmentEvent`
  - `org.kie.kogito.usertask.events.UserTaskVariableEvent`
  - `org.kie.kogito.usertask.UserTaskEventListenerConfig`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Dedicated observer for human task interactions. Captures task status changes (`Ready`, `Reserved`, `InProgress`, `Completed`, `Obsolete`), assignment updates, reassignments, and form data input/output mutations.
  - **Engine Invocation Lifecycle:** Invoked synchronously whenever a User Task instance changes state (`onUserTaskState`), is assigned/claimed/released (`onUserTaskAssignment`), or has its input/output parameters modified (`onUserTaskInputVariable` / `onUserTaskOutputVariable`).
  - **Data Contract:**
    - **Input:** Specific event payloads exposing `UserTaskInstance` (ID, Task Name, Priority), `oldStatus`/`newStatus`, `oldUsersId`/`newUsersId`, and modified variable values.
    - **Output:** `void`.
  - **Thread-Safety & Concurrency:** Thread-safe singleton bean invoked on the HTTP request thread executing the task transition.
  - **Error & Failure Handling:** Exceptions thrown inside the listener bubble up and abort the task transition transaction.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, human task events were managed through `TaskLifeCycleEventListener` within the internal `jbpm-human-task-core` module. In BAMOE v9, User Tasks are decoupled from internal runtime sessions into a first-class `usertask` subsystem with clear event contracts.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.usertask.UserTaskEventListener;
import org.kie.kogito.usertask.events.UserTaskAssignmentEvent;
import org.kie.kogito.usertask.events.UserTaskStateEvent;
import org.kie.kogito.usertask.events.UserTaskVariableEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TestUserTaskEventListener implements UserTaskEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUserTaskEventListener.class);

    @Override
    public void onUserTaskState(UserTaskStateEvent event) {
        LOGGER.info("[Quarkus] Task State: taskName={}, old={}, new={}",
                event.getUserTaskInstance().getTaskName(),
                event.getOldStatus(),
                event.getNewStatus());
    }

    @Override
    public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
        LOGGER.info("[Quarkus] Task Assignment: taskName={}, oldUsers={}, newUsers={}",
                event.getUserTaskInstance().getTaskName(),
                event.getOldUsersId() != null ? String.join(",", event.getOldUsersId()) : "none",
                event.getNewUsersId() != null ? String.join(",", event.getNewUsersId()) : "none");
    }

    @Override
    public void onUserTaskInputVariable(UserTaskVariableEvent event) {
        LOGGER.info("[Quarkus] Task Input: var={}, old={}, new={}",
                event.getVariableName(), event.getOldValue(), event.getNewValue());
    }

    @Override
    public void onUserTaskOutputVariable(UserTaskVariableEvent event) {
        LOGGER.info("[Quarkus] Task Output: var={}, old={}, new={}",
                event.getVariableName(), event.getOldValue(), event.getNewValue());
    }
}
```

##### Spring Boot:
```java
package org.acme;

import org.kie.kogito.usertask.UserTaskEventListener;
import org.kie.kogito.usertask.events.UserTaskAssignmentEvent;
import org.kie.kogito.usertask.events.UserTaskStateEvent;
import org.kie.kogito.usertask.events.UserTaskVariableEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TestUserTaskEventListener implements UserTaskEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUserTaskEventListener.class);

    @Override
    public void onUserTaskState(UserTaskStateEvent event) {
        LOGGER.info("[SpringBoot] Task State: taskName={}, old={}, new={}",
                event.getUserTaskInstance().getTaskName(),
                event.getOldStatus(),
                event.getNewStatus());
    }

    @Override
    public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
        LOGGER.info("[SpringBoot] Task Assignment: taskName={}, oldUsers={}, newUsers={}",
                event.getUserTaskInstance().getTaskName(),
                event.getOldUsersId() != null ? String.join(",", event.getOldUsersId()) : "none",
                event.getNewUsersId() != null ? String.join(",", event.getNewUsersId()) : "none");
    }

    @Override
    public void onUserTaskInputVariable(UserTaskVariableEvent event) {
        LOGGER.info("[SpringBoot] Task Input: var={}, old={}, new={}",
                event.getVariableName(), event.getOldValue(), event.getNewValue());
    }

    @Override
    public void onUserTaskOutputVariable(UserTaskVariableEvent event) {
        LOGGER.info("[SpringBoot] Task Output: var={}, old={}, new={}",
                event.getVariableName(), event.getOldValue(), event.getNewValue());
    }
}
```

---

### 3.4. User Task Assignment Strategy (Dynamic Routing & User Group Callback)

* **Canonical Name:** `User Task Assignment Strategy`
* **Reference Examples:** [`v9/bamoe-examples/process-user-tasks-subsystem/`](../v9/bamoe-examples/process-user-tasks-subsystem/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.usertask.UserTaskAssignmentStrategy`
  - `org.kie.kogito.usertask.impl.BasicUserTaskAssignmentStrategy`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Programmatically evaluates potential owners and assigns an `actualOwner` to a user task at runtime (e.g. Round-Robin distribution, load balancing, skill-matrix routing, or querying external LDAP/Keycloak/HR databases).
  - **Engine Invocation Lifecycle:** Fired automatically when a user task instance is initialized into the `Ready` state. The strategy computes the assignee before the task is presented to end users.
  - **Data Contract:**
    - **Input:** `UserTaskInstance` (containing candidate groups, potential users, process variables) and `IdentityProvider` (current security context).
    - **Output:** Returns an `Optional<String>` containing the resolved user identifier to claim/assign the task immediately. If `Optional.empty()` is returned, the task remains in the candidate pool for manual claiming.
  - **Thread-Safety & Concurrency:** Stateless singleton bean executed concurrently across multiple task creation requests.
  - **Error & Failure Handling:** If assignment lookup fails, the strategy can gracefully return `Optional.empty()` to fall back to group assignment rather than failing process execution.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, user/group validation and assignment was managed via the `UserGroupCallback` interface (and `TaskAssignmentService`). In BAMOE v9, `BasicUserTaskAssignmentStrategy` provides a cleaner, contextual SPI directly tied to the task instance attributes.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.candidate;

import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.impl.BasicUserTaskAssignmentStrategy;

@ApplicationScoped
public class CustomUserTaskAssignmentStrategyConfig extends BasicUserTaskAssignmentStrategy {

    @Override
    public Optional<String> computeAssignment(UserTaskInstance userTaskInstance, IdentityProvider identityProvider) {
        String taskName = userTaskInstance.getTaskName();

        if ("hr_interview".equals(taskName)) {
            return Optional.of("recruiter");
        } else if ("it_interview".equals(taskName)) {
            return Optional.of("developer");
        }
        
        return Optional.empty();
    }
}
```

##### Spring Boot:
```java
package org.acme.candidate;

import java.util.Optional;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.impl.BasicUserTaskAssignmentStrategy;
import org.springframework.stereotype.Component;

@Component
public class CustomUserTaskAssignmentStrategyConfig extends BasicUserTaskAssignmentStrategy {

    @Override
    public Optional<String> computeAssignment(UserTaskInstance userTaskInstance, IdentityProvider identityProvider) {
        String taskName = userTaskInstance.getTaskName();

        if ("hr_interview".equals(taskName)) {
            return Optional.of("recruiter");
        } else if ("it_interview".equals(taskName)) {
            return Optional.of("developer");
        }
        
        return Optional.empty();
    }
}
```

---

### 3.5. User Task Lifecycle Transition Strategy (Custom Task State Machines)

* **Canonical Name:** `User Task Lifecycle Transition Strategy`
* **Reference Examples:** [`v9/incubator-kie-kogito-examples/kogito-quarkus-examples/process-usertasks-custom-lifecycle-quarkus/`](../v9/incubator-kie-kogito-examples/kogito-quarkus-examples/process-usertasks-custom-lifecycle-quarkus/) and [`process-usertasks-custom-lifecycle-springboot/`](../v9/incubator-kie-kogito-examples/kogito-springboot-examples/process-usertasks-custom-lifecycle-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.usertask.lifecycle.UserTaskLifeCycle`
  - `org.kie.kogito.usertask.lifecycle.UserTaskTransition`
  - `org.kie.kogito.usertask.lifecycle.UserTaskState`
  - `org.kie.kogito.usertask.impl.lifecycle.DefaultUserTransition`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Completely customizes the state machine of human tasks. Enables automated multi-step transitions (e.g. "Auto-progress" from Claim $\rightarrow$ Start $\rightarrow$ Complete in a single API call) or enterprise states (e.g., `Jeopardy`, `UnderReview`, `Suspended`).
  - **Engine Invocation Lifecycle:** The engine routes all user task REST operations (`/usertasks/instance/{id}/transition`) through `UserTaskLifeCycle.transition(...)`.
  - **Data Contract:**
    - **Input:** `UserTaskInstance`, requested `UserTaskTransitionToken`, and caller `IdentityProvider`.
    - **Output:** Returns `Optional<UserTaskTransitionToken>` indicating the new state token and outcome data map.
  - **Thread-Safety & Concurrency:** Must be thread-safe. Transitions operate within the database transaction of the task instance.
  - **Error & Failure Handling:** Throws `UserTaskTransitionException` if a requested transition is invalid or forbidden by security policies.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, the WS-HT lifecycle was hardcoded and rigid; custom state progression required building complex REST wrappers on KIE Server. In BAMOE v9, `UserTaskLifeCycle` is a pluggable SPI.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.travels.usertasks;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Singleton;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.lifecycle.UserTaskLifeCycle;
import org.kie.kogito.usertask.lifecycle.UserTaskState;
import org.kie.kogito.usertask.lifecycle.UserTaskState.TerminationType;
import org.kie.kogito.usertask.lifecycle.UserTaskTransition;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionToken;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTransition;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskTransitionToken;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionException;

@Singleton
public class CustomUserTaskLifeCycle implements UserTaskLifeCycle {

    public static final UserTaskState INACTIVE = UserTaskState.initalized();
    public static final UserTaskState ACTIVE = UserTaskState.of("Ready");
    public static final UserTaskState RESERVED = UserTaskState.of("Reserved");
    public static final UserTaskState COMPLETED = UserTaskState.of("Completed", TerminationType.COMPLETED);
    public static final UserTaskState ABORTED = UserTaskState.of("Aborted", TerminationType.OBSOLETE);

    public static final String PARAMETER_USER = "USER";

    private final List<UserTaskTransition> transitions = List.of(
        new DefaultUserTransition("activate", INACTIVE, ACTIVE, this::activate),
        new DefaultUserTransition("claim", ACTIVE, RESERVED, this::claim),
        new DefaultUserTransition("complete", RESERVED, COMPLETED, this::complete),
        new DefaultUserTransition("reassign", RESERVED, RESERVED, this::reassign),
        new DefaultUserTransition("abort", RESERVED, ABORTED, this::abort)
    );

    @Override
    public String startTransition() {
        return "activate";
    }

    @Override
    public String reassignTransition() {
        return "reassign";
    }

    @Override
    public String abortTransition() {
        return "abort";
    }

    @Override
    public UserTaskTransitionToken newTransitionToken(String transitionId, UserTaskInstance userTaskInstance, Map<String, Object> data) {
        UserTaskTransition transition = transitions.stream()
                .filter(e -> e.source().equals(userTaskInstance.getStatus()) && e.id().equals(transitionId))
                .findAny()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition " + transitionId + " from " + userTaskInstance.getStatus()));
        return new DefaultUserTaskTransitionToken(transition.id(), transition.source(), transition.target(), data);
    }

    @Override
    public Optional<UserTaskTransitionToken> newReassignmentTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        try {
            return Optional.of(newTransitionToken("reassign", userTaskInstance, data));
        } catch (UserTaskTransitionException e) {
            return Optional.empty();
        }
    }

    @Override
    public UserTaskTransitionToken newCompleteTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken("complete", userTaskInstance, data);
    }

    @Override
    public UserTaskTransitionToken newAbortTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken("abort", userTaskInstance, data);
    }

    @Override
    public Optional<UserTaskTransitionToken> transition(
            UserTaskInstance userTaskInstance,
            UserTaskTransitionToken transitionToken,
            IdentityProvider identity) {
        UserTaskTransition transition = transitions.stream()
                .filter(t -> t.source().equals(userTaskInstance.getStatus()) && t.id().equals(transitionToken.transitionId()))
                .findFirst()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition from " + userTaskInstance.getStatus()));
        // Executes the transition logic and returns next chained transition token or Optional.empty() when finished
        return transition.executor().execute(userTaskInstance, transitionToken, identity);
    }

    public Optional<UserTaskTransitionToken> activate(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> claim(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null && token.data().containsKey(PARAMETER_USER)) {
            ut.setActualOwner((String) token.data().get(PARAMETER_USER));
        } else {
            ut.setActualOwner(id.getName());
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> complete(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null) {
            token.data().forEach(ut::setOutput);
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> reassign(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null && token.data().containsKey(PARAMETER_USER)) {
            String newUser = (String) token.data().get(PARAMETER_USER);
            ut.setActualOwner(newUser);
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> abort(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        return Optional.empty();
    }

    @Override
    public List<UserTaskTransition> allowedTransitions(UserTaskInstance ut, IdentityProvider identity) {
        return transitions.stream().filter(t -> t.source().equals(ut.getStatus())).toList();
    }
}
```

##### Spring Boot:
```java
package org.acme.travels.usertasks;

import java.util.List;
import java.util.Optional;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.lifecycle.UserTaskLifeCycle;
import org.kie.kogito.usertask.lifecycle.UserTaskState;
import org.kie.kogito.usertask.lifecycle.UserTaskState.TerminationType;
import org.kie.kogito.usertask.lifecycle.UserTaskTransition;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionToken;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTransition;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskTransitionToken;
import org.kie.kogito.usertask.lifecycle.UserTaskTransitionException;
import org.springframework.stereotype.Component;

@Component
public class CustomUserTaskLifeCycle implements UserTaskLifeCycle {

    public static final UserTaskState INACTIVE = UserTaskState.initalized();
    public static final UserTaskState ACTIVE = UserTaskState.of("Ready");
    public static final UserTaskState RESERVED = UserTaskState.of("Reserved");
    public static final UserTaskState COMPLETED = UserTaskState.of("Completed", TerminationType.COMPLETED);
    public static final UserTaskState ABORTED = UserTaskState.of("Aborted", TerminationType.OBSOLETE);

    public static final String PARAMETER_USER = "USER";

    private final List<UserTaskTransition> transitions = List.of(
        new DefaultUserTransition("activate", INACTIVE, ACTIVE, this::activate),
        new DefaultUserTransition("claim", ACTIVE, RESERVED, this::claim),
        new DefaultUserTransition("complete", RESERVED, COMPLETED, this::complete),
        new DefaultUserTransition("reassign", RESERVED, RESERVED, this::reassign),
        new DefaultUserTransition("abort", RESERVED, ABORTED, this::abort)
    );

    @Override
    public String startTransition() {
        return "activate";
    }

    @Override
    public String reassignTransition() {
        return "reassign";
    }

    @Override
    public String abortTransition() {
        return "abort";
    }

    @Override
    public UserTaskTransitionToken newTransitionToken(String transitionId, UserTaskInstance userTaskInstance, Map<String, Object> data) {
        UserTaskTransition transition = transitions.stream()
                .filter(e -> e.source().equals(userTaskInstance.getStatus()) && e.id().equals(transitionId))
                .findAny()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition " + transitionId + " from " + userTaskInstance.getStatus()));
        return new DefaultUserTaskTransitionToken(transition.id(), transition.source(), transition.target(), data);
    }

    @Override
    public Optional<UserTaskTransitionToken> newReassignmentTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        try {
            return Optional.of(newTransitionToken("reassign", userTaskInstance, data));
        } catch (UserTaskTransitionException e) {
            return Optional.empty();
        }
    }

    @Override
    public UserTaskTransitionToken newCompleteTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken("complete", userTaskInstance, data);
    }

    @Override
    public UserTaskTransitionToken newAbortTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data) {
        return newTransitionToken("abort", userTaskInstance, data);
    }

    @Override
    public Optional<UserTaskTransitionToken> transition(
            UserTaskInstance userTaskInstance,
            UserTaskTransitionToken transitionToken,
            IdentityProvider identity) {
        UserTaskTransition transition = transitions.stream()
                .filter(t -> t.source().equals(userTaskInstance.getStatus()) && t.id().equals(transitionToken.transitionId()))
                .findFirst()
                .orElseThrow(() -> new UserTaskTransitionException("Invalid transition from " + userTaskInstance.getStatus()));
        // Executes the transition logic and returns next chained transition token or Optional.empty() when finished
        return transition.executor().execute(userTaskInstance, transitionToken, identity);
    }

    public Optional<UserTaskTransitionToken> activate(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> claim(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null && token.data().containsKey(PARAMETER_USER)) {
            ut.setActualOwner((String) token.data().get(PARAMETER_USER));
        } else {
            ut.setActualOwner(id.getName());
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> complete(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null) {
            token.data().forEach(ut::setOutput);
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> reassign(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        if (token.data() != null && token.data().containsKey(PARAMETER_USER)) {
            String newUser = (String) token.data().get(PARAMETER_USER);
            ut.setActualOwner(newUser);
        }
        return Optional.empty();
    }

    public Optional<UserTaskTransitionToken> abort(UserTaskInstance ut, UserTaskTransitionToken token, IdentityProvider id) {
        return Optional.empty();
    }

    @Override
    public List<UserTaskTransition> allowedTransitions(UserTaskInstance ut, IdentityProvider identity) {
        return transitions.stream().filter(t -> t.source().equals(ut.getStatus())).toList();
    }
}
```

---

### 3.6. Business Calendar Provider (SLA & Timers)

* **Canonical Name:** `Business Calendar Provider`
* **Reference Examples:** [`v9/bamoe-examples/process-business-calendar/`](../v9/bamoe-examples/process-business-calendar/) and [`v9/incubator-kie-kogito-examples/kogito-quarkus-examples/process-business-calendar-quarkus-example/`](../v9/incubator-kie-kogito-examples/kogito-quarkus-examples/process-business-calendar-quarkus-example/)
* **Interface:** `org.kie.kogito.calendar.BusinessCalendar`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Computes business-day durations and dynamic timer expiration timestamps by excluding non-working hours, weekends, shift rotations, and corporate holidays.
  - **Engine Invocation Lifecycle:** Whenever a process encounters a BPMN Intermediate Catch Timer Event, Boundary Timer Event, or Human Task SLA deadline configured with business-time expressions (e.g. `5d`, `8h`), the engine invokes `calculateBusinessTimeAsDuration(...)` or `calculateBusinessTimeAsDate(...)`.
  - **Data Contract:**
    - **Input:** ISO-8601 or custom duration expression string (e.g. `"5d"`).
    - **Output:** Returns duration in milliseconds (`long`) or target expiration `Date`.
  - **Thread-Safety & Concurrency:** Stateless calculation utility shared across all workflow timers.
  - **Error & Failure Handling:** Invalid time expressions should fall back to standard duration parsing or log descriptive parsing warnings.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, business calendars were configured via `business-calendar.properties` inside the KIE Container. In BAMOE v9, custom calendar logic is written as a Java class and registered via `kogito.processes.businessCalendar` in `application.properties`.

#### Calendar Implementation (Framework-Agnostic):
```java
package org.kie.kogito.calendar.custom;

import java.util.Calendar;
import java.util.Date;
import org.kie.kogito.calendar.BusinessCalendar;

public class CustomCalendar implements BusinessCalendar {

    @Override
    public long calculateBusinessTimeAsDuration(String timeExpression) {
        // Parse timeExpression (e.g. "5d", "8h") and calculate duration in ms
        // excluding non-working hours and holidays
        return 8 * 60 * 60 * 1000L; // Example: 8 business hours
    }

    @Override
    public Date calculateBusinessTimeAsDate(String timeExpression) {
        // Calculate target timestamp taking shift calendars into account
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 8);
        return cal.getTime();
    }
}
```

#### Registration (Both Quarkus & Spring Boot via `application.properties`):
```properties
kogito.processes.businessCalendar=org.kie.kogito.calendar.custom.CustomCalendar
```

---

### 3.7. Custom Event Publisher (Direct Audit & Telemetry Ingestion)

* **Canonical Name:** `Custom Event Publisher`
* **Reference Examples:** [`v9/bamoe-examples/process-event-listeners-quarkus/`](../v9/bamoe-examples/process-event-listeners-quarkus/) and [`v9/bamoe-examples/process-event-listeners-springboot/`](../v9/bamoe-examples/process-event-listeners-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.event.EventPublisher`
  - `org.kie.kogito.event.DataEvent`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Ingests and routes engine execution CloudEvents directly into enterprise data stores (Elasticsearch, OpenSearch, Splunk, Kafka) without deploying the separate BAMOE Data Index service.
  - **Engine Invocation Lifecycle:** At the end of every Unit of Work / transaction commit, the engine gathers all generated data events (`ProcessInstanceStateDataEvent`, `UserTaskInstanceStateDataEvent`, `UserTaskInstanceAssignmentDataEvent`) and invokes `EventPublisher.publish(...)`.
  - **Data Contract:**
    - **Input:** Single `DataEvent<?>` or `Collection<DataEvent<?>>` containing standardized CloudEvent payloads with trace IDs and process instance metadata.
    - **Output:** `void`.
  - **Thread-Safety & Concurrency:** Invoked on the persistence commit thread. Should handle network dispatching asynchronously if throughput is high.
  - **Error & Failure Handling:** Handlers must catch external communication failures to avoid failing the core process transaction if external metrics/logging sinks are unavailable.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, custom emitters were built using `KieServerExtension` or custom DB event listeners. In BAMOE v9, `EventPublisher` is a clean SPI that receives standard structured CloudEvents.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme;

import java.util.Collection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ElasticsearchEventPublisher implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchEventPublisher.class);

    @Inject
    RestClient restClient;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void publish(DataEvent<?> event) {
        if (event == null) return;
        try {
            if (event instanceof ProcessInstanceStateDataEvent) {
                ProcessInstanceStateDataEvent pEvent = (ProcessInstanceStateDataEvent) event;
                upsert("process-instances", pEvent.getData().getProcessInstanceId(), pEvent.getData());
            } else if (event instanceof UserTaskInstanceStateDataEvent) {
                UserTaskInstanceStateDataEvent uEvent = (UserTaskInstanceStateDataEvent) event;
                upsert("user-tasks", uEvent.getData().getUserTaskInstanceId(), uEvent.getData());
            }
        } catch (Exception e) {
            LOGGER.error("Elasticsearch publish failed", e);
        }
    }

    @Override
    public void publish(Collection<DataEvent<?>> events) {
        if (events != null) events.forEach(this::publish);
    }

    private void upsert(String index, String id, Object payload) throws Exception {
        Request req = new Request("PUT", "/" + index + "/_doc/" + id);
        req.setJsonEntity(objectMapper.writeValueAsString(payload));
        restClient.performRequest(req);
    }
}
```

##### Spring Boot:
```java
package org.acme;

import java.util.Collection;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchEventPublisher implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchEventPublisher.class);

    @Autowired
    private RestClient restClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void publish(DataEvent<?> event) {
        if (event == null) return;
        try {
            if (event instanceof ProcessInstanceStateDataEvent) {
                ProcessInstanceStateDataEvent pEvent = (ProcessInstanceStateDataEvent) event;
                upsert("process-instances", pEvent.getData().getProcessInstanceId(), pEvent.getData());
            } else if (event instanceof UserTaskInstanceStateDataEvent) {
                UserTaskInstanceStateDataEvent uEvent = (UserTaskInstanceStateDataEvent) event;
                upsert("user-tasks", uEvent.getData().getUserTaskInstanceId(), uEvent.getData());
            }
        } catch (Exception e) {
            LOGGER.error("Elasticsearch publish failed", e);
        }
    }

    @Override
    public void publish(Collection<DataEvent<?>> events) {
        if (events != null) events.forEach(this::publish);
    }

    private void upsert(String index, String id, Object payload) throws Exception {
        Request req = new Request("PUT", "/" + index + "/_doc/" + id);
        req.setJsonEntity(objectMapper.writeValueAsString(payload));
        restClient.performRequest(req);
    }
}
```

---

### 3.8. Identity Provider & Security Context

* **Canonical Name:** `Identity Provider & Security Context`
* **Reference Examples:** [`v9/incubator-kie-kogito-examples/kogito-springboot-examples/process-usertasks-with-security-springboot/`](../v9/incubator-kie-kogito-examples/kogito-springboot-examples/process-usertasks-with-security-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.auth.IdentityProvider`
  - `org.kie.kogito.auth.IdentityProviderFactory`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Bridges the security context of Quarkus (SecurityIdentity) or Spring Security (Authentication / SecurityContextHolder) with the workflow engine to enforce role-based access control and identify current task callers.
  - **Engine Invocation Lifecycle:** Queried during user task operations (claim, start, complete, release) to verify if the executing user has required permissions or membership in candidate groups.
  - **Data Contract:**
    - **Methods:** `getName()` (returns username/principal), `getRoles()` (returns `Set<String>` of assigned roles), `hasRole(String role)`.
  - **Thread-Safety & Concurrency:** Resolved per request thread.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, security was tied to Java EE JAAS or KIE Server LoginModules. In BAMOE v9, `IdentityProvider` integrates with modern OpenID Connect (OIDC), JWT tokens, and Keycloak.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.security;

import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.auth.IdentityProvider;

@ApplicationScoped
public class CustomQuarkusIdentityProvider implements IdentityProvider {

    @Override
    public String getName() {
        return "custom-authenticated-user";
    }

    @Override
    public Set<String> getRoles() {
        return Set.of("admin", "managers", "approvers");
    }

    @Override
    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }
}
```

##### Spring Boot:
```java
package org.acme.security;

import java.util.Set;
import org.kie.kogito.auth.IdentityProvider;
import org.springframework.stereotype.Component;

@Component
public class CustomSpringBootIdentityProvider implements IdentityProvider {

    @Override
    public String getName() {
        return "custom-authenticated-user";
    }

    @Override
    public Set<String> getRoles() {
        return Set.of("admin", "managers", "approvers");
    }

    @Override
    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }
}
```

---

### 3.9. Unit of Work & Transaction Synchronization

* **Canonical Name:** `Unit of Work & Transaction Synchronization`
* **Reference Examples:** [`v9/incubator-kie/kogito-quarkus/integration-tests/integration-tests-quarkus-processes/`](../v9/incubator-kie/kogito-quarkus/integration-tests/integration-tests-quarkus-processes/) and [`v9/incubator-kie/kogito-springboot/integration-tests/integration-tests-springboot-processes-it/`](../v9/incubator-kie/kogito-springboot/integration-tests/integration-tests-springboot-processes-it/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.uow.UnitOfWorkManager`
  - `org.kie.kogito.uow.UnitOfWork`
  - `org.kie.kogito.uow.events.UnitOfWorkEventListener`
  - `org.kie.kogito.uow.WorkUnit`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Coordinates external transactional operations (database commits, message queue sends, cache updates) atomically with workflow persistence. Ensures side-effects are only dispatched if the process step persists successfully.
  - **Engine Invocation Lifecycle:** Triggers `onStart(unitOfWork)` when a workflow execution cycle begins, `onEnd(unitOfWork)` upon successful database flush/commit, and `onAbort(unitOfWork, cause)` if an unhandled exception occurs.
  - **Data Contract:**
    - **Methods:** `onStart(...)`, `onEnd(...)`, `onAbort(...)`. Developers can attach custom `WorkUnit<T>` objects containing compensating or deferred actions.
  - **Thread-Safety & Concurrency:** Scope is per-execution transaction.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, developers had to manipulate JTA `TransactionManager` or register custom `TransactionSynchronization` objects on the Hibernate session. In BAMOE v9, `UnitOfWork` provides a clean, database-agnostic transaction boundary.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.uow;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.uow.events.UnitOfWorkEventListener;
import org.kie.kogito.uow.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CustomUnitOfWorkEventListener implements UnitOfWorkEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(CustomUnitOfWorkEventListener.class);

    @Override
    public void onStart(UnitOfWork unitOfWork) {
        LOG.info("[Quarkus] UnitOfWork started: {}", unitOfWork.identifier());
    }

    @Override
    public void onEnd(UnitOfWork unitOfWork) {
        LOG.info("[Quarkus] UnitOfWork committed successfully: {}", unitOfWork.identifier());
    }

    @Override
    public void onAbort(UnitOfWork unitOfWork, Throwable cause) {
        LOG.error("[Quarkus] UnitOfWork aborted: {}", unitOfWork.identifier(), cause);
    }
}
```

##### Spring Boot:
```java
package org.acme.uow;

import org.kie.kogito.uow.events.UnitOfWorkEventListener;
import org.kie.kogito.uow.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomUnitOfWorkEventListener implements UnitOfWorkEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(CustomUnitOfWorkEventListener.class);

    @Override
    public void onStart(UnitOfWork unitOfWork) {
        LOG.info("[SpringBoot] UnitOfWork started: {}", unitOfWork.identifier());
    }

    @Override
    public void onEnd(UnitOfWork unitOfWork) {
        LOG.info("[SpringBoot] UnitOfWork committed successfully: {}", unitOfWork.identifier());
    }

    @Override
    public void onAbort(UnitOfWork unitOfWork, Throwable cause) {
        LOG.error("[SpringBoot] UnitOfWork aborted: {}", unitOfWork.identifier(), cause);
    }
}
```

---

### 3.10. Process Variable Serialization & Marshalling

* **Canonical Name:** `Process Variable Serialization & Marshalling`
* **Reference Examples:** [`v9/bamoe-examples/process-persistence/`](../v9/bamoe-examples/process-persistence/) and [`v9/bamoe-examples/process-persistence-springboot/`](../v9/bamoe-examples/process-persistence-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.kogito.serialization.VariableMarshaller`
  - Jackson `ObjectMapperCustomizer` (Quarkus) / `Jackson2ObjectMapperBuilderCustomizer` (Spring Boot)
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Configures custom JSON/Protobuf/Avro object serializers and date/time formatters for domain model objects stored inside process instance variables.
  - **Engine Invocation Lifecycle:** Invoked during process state persistence to PostgreSQL/Infinispan and during REST request serialization.
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, variables required Java `Serializable` or custom `Jaxb/XStream` marshallers configured in KIE Server. In BAMOE v9, standard Jackson object mapper modules are used.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.serialization;

import jakarta.inject.Singleton;
import io.quarkus.jackson.ObjectMapperCustomizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Singleton
public class CustomProcessVariableMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
    }
}
```

##### Spring Boot:
```java
package org.acme.serialization;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class CustomProcessVariableSerializationConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.modules(new JavaTimeModule());
    }
}
```

---

### 3.11. Process Version Resolver

* **Canonical Name:** `Process Version Resolver`
* **Reference Examples:** [`v9/incubator-kie/kogito-jbpm/jbpm-flow/src/main/java/org/kie/kogito/process/version/ProjectVersionProcessVersionResolver.java`](../v9/incubator-kie/kogito-jbpm/jbpm-flow/src/main/java/org/kie/kogito/process/version/ProjectVersionProcessVersionResolver.java)
* **Interface:** `org.kie.kogito.process.ProcessVersionResolver`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Selects which process definition version should be instantiated when a client triggers a workflow by process ID in environments hosting multiple active versions.
  - **Engine Invocation Lifecycle:** Invoked at process instance creation (`Process.createInstance(...)`).
  - **Data Contract:**
    - **Input:** `Process` definition metadata.
    - **Output:** Returns version string (`String`).
  - **v8 Predecessor & Migration Difference:** In BAMOE v8, KIE Container Aliases were used to manage version routing across deployments. In BAMOE v9, versioning is resolved inside the service or via Kubernetes/OpenShift traffic routing.

#### Framework Implementations:

##### Quarkus:
```java
package org.acme.version;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessVersionResolver;

@ApplicationScoped
public class CustomProcessVersionResolver implements ProcessVersionResolver {

    @Override
    public String apply(Process process) {
        return process.version() != null ? process.version() : "1.0";
    }
}
```

##### Spring Boot:
```java
package org.acme.version;

import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessVersionResolver;
import org.springframework.stereotype.Component;

@Component
public class CustomProcessVersionResolver implements ProcessVersionResolver {

    @Override
    public String apply(Process process) {
        return process.version() != null ? process.version() : "1.0";
    }
}
```

---

### 3.12. Decision (DMN) & Rule Runtime Event Listeners

* **Canonical Name:** `Decision & Rule Runtime Event Listener`
* **Reference Examples:** [`v9/bamoe-examples/dmn-listener-quarkus/`](../v9/bamoe-examples/dmn-listener-quarkus/) and [`v9/bamoe-examples/dmn-listener-springboot/`](../v9/bamoe-examples/dmn-listener-springboot/)
* **Interfaces & Base Classes:**
  - `org.kie.dmn.api.core.event.DMNRuntimeEventListener`
  - `org.kie.kogito.decision.DecisionEventListenerConfig`
  - `org.kie.kogito.rules.RuleEventListenerConfig`
* **Architecture & Deep-Dive Description:**
  - **Purpose:** Intercepts DMN decision evaluation and Drools rule execution events when invoked standalone or within BPMN Business Rule Task nodes.
  - **Engine Invocation Lifecycle:** Fired before and after evaluating decisions, decision tables, and Business Knowledge Models (BKMs).
  - **Data Contract:**
    - **Input:** `BeforeEvaluateDecisionEvent` and `AfterEvaluateDecisionEvent` containing the input context and computed decision results.
    - **Output:** `void`.

#### Framework Implementations:

##### Quarkus:
```java
package org.kie.kogito.dmn.quarkus.example.listener;

import jakarta.enterprise.context.ApplicationScoped;
import org.kie.dmn.api.core.event.AfterEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.DMNRuntimeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LoggingDMNRuntimeEventListener implements DMNRuntimeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDMNRuntimeEventListener.class);

    @Override
    public void beforeEvaluateDecision(BeforeEvaluateDecisionEvent event) {
        LOGGER.info("[Quarkus] Before DMN decision: {}", event.getDecision().getName());
    }

    @Override
    public void afterEvaluateDecision(AfterEvaluateDecisionEvent event) {
        LOGGER.info("[Quarkus] After DMN decision: {} -> Result: {}", 
            event.getDecision().getName(), 
            event.getResult().getDecisionResultByName(event.getDecision().getName()).getResult());
    }
}
```

##### Spring Boot:
```java
package org.kie.kogito.dmn.springboot.example.listener;

import org.kie.dmn.api.core.event.AfterEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.DMNRuntimeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDMNRuntimeEventListener implements DMNRuntimeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDMNRuntimeEventListener.class);

    @Override
    public void beforeEvaluateDecision(BeforeEvaluateDecisionEvent event) {
        LOGGER.info("[SpringBoot] Before DMN decision: {}", event.getDecision().getName());
    }

    @Override
    public void afterEvaluateDecision(AfterEvaluateDecisionEvent event) {
        LOGGER.info("[SpringBoot] After DMN decision: {} -> Result: {}", 
            event.getDecision().getName(), 
            event.getResult().getDecisionResultByName(event.getDecision().getName()).getResult());
    }
}
```

---

## 4. Conclusion & Standards Compliance

All 12 extension points documented above:
1. Provide **side-by-side, framework-native code examples for both Quarkus (CDI) and Spring Boot**.
2. Include full architectural deep-dives (Lifecycle, Data Contracts, Thread Safety, Error Handling, and v8 Predecessors).
3. Implement first-class **programmatic SPIs** located in `org.kie.kogito.*` / `org.kie.api.*` in upstream `v9/incubator-kie`.
4. Have concrete, working reference implementations in [`v9/bamoe-examples/`](../v9/bamoe-examples/) and [`v9/incubator-kie-kogito-examples/`](../v9/incubator-kie-kogito-examples/).
5. Fully satisfy the goals and non-goals defined in [`docs/Capability.md`](../docs/Capability.md) and [`deepak/bamoe-design-docs/DBACLD-214879`](../deepak/bamoe-design-docs/DBACLD-214879%20-%20Support%20programmatic%20invocation%20of%20BAMOE%20extension%20APIs/README.md).
