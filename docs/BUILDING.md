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

# Building

## Prerequisites

- Java SDK (OpenJDK recommended) and Git
- Docker — not strictly required, but needed by some integration tests
  (Testcontainers-based); those tests can be skipped
- For GraalVM native image builds on macOS, see
  [DEVELOP_ON_MACOS.md](./DEVELOP_ON_MACOS.md)

## Basic build

The repository ships the Maven wrapper; build from the root:

```bash
./mvnw clean install
```

A full build with all tests takes a long time. Common ways to shorten the
cycle:

| Flag | Effect |
| --- | --- |
| `-DskipTests` | Skip unit and integration tests |
| `-DquickTests` | Formatting validation and unit tests only (skips invoker and archetype tests) |
| `-DskipITs` | Skip integration tests only (failsafe) |
| `-Dinvoker.skip=true` | Skip maven-invoker-based integration tests (e.g. in `kie-maven-plugin`) |
| `-Dquickly` | Kogito-apps modules only: skip most plugins and tests |
| `-Dfull` | Also build distribution modules (`fullProfile`) |

You can also build only the module you work on, plus what depends on it, with
standard Maven reactor flags (`-pl <module> -am` / `-amd`).

## Tests

- Unit tests run with surefire; integration tests run with failsafe and are
  named with a trailing `IT` (e.g. `SomethingIT.java`).
- Some tests assume an `en_US` locale. On machines with a different locale,
  activate the `test-en` profile: `-Ptest-en` or `-DTestEn`.
- Test coverage (Jacoco): `mvn clean verify -Ptest-coverage`, report under
  `target/site/jacoco/`.
- Quarkus modules: `@QuarkusTest` classes are unit tests (surefire),
  `@QuarkusIntegrationTest` classes are integration tests (failsafe,
  `*IT.java`). The two cannot be mixed in the same `integration-test` phase.
- Maven-invoker-based integration tests (which build real kjars with the KIE
  Maven plugin) support running a subset with `-Dinvoker.test=<pattern>`;
  their build logs land under `target/it/<module_name>/build.log`.

## Troubleshooting

- **`UnmappableCharacterException` during the build**: set
  `MAVEN_OPTS=-Dfile.encoding=UTF-8` (as an environment variable, not a `mvn`
  argument) and rebuild.
- **Testcontainers fails with "Can not connect to Ryuk at localhost"** (seen
  with some Docker for Mac versions, or when privileged containers are not
  allowed): try `export TESTCONTAINERS_RYUK_DISABLED=true`.
- **Locale-dependent test failures**: use the `test-en` profile described
  above.
