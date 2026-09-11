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

# CiComputeBuildScopes snapshot scenarios

Each subdirectory is a scenario for
[`CiComputeBuildScopesTest.java`](../CiComputeBuildScopesTest.java): the runner
feeds `changed-files.txt` to `CiComputeBuildScopes` (with `CI_PARTITIONS_DIR`
pointing to the real partition files) and asserts the produced
`changed` / `affected` / `upstream` lists — plus per-partition and default
variants — match the committed `expected-*.txt` goldens.

Scenarios are numbered (`01-`, `02-`, …) so new ones append without
renumbering. What each one exercises is explained in its own `README.md`.

## Module add / remove

There is no dedicated "adds a module" or "removes a module" scenario:

- **Add.** The new module's files are on disk at checkout, so they map like any
  other module. The operation that actually defines the addition — editing the
  parent `<module>` list — is covered by `06-aggregator-pom`.
- **Remove.** The module is absent from both disk and the reactor graph, so
  paths under it walk up to the nearest surviving ancestor — covered by
  `07-removed-module`.

## Updating the goldens

`CiComputeBuildScopes` builds the extension on demand, so this is a one-liner:

```bash
CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiComputeBuildScopesTest.java
git add script/ci/tests/scenarios-compute-build-scopes/*/expected-*.txt
```

When CI fails because the cascade shifted (module moved, dep added/dropped,
module added/removed from the reactor), regenerate and commit the updated
goldens in a **separate, dedicated PR** so reviewers can judge whether the
shift is expected.

## Adding a scenario

1. `mkdir script/ci/tests/scenarios-compute-build-scopes/NN-short-name/`
2. Write `changed-files.txt` — real repo-relative paths, or non-existent paths
   to simulate deletions.
3. Write a short `README.md` explaining the case.
4. Run `CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiComputeBuildScopesTest.java`
   to generate the `expected-*.txt` files.
5. Commit everything.
