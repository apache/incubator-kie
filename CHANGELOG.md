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

## [Unreleased]

### Added
- YAML property retrieval support (PR #6662)

### Changed
- Upgraded Quarkus to 3.27.4 (PR #6747)
- Upgraded Netty to 4.1.135 (PR #6755)
- Upgraded Netty to 4.1.133.Final (PR #6747)
- Upgraded Vert.x to 4.5.27 (PR #6747)
- Upgraded Jetty to 12.0.33 (PR #6748)
- Upgraded Wiremock to 3.13.2 (PR #6741)
- Upgraded logback-core to 1.5.34 (PR #6743)
- Upgraded Quarkus to 3.27.3 (PR #6673)
- Upgraded kafka-clients to 4.1.2 (PR #6671)
- Provide correct Jackson 2 integration for all Spring Boot based KIE modules (PR #6735)

### Removed
- YaRD and Serverless Workflow support (PR #6663)

### Security
- Fixed CVE-2024-6763, CVE-2025-11143, CVE-2026-2332, CVE-2025-5115: Upgraded Jetty to 12.0.33 (PR #6748)
- Fixed CVE-2026-45292: Upgraded opentelemetry-api to 1.44.1 (PR #6744)
- Fixed CVEs: Upgraded logback-core to 1.5.34 (PR #6743)
- Fixed CVE-2026-33870, CVE-2026-33871: Upgraded netty-codec-http to 4.1.131.Final (PR #6673)
- Fixed CVE-2025-67030: Upgraded plexus-utils to 3.6.1 (PR #6670)
- Fixed CVE-2025-67721: Upgraded Jackson core to 2.21.1 (PR #6672)
- Fixed CVE-2026-1002: Upgraded vertx-core to 4.5.24 (PR #6636)
- Fixed CVE-2022-41853: Upgraded HSQLDB to 2.7.1 (PR #6563)

### Fixed
- Guard subnetwork-not right-delete against null memory (PR #6756)
- Fix broken behavior involving ForIterationNode (PR #6746)
- Fix CiComputeBuildScopesTest.java (PR #6745)
- Fix getDecisionService method NPE (PR #6740)
- Fix NPE in RuleExecutor.removeDormantTuple (PR #6707)
- Preserve insertion order in ListDataStore (PR #6734)
- PseudoClockScheduler may retain 1000 canceled jobs at most (PR #6660)
- Flaky PersistenceTest by disabling parallel execution (PR #6675)

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

[Unreleased]: https://github.com/apache/incubator-kie-drools/compare/10.2.0...HEAD
[10.2.0]: https://github.com/apache/incubator-kie-drools/compare/10.1.0...10.2.0
[10.1.0]: https://github.com/apache/incubator-kie-drools/compare/10.0.0...10.1.0
[10.0.0]: https://github.com/apache/incubator-kie-drools/releases/tag/10.0.0