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

# Local development scripts

Used by the `dev` target in the [Makefile](../../Makefile).

- [Dev.java](Dev.java) — the whole tool: turns a choice of git changes into a Maven
  `-pl` list, and works out which upstream modules have to be rebuilt first.

Two things are deliberately shared with CI rather than copied, as JBang `//SOURCES`
dependencies, so that local builds and pull request builds cannot disagree:

- [`script/ci/CiComputeBuildScopes.java`](../ci/CiComputeBuildScopes.java) — maps
  changed files to the modules that need building.
- [`script/ci/DepGraph.java`](../ci/DepGraph.java) — the reactor dependency graph
  written by the `dep-graph-extractor` Maven extension.

## Developing this script

Run it directly to skip Make:

```bash
jbang script/dev/Dev.java scope
jbang script/dev/Dev.java mvn clean install -DskipTests
jbang script/dev/Dev.java config
```

Commands are none (the usual build), `scope` (dry run), `config` (show settings)
and `mvn` (your own Maven arguments).

State lives in `.kie-dev/` at the repository root. When debugging, that
directory is the first place to look — `changed-files.txt` and the `pl-*.txt`
files show exactly what the last run computed. Delete the directory to reset.

## Tests

```bash
jbang script/dev/tests/DevTest.java    # the script
jbang script/dev/tests/MakefileTest.java    # the `dev` target in the Makefile
```

Both run in
[dev-tests.yaml](../../.github/workflows/dev-tests.yaml).

They are fast and hermetic: `DevTest` builds a throwaway git repository,
dependency graph and local Maven repository per test, so it needs git but never
Maven, and `MakefileTest` only ever runs `make --dry-run`. Neither reads the real
reactor — add a case there rather than reaching for a fixture in this repository.

Because this script shares `CiComputeBuildScopes` with CI, changes to that file
also affect pull request builds. Run its snapshot tests too:

```bash
jbang script/ci/tests/CiComputeBuildScopesTest.java
```

See [script/ci/README.md](../ci/README.md) for more on those tests.
