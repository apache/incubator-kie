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

# Conventions

## Code style

- Configure your IDE with the shared formatter and import order files from
  [`kogito-build/kogito-ide-config/src/main/resources/`](../kogito-build/kogito-ide-config/src/main/resources/):
  - **Eclipse**: import `eclipse-format.xml` under *Preferences > Java > Code
    Style > Formatter*, and `eclipse.importorder` under *Organize Imports*.
  - **IntelliJ IDEA**: install the *Eclipse Code Formatter* plugin, point it at
    `eclipse-format.xml`, enable *Optimize Imports*, and use
    `eclipse.importorder`.
  - **VS Code**: install the *Checkstyle for Java* extension and set
    `eclipse-format.xml` as the Checkstyle configuration file.
- No `@author` tags in Javadoc — Git history tracks authorship.
- Prefer readability over conciseness: self-explanatory method names,
  meaningful variable names, small classes with a single clear responsibility,
  and methods that don't depend on hidden state established by their callers.

## Licensing

- Every source file carries the ASF license header. This is enforced in CI
  (`ci_check_license_headers` workflow). To format headers automatically:

  ```bash
  mvn com.mycila:license-maven-plugin:format
  ```

- Dependency licensing rules (Apache-2.0-compatible only, available on Maven
  Central, no fat/shaded jars, sources public, …) are spelled out in
  [CONTRIBUTING.md](../CONTRIBUTING.md#requirements-for-dependencies).

## Service discovery (SPI)

Two discovery mechanisms are used across the codebase:

- **Standard Java SPI** (`src/main/resources/META-INF/services/`) — used by
  Efesto plugins (`KieCompilerService`, `KieRuntimeService`) and the PMML
  engine.
- **KIE service discovery** (`META-INF/kie.conf`) — the traditional Drools
  mechanism; e.g. `drools-mvel` registers its implementations there, and
  missing implementations are reported centrally by
  `org.drools.core.base.CoreComponentsBuilder`.

## Code generation

Code generation is used by several engines (Drools executable model, Kogito
codegen, PMML). Shared principles:

- **Generate the code you would write by hand** if there were no codegen step —
  and before opening a PR, read the generated code and check that it does.
- Generation is JavaParser-based and template-driven; prefer manipulating the
  AST over plain string replacement. Template files use the `.tmpl` extension
  (PMML) or live under `src/main/resources/class-templates/` named
  `<Something><Runtime>Template.java` with `$…$` placeholders (Kogito).
- Prefer framework-specific templates plus shared plain classes over
  framework-agnostic templates patched by annotators. A template with no
  placeholders should be promoted to a plain shared class.
- In generated DI code, favor constructor injection; REST endpoints are the
  exception (field injection, no inheritance).

## Testing

- Integration test classes are named `*IT.java` (failsafe); everything else is
  a unit test (surefire). See [BUILDING.md](./BUILDING.md) for the flags and
  Quarkus-specific annotations.
- Include tests with every PR — existing unit tests are treated as the
  compatibility contract when refactoring.
- After significant refactoring of an engine, run the corresponding benchmarks
  in [incubator-kie-benchmarks](https://github.com/apache/incubator-kie-benchmarks)
  to catch performance regressions.

## Documentation

- Architecture and design diagrams are authored as PlantUML `.puml` files.
- Reference documentation for end users belongs to the
  [Apache KIE website](https://kie.apache.org/docs/documentation/), not this
  repository. Documentation about how *this repository* works belongs in
  [docs/](./README.md).
