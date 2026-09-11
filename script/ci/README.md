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

# CI scripts

Used by [.github/workflows/ci.yaml](../../.github/workflows/ci.yaml) and
[.github/workflows/ci-parallel.yaml](../../.github/workflows/ci-parallel.yaml).

- [CiComputeBuildScopes.java](CiComputeBuildScopes.java) — figures out which modules a PR actually touches, so CI only builds and tests what's needed instead of the whole reactor. When `CI_PARTITIONS_DIR` is set, it also partitions affected modules across parallel jobs using the partition files in [.github/supporting-files/ci/partitions/](../../.github/supporting-files/ci/partitions/). Its optional fifth output contains the upstream modules listed in [`image-producers.txt`](../../.github/supporting-files/ci/partitions/image-producers.txt), so CI can build their image artifacts before running downstream tests.
- [CiSummary.java](CiSummary.java) — turns the build's test results into the human-readable summary you see at the top of a CI run on GitHub.
- [DepGraph.java](DepGraph.java) — reads the dependency graph TSV. Shared with [script/dev/](../dev/), so CI and local builds cannot disagree about the reactor.
- [dep-graph-extractor/](dep-graph-extractor/) — Maven extension that writes the reactor dependency graph. Required by `CiComputeBuildScopes` (and exercised transitively by its tests). Its output is a TSV of one-letter record types; consumers ignore types they do not know, so new ones can be added without breaking them.

These are also used outside CI: [script/dev/Dev.java](../dev/Dev.java) reuses `CiComputeBuildScopes` and the dependency graph for partial builds. See [docs/DEV.md](../../docs/DEV.md).

## Developing these scripts

If you change `CiComputeBuildScopes.java` or `CiSummary.java`, run the snapshot tests
locally before pushing — they cover the scenarios under
[tests/scenarios-compute-build-scopes/](tests/scenarios-compute-build-scopes/) and
[tests/scenarios-summary/](tests/scenarios-summary/).

```bash
jbang script/ci/tests/CiComputeBuildScopesTest.java
jbang script/ci/tests/CiSummaryTest.java
```

If a behavior change is intentional, regenerate the goldens by re-running the tests with
`CI_UPDATE_GOLDEN=1` — they will rewrite the matching `expected-*.txt` /
`expected-summary.md` files instead of asserting:

```bash
CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiComputeBuildScopesTest.java
CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiSummaryTest.java
```

Review the diff and commit the updated goldens alongside the script change.
