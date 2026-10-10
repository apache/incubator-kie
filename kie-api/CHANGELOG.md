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

All notable changes to the kie-api module will be documented in this file.

## [Unreleased]

## [10.2.0] - 2026-03-28

### Added
- `ProcessStateChangeEvent` - Event interface for process state changes (PR #6358)
- `ProcessNodeStateChangeEvent` - Event interface for process node state changes (PR #6358)
- `ProcessEventListener.onProcessStateChanged(ProcessStateChangeEvent)` - Default method for process state change notifications (PR #6358)
- `ProcessEventListener.onNodeStateChanged(ProcessNodeStateChangeEvent)` - Default method for node state change notifications (PR #6358)
- `KieCommands.newApplyPmmlModel(Map<String, Object>)` - New PMML model command using Map instead of PMMLRequestData (PR #6310)

### Removed
- `KieCommands.newApplyPmmlModel(PMMLRequestData)` - Migrate to `newApplyPmmlModel(Map<String, Object>)` by converting your PMMLRequestData to a Map (PR #6310)

## [10.1.0] - 2025-04-30

### Added
- `ProcessRetriggeredEvent` - Event interface for process retriggered events (PR #6275)
- `ProcessNodeTriggeredEvent.isRetrigger()` - Default method to detect retriggered nodes (PR #6275)
- `RuntimeSession` - New interface extracting common runtime session operations (PR #6103)
- `KieRuntimeBuilder.newStatelessKieSession()` - Create stateless session from builder (PR #6103)
- `KieRuntimeBuilder.newStatelessKieSession(String)` - Create named stateless session from builder (PR #6103)
- `KieCommands.newBatchExecution(Command...)` - Default method for batch execution (PR #6103)
- `NodeInstanceContainer.getSerializableNodeInstances()` - Default method for serialization support (PR #6116)

### Changed
- `KieRuntime` now extends `RuntimeSession` - Refactored to use new RuntimeSession interface (PR #6103)
- `StatelessKieSession` now extends `RuntimeSession` - Refactored to use new RuntimeSession interface (PR #6103)

### Removed
- `org.kie.api.event.usertask.*` classes - Moved to `org.kie.kogito.usertask.events` as part of kogito-api (PR #6052). Update your imports to use the new package location
- `ResourceType.DRF` field - Removed as part of .rf (ruleflow) format cleanup (PR #6189). The .rf format is no longer supported. Use BPMN2 format instead

## [10.0.0] - 2024-12-10

### Added
- Initial release

[Unreleased]: https://github.com/apache/incubator-kie-drools/compare/10.2.0...HEAD
[10.2.0]: https://github.com/apache/incubator-kie-drools/compare/10.1.0...10.2.0
[10.1.0]: https://github.com/apache/incubator-kie-drools/compare/10.0.0...10.1.0
[10.0.0]: https://github.com/apache/incubator-kie-drools/releases/tag/10.0.0