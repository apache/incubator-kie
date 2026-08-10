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

# Documentation

This directory centralizes documentation about how this repository works — its
structure, its build, and its conventions. User documentation for Drools, OptaPlanner, jBPM, and Kogito lives on
[Apache KIE :: Documentation](https://kie.apache.org/docs/documentation/).

## In this directory

| Document | What it covers |
| --- | --- |
| [STRUCTURE.md](./STRUCTURE.md) | The module prefix structure, the BOM/parent-pom hierarchies, and architectural notes |
| [BUILDING.md](./BUILDING.md) | How to build the repo, useful Maven flags and profiles, and troubleshooting |
| [DEV.md](./DEV.md) | Building only the modules your changes affect, with `make dev` |
| [CONVENTIONS.md](./CONVENTIONS.md) | Code style, licensing, dependency rules, testing and codegen conventions |
| [PR_CHECKS_AND_CI.md](./PR_CHECKS_AND_CI.md) | The checks that run on pull requests, and how CI builds the repo |
| [DEVELOP_ON_MACOS.md](./DEVELOP_ON_MACOS.md) | Setting up macOS for GraalVM native image builds |
| [OTHER_COMPONENTS.md](./OTHER_COMPONENTS.md) | Apache KIE components beyond the four main projects: the PMML engine and KIE Tools |
| [HISTORY.md](./HISTORY.md) | How KIE started, its initiatives over the years, and the move to Apache |

## At the repository root

- [README.md](../README.md) — project introduction
- [CONTRIBUTING.md](../CONTRIBUTING.md) — contribution guide (issues, PRs, dependency requirements)
