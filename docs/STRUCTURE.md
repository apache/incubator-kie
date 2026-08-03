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

# Repository structure

This repository hosts several Apache KIE projects, built together as a single
Maven reactor. All top-level modules are declared in the root
[pom.xml](../pom.xml).

## Module prefixes

Top-level modules are grouped by prefix:

| Prefix | What it is |
| --- | --- |
| `drools-` | The Drools rule engine: runtime, compiler, DRL language, executable model, and related tooling |
| `optaplanner-` | The OptaPlanner constraint solver |
| `kogito-` | The Kogito cloud-native runtimes: code generation, Quarkus/Spring Boot integrations, add-ons |
| `kogito-apps-` | The Kogito supporting services and their build infrastructure |

A `fullProfile` profile (activated with `-Dfull`) adds distribution modules
such as `drools-distribution` to the build.

## Parent poms and BOMs

Two parallel hierarchies exist:

**KIE/Drools side:**
- [`kie-parent`](../kie-parent) — main build configuration (plugins, profiles) for the Drools/KIE modules
- [`bom`](../bom) (`drools-bom`) — the artifacts distributed by the Drools side
- [`build-parent`](../build-parent), [`kie-quarkus-build-parent`](../kie-quarkus-build-parent) — build configuration layers

**Kogito side:**
- `kogito-bom` — every library distributed by Kogito; **any new Kogito module must be added there**
- `kogito-dependencies-bom` — all third-party dependencies, runtime-agnostic
- `kogito-build-no-bom-parent` — main entry point for build configuration (plugins, profiles); deliberately imports no Kogito BOM so the Quarkus Platform can control dependency management
- `kogito-build-parent` — inherits the above and imports `kogito-bom`; every internal Kogito module inherits from it
- `kogito-quarkus/bom` and `kogito-springboot/bom` — add the Quarkus / Spring Boot BOMs respectively

Rule of thumb when adding a dependency: runtime-specific dependencies go to the
respective runtime BOM; everything else goes to the dependencies BOM. See the
dependency requirements in [CONTRIBUTING.md](../CONTRIBUTING.md).


---

*Historical footnote: the `optaplanner-`, `kogito-` and `kogito-apps-` module
groups originate from formerly separate repositories that were folded into
this one; the root [pom.xml](../pom.xml) still marks those sections with
`BEGIN/END … modules (auto)` comments.*
