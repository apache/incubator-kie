<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  -->

# Changelog

All notable changes to the Apache KIE Drools project will be documented in this file.

## [10.3.0] - 2026-11-xx

### Added
- YAML property retrieval support (PR #6662)
- `sequence()` DSL and Phreak `SequenceNode` (PR #6909)
- RuleUnit DSL: rule attributes (PR #6912), STREAM event processing with CEP temporal operators (PR #7104) and follow-up CEP operators (PR #7122)
- Declared enums can reference other declared types (PR #6738)
- Workflow Engine: dynamic process calling (PR #6824)
- Workflow Engine: dynamic signals based on variables (PR #7204)
- Support for undeclared process variables (PR #7199)
- Support for `java.lang.Object` as a process variable type (PR #7151)
- Support for untyped collection process variables in persistence proto generation (PR #7196)
- Allow WS-Human Task lifecycle transition parameters (PR #7139)
- Configurable pool size for the REST work item handler `WebClient` (PR #7167)
- `outOfOrder` property in `KieFlyway` (PR #7193)
- Data isolation by process id and version pair in data-audit addons (PR #6852)

### Changed
- Migrated `quarkus-resteasy` to `quarkus-rest` (PR #7258)
- Default expression format changed from MVEL to Java (PR #6885)
- Unified user task models and REST endpoints by task name in BPMN processes (PR #7207)
- Interrupting event sub process aligned with BPMN 2.0 specification, section 13.4.4 (PR #7257)
- Sequential mode: disabled beta node sharing (PR #7202), ignored `@Propagation(IMMEDIATE)` (PR #7220) and removed the fallback to sequential mode in `ForEachNodeInstance` with async nodes (PR #7208)
- Delay `ProcessInstance` lock release to transaction commit to avoid stale reads in multi-threaded scenarios (PR #7163)
- Upgraded Quarkus to 3.33.4 (PR #7153)
- Upgraded Spring Boot to 4.1.1 (PR #7128)
- Upgraded Spring Framework to 7.0.9 (PR #7086)
- Upgraded Netty to 4.1.138.Final (PR #7153)
- Upgraded Vert.x to 4.5.34 (PR #7153)
- Upgraded Jetty to 12.0.36 (PR #6828)
- Upgraded Tomcat embed core to 11.0.26 (PR #7169)
- Upgraded Jackson to 2.22.3 (PR #7157) and Jackson 3.x to 3.1.7 (PR #7195)
- Upgraded logback-core to 1.6.3 (PR #7090)
- Upgraded Micrometer to 1.17.1, OW2 ASM to 9.11.1 and snappy-java to 1.1.10.12 (PR #7228)
- Upgraded MVEL to 2.5.4.Final (PR #7077)
- Upgraded Guava to 33.7.2-jre (PR #7249)
- Upgraded Maven to 3.9.16 (PR #6899)
- Upgraded Wiremock to 3.13.2 (PR #6741)
- Upgraded kafka-clients to 4.1.2 (PR #6671)
- Upgraded Bouncy Castle to 1.86 and Apache Ant to 1.10.18 (PR #7195)
- Upgraded OpenNLP to 2.5.12, WildFly Elytron to 2.8.5 and zstd-jni to 1.5.7-16 (PR #7169)
- Upgraded lz4-java to 1.11.4 (PR #7183)
- Upgraded log4j-api to 2.26.1 (PR #6869)
- Upgraded Keycloak to 26.1.5 (PR #7098)
- Upgraded MongoDB driver to 5.11.1 (PR #7153)
- Upgraded FreeMarker to 2.3.35 (PR #7131)
- Upgraded SCRAM to 3.3 (PR #6797)
- Upgraded parsson to 1.1.8 (PR #6799)

### Removed
- YaRD and Serverless Workflow support (PR #6663)

### Security
- Fixed CVE-2024-6763, CVE-2025-11143, CVE-2026-2332, CVE-2025-5115: Upgraded Jetty (PR #6748)
- Fixed CVE-2026-45292: Upgraded opentelemetry-api to 1.62.0 (PR #7112)
- Fixed CVE-2026-33870, CVE-2026-33871, CVE-2026-42583: Upgraded Netty (PR #6673, PR #6764)
- Fixed CVE-2025-67030: Upgraded plexus-utils to 3.6.1 (PR #6670)
- Fixed CVE-2025-67721: Upgraded Jackson core (PR #6672)
- Fixed CVE-2026-1002: Upgraded vertx-core (PR #6636)
- Fixed CVE-2022-41853: Upgraded HSQLDB to 2.7.1 (PR #6563)
- Fixed CVE-2026-56624: Upgraded Apache MINA SSHD to 2.19.0 (PR #6873)
- Fixed CVE-2026-107314, CVE-2026-107315: Upgraded PostgreSQL JDBC driver to 42.7.14 (PR #7218)
- Fixed CVE-2026-18710: Pinned MongoDB driver (PR #7093)
- Fixed CVE-2026-59295, CVE-2026-59296: Upgraded Micrometer (PR #7088)
- Fixed CVE-2026-47857, CVE-2026-47863: Upgraded reactor-core to 3.8.7 (PR #7103)
- Fixed log forging (CWE-117) (PR #6861) and sanitized control characters in `DataAuditQuery.toString()` (PR #7268)
- Validate protobuf session deserialization (PR #6823)
- Apply the Drools deserialization class allowlist to Java-serialized process variables (PR #7254)

### Fixed
- Provide correct Jackson 2 integration for all Spring Boot based KIE modules (PR #6735)
- Guard subnetwork-not right-delete against null memory (PR #6756)
- Fix broken behavior involving ForIterationNode (PR #6746)
- Fix getDecisionService method NPE (PR #6740)
- Fix NPE in RuleExecutor.removeDormantTuple (PR #6707)
- Preserve insertion order in ListDataStore (PR #6734)
- PseudoClockScheduler may retain 1000 canceled jobs at most (PR #6660)
- not-subnetwork silently stops firing after cross-package segment split (PR #7135)
- ClassCastException in doSubnetwork2 when a subnetwork is shared by not() and accumulate() (PR #7191)
- exists(A() and not B()) stops firing when a sibling package wraps the same CE in its own exists (PR #7116)
- Accumulate rule stops firing after its segment is split (PR #7110)
- Evaluate alpha constraints during isAllowedCachedLeft in CombinedConstraint (PR #7124)
- Collect facts, not subnetwork tuples, in GenericCollectAccumulator (PR #7224)
- Accumulate for non-reversible functions (PR #6796)
- Inline-accumulate result binding used in a result constraint (PR #6791)
- NPE evaluating after() under an OR-of-negated-join (PR #6760)
- Park no-loop/lock-on-active blocked tuples in dormantMatches (PR #6768)
- Auto-focus in PhreakRuleTerminalNode does not halt the active group evaluator (PR #6908)
- Do not create duplicate facts on insert in sequential mode (PR #7250)
- Keep facts dynamic across a marshalling round trip (PR #7127) and release dynamic facts when they are deleted or expired (PR #7176)
- Ternary condition in an inline-eval (PR #7096)
- `contains` on a generic `Map.get()` String no longer broken after jitting (PR #7145)
- Declared types: extends across packages (PR #6785), kbase-wide globals referenced from another package (PR #6786), Java annotations preserved when the DRL declare block is empty (PR #7186)
- Executable model: `@propertyChangeSupport` (PR #7146), Java superclass `@Position` fields (PR #6780), DRL `@role` overriding Java `@Role` (PR #7251), capitalized DRL type annotations (PR #7270)
- DRL10 ANTLR4 parser: lambdas and methods in functions (PR #6896)
- Register concrete project classloaders as parallel capable (PR #6809)
- Improve CronExpression wildcard validation (PR #6774)
- Warning for "correspondence between package and folder names" triggered unexpectedly (PR #7129)
- DMN: nested list results from Context expressions in for loops (PR #7230)
- DMN: `typeRef` on `tUnaryTests` broken for DMN 1.3-1.6 since 10.2.0 (PR #7184)
- DMN: compilation failure on DecisionService-Decision output type mismatch (PR #6776)
- DMN: performance regression of FEEL evaluation (PR #6871)
- DMN: B-FEEL relational comparison returning false for non-boolean operands (PR #6770)
- DMN: date time conversion issue (PR #6789)
- Process variable with an empty structureRef fails to build (PR #7225)
- Intermediate message correlation failure inside a subprocess (PR #7130)
- Message-correlated signals sent through `ProcessEventDispatcher` do not publish DataEvents (PR #7190)
- NPE in embedded job service (PR #7148) and intermittent NPE on terminate (PR #7197)
- `KieMemoryCompilerException: code too large` for large BPMNs (PR #6888)
- Message start event without data mapping fails code generation (PR #6821)
- Duplicate process variable in Swagger page when the name starts with a capital letter (PR #6845)
- Invalid Java identifier generation for REST task params with hyphens (PR #7158)
- Concurrent duplicate ProcessDefinition insert (PR #6850)
- Scope the `UnitOfWorkManager` bean to the application, not the JVM (PR #7107)
- Avoid redundant process instance unmarshalling when signaling via `ProcessService` (PR #7092)
- Use the correct configuration property by removing `@WithParentName` (PR #7152)
- WIH exception handling (PR #7166)

## [10.2.0] - 2026-03-28

### Added
- Question mark operator support in decision table unary test expressions (PR #6577)
- Configurable compile-time warning for self-referencing constraints (PR #6618)
- Validation for fact types used in rules (PR #6524)
- Test Scenario Runner adapted to JUnit 5 Test Engine (PR #6338)
- DMN error handling modes (PR #6387)
- Support for re-scheduling Process Instances and Node Instances SLA timers (PR #6358)
- PathUtils implementation (PR #6355)
- New date and time conversion functions (PR #6303)

### Changed
- **kie-api**: API changes - see [kie-api/CHANGELOG.md](kie-api/CHANGELOG.md#1020---2026-03-28)
- Upgraded Quarkus to 3.27.2 (PR #6575)
- Upgraded Maven to 3.9.11 (PR #6409)
- Upgraded Spring Boot to 3.4.11 (PR #6502)
- Upgraded testcontainers to 2.0.3 (PR #6552)
- Migrated Apache licenses check to apache-rat 0.17.x (PR #6550)

### Security
- Fixed path traversal vulnerability (PR #6615)
- Fixed multiple CVEs via dependency upgrades (PR #6544)
- Upgraded tomcat-dbcp to 10.1.48 (PR #6505)
- Upgraded vertx-web to 4.5.22 (PR #6500)
- Upgraded logback to 1.5.19 (PR #6485)

### Fixed
- DMN TCK unary test 005 returning null (PR #6556)
- DMN missing evaluationHitIds in decision tables (PR #6553)
- DMN missing evaluationHitIds in conditional expressions (PR #6568)
- Null pointer with exists() clauses (PR #6585)
- DMN/EFESTO LocalUriId generation in Gradle context (PR #6607)
- Comparing lists using `in` does not work (PR #6557)
- Activation-group not applied after rule fires (PR #6517)
- Rules in agenda group with auto-focus issues (PR #6449, #6447)
- Multiple rules in activation group firing (PR #6419)
- Conditional named consequence with break (PR #6461)
- Parser exception with leading zeros in DRL10 (PR #6541)
- Executable-model missing passive flag (PR #6530)
- kJar code generation not failing on unknown field in OOPath (PR #6518)

## [10.1.0] - 2025-04-30

### Added
- DMN 1.6 support
- New Antlr4 DRL parser (disabled by default) (PR #6225)
- DMN B-FEEL implementation (PR #6213)
- Support for generic range types in DMN engine (PR #6123)
- DMNEvent reporting in DMNConditionalEvaluator (PR #6124)
- Rule IDs in AfterEvaluateDecisionTableEvent (PR #6127)
- Multi-module and indexed dependencies support (PR #6278)
- Support for BigInteger arithmetic operations with executable-model (PR #6276)
- Support for traits to inherit multiple traits (PR #5824)
- Support for trait type declaration (PR #5820)

### Changed
- **kie-api**: API changes - see [kie-api/CHANGELOG.md](kie-api/CHANGELOG.md#1010---2025-04-30)
- Upgraded Quarkus to 3.15 (PR #6131)
- Upgraded MVEL to 2.5.2.Final (PR #6226)
- Upgraded logback to 1.5.16 (PR #6236)
- Upgraded XStream to 1.4.21 (PR #6148)
- Upgraded protobuf-java (PR #6094)
- Removed drools-docs from repository (Apache policy compliance, PR #6287)
- Cleaned up legacy change-set support (PR #6188)
- Cleaned up legacy .rf format support (PR #6189)

### Security
- Fixed CVE-2023-0833: Upgraded okhttp to address vulnerability (PR #6230)
- Fixed netty-related CVE (PR #6263)
- Upgraded logback-core from 1.4.14 to 1.5.13 (PR #6208)

### Fixed
- DMN TCK range equality test failures for unary test ranges (PR #6134)
- DMN type check for decision service input parameters (PR #6133)
- DMN namespace alignment verification (PR #6259)
- DMN zero-seconds truncation on date time (PR #6255)
- DMN evaluation hit IDs mapping in nested conditional/decision table elements (PR #6271)
- DMN evaluation hit ID mapping to belonging decision (PR #6242)
- DMN range comparison failures in FEEL with unary test ranges (PR #6231)
- DMN range-related TCK failures (PR #6244)
- DMN range starting with null not permitted (PR #6266)
- DMN missing conditional element runtime exception (PR #6257)
- DMN lambda function returning null TCK failure (PR #6261)
- DMN return list of dates instead of range (PR #6223)
- Test Scenario version attribute not correctly retrieved (PR #6254)
- BooleanEvalHelper class cast exceptions (PR #6229)
- Group node validation failure (PR #6262)
- Property reactivity not reacting to super class properties overridden by sub class getter (PR #6251)
- Removal of detached tuples during incremental compilation (PR #6192)
- ForkJoinPool context ClassLoader inheritance (PR #6211)
- SessionConfiguration loading at startup in project templates (PR #6218)
- DroolsAssetsProcessor exception when no DRL resources present (PR #6277)
- Accumulate min evaluation (PR #6186)
- KieServiceLoader thread-safety (PR #6177)

## [10.0.0] - 2024-12-10

### Added
- Initial release
- **kie-api**: Initial API - see [kie-api/CHANGELOG.md](kie-api/CHANGELOG.md#1000---2024-12-10)

[10.3.0]: https://github.com/apache/incubator-kie-drools/compare/10.2.0...HEAD
[10.2.0]: https://github.com/apache/incubator-kie-drools/compare/10.1.0...10.2.0
[10.1.0]: https://github.com/apache/incubator-kie-drools/compare/10.0.0...10.1.0
[10.0.0]: https://github.com/apache/incubator-kie-drools/releases/tag/10.0.0