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

# PR checks and CI

CI runs on GitHub Actions. Workflows live in
[`.github/workflows/`](../.github/workflows/); the logic they rely on lives in
[`script/ci/`](../script/ci/) as [JBang](https://www.jbang.dev) scripts, plus
local composite actions in [`.github/actions/`](../.github/actions/).

## `CI :: Build` — the main PR check

Defined in [ci.yaml](../.github/workflows/ci.yaml). Runs on every pull request
against `main`, and on every push to `main`. Documentation-only changes
(Markdown/AsciiDoc files, `LICENSE`, text files, images under `docsimg/`) do
not trigger it.

On a pull request it builds **only what the PR can affect**, instead of the
whole reactor:

1. The PR is checked out as a **simulated squashed merge** onto the base
   branch (via the local [`checkout-pr`](../.github/actions/checkout-pr)
   action), so the build sees exactly what `main` would look like after the
   merge.
2. The changed files are collected with a three-dot diff against the base
   branch.
3. [`CiComputeBuildScopes.java`](../script/ci/CiComputeBuildScopes.java) maps
   the changed files to two disjoint sets of Maven modules, using the
   dependency graph produced by
   [`dep-graph-extractor`](../script/ci/dep-graph-extractor/):
   - **upstream** — intra-reactor dependencies of the affected modules; built
     with tests skipped, just to populate the local repository;
   - **affected** — the changed modules plus everything transitively
     downstream of them; built with tests.
4. Both passes run with `-fae` (fail at end) so every broken module is
   reported, not just the first one, and the affected pass builds with
   `-Dreproducible`. Enforcer, Checkstyle, formatter and ArchUnit plugins are
   skipped in PR builds — they are validated elsewhere.
5. [`CiSummary.java`](../script/ci/CiSummary.java) publishes a build summary
   (including the computed scopes and test results) to the workflow run.

On a push to `main` it instead runs a full `mvn install` of the whole reactor,
with `-Dfull` (which additionally builds the distribution modules and Javadoc
JARs) and `-Dreproducible`.

Notes:

- The build matrix currently runs on Linux with Java 17 and 21; macOS and
  Windows are temporarily excluded because of Docker-dependent tests failing
  on non-Linux runners.
- The `apache.snapshots` Maven repository is blocked via a mirror in
  `settings.xml`, so builds cannot silently depend on snapshot artifacts.
- A new push to a PR cancels that PR's in-progress run; runs for pushes to
  `main` are never canceled.
- After the build, a Surefire report step fails the job on test failures, and
  the build logs (`build.log`) plus the reproducibility outputs
  (`*.buildcompare`, `*.buildinfo`) are uploaded as workflow artifacts.

To re-run the checks, push a new commit to the PR — an empty commit is enough:
`git commit --allow-empty -m "re-trigger CI"`.

## `CI :: Examples` — downstream check

Defined in [pr-downstream.yml](../.github/workflows/pr-downstream.yml). Runs
on every pull request against `main` (same documentation-only exclusions as
`CI :: Build`). It verifies that the PR does not break the
[apache/incubator-kie-kogito-examples](https://github.com/apache/incubator-kie-kogito-examples)
repository:

1. This repository is built and installed into the local Maven repository with
   tests skipped, so the examples resolve the PR's snapshot artifacts.
2. The examples repository is checked out and built — one matrix job per
   subfolder: `kogito-quarkus-examples`, `kogito-springboot-examples`, and
   `gradle-examples`.
3. A Surefire report step fails the job on test failures, and the Maven
   dependency trees are uploaded as workflow artifacts.

## `CI :: CI Tests`

Defined in [ci-tests.yaml](../.github/workflows/ci-tests.yaml). Snapshot tests
for the CI scripts themselves: the scenarios under
[`script/ci/tests/`](../script/ci/tests/) verify that build-scope computation
and summary generation behave as expected. If you change anything in
`script/ci/`, this is the check that guards it.

## License header check

Defined in
[ci_check_license_headers.yaml](../.github/workflows/ci_check_license_headers.yaml).
Runs [Apache RAT](https://creadur.apache.org/rat/) on every PR and fails if
any file is missing the ASF license header. See
[CONVENTIONS.md](./CONVENTIONS.md#licensing) for how to format headers
automatically.

## Split package detection

Defined in
[split-package-detection.yml](../.github/workflows/split-package-detection.yml).
Detects Java packages split across multiple modules. The explicitly allowed
exceptions live in
[check-split-packages-allowed.txt](../script/split-packages/check-split-packages-allowed.txt);
introducing a new split package fails the check.
