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

# Release scripts

Local-first release tooling for the Apache KIE repository.
Since the 10.3.x consolidation, **drools, optaplanner, kogito-runtimes, and
kogito-apps are all modules of the same root POM** — so the release process is
now a single-repo, single-command workflow.

These scripts implement the "local-first" approach: every step runs on your
machine without needing Jenkins.  Jenkins can still call the same scripts, but
you no longer depend on it for the mechanics.

---

## Terminology & Lifecycle Stages

To avoid confusion between different release phases:

| Term | Stage | Meaning & Scope |
|---|---|---|
| **`update-version`** | Automation A / D | Updates Maven POM versions across all modules in the reactor to stream snapshots (`10.3.999-SNAPSHOT`) or exact release versions (`10.3.0`). |
| **`rc-commit` / `rc-tag`** | Automation D.1 | Creates a temporary branch, bumps to the release version, creates the R-commit, tags the Git RC (`10.3.0-rc1`), and cleans up the temporary branch. |
| **`build`** | Automation D.2 | Compiles the full Maven reactor and installs artifacts to the local `~/.m2/repository`. |
| **`deploy` / `staging`** | Automation D.3 | GPG signs artifacts and deploys them to the Apache Nexus Staging repository for release vote inspection. |
| **`tag-release`** | Automation F | Promotes an approved RC tag (e.g., `10.3.0-rc2`) to the final release tag (`10.3.0`) once the release vote passes. |
| **`promote` / `publish`** | Automation G | Releases the staging repository in Nexus to Maven Central and publishes release distribution files. |

---

## Numbered Scripts

Scripts are numbered to clearly communicate their intended execution order:

| Number / Script | Automation Stage | Purpose |
|---|---|---|
| **[`01-update-version.sh`](#01-update-versionsh)** *(alias: `update-version.sh`)* | Automation A / D | Update Maven POM versions + data-index image tag property |
| **[`02-rc-commit.sh`](#02-rc-commitsh)** *(alias: `rc-commit.sh`)* | Automation D.1 | Create temporary branch, R commit, and RC git tag |
| **[`03-build.sh`](#03-buildsh)** *(alias: `build.sh`)* | Automation D.2 | Build the full reactor to local `~/.m2/repository` |
| **[`04-deploy-to-staging.sh`](#04-deploy-to-stagingsh)** *(alias: `deploy-to-staging.sh`)* | Automation D.3 | Deploy signed JARs to Apache Nexus staging repository |
| **[`05-tag-release.sh`](#05-tag-releasesh)** *(alias: `tag-release.sh`)* | Automation F | Promote an approved RC tag → final release tag |
| **[`release-all.sh`](#release-allsh)** | Orchestrator (D) | Orchestrate steps 02 → 03 → 04 in one single command |

Make the scripts executable once:

```bash
chmod +x script/release/*.sh
```

---

## Quick start — full RC candidate locally

```bash
# 1. Be on the development stream branch
git checkout 10.3.x

# 2. Run the full RC flow (no remote side-effects — dry run first)
./script/release/release-all.sh \
    --version 10.3.0 --tag 10.3.0-rc1 \
    --skip-tests \
    --dry-run

# 3. If the dry run looks right, remove --dry-run
./script/release/release-all.sh \
    --version 10.3.0 --tag 10.3.0-rc1 \
    --skip-tests
```

This leaves the RC tag locally (`git tag -l 10.3.0-rc1`).  Nothing has been
pushed or published.  When you are ready to share the RC:

```bash
# Push the RC tag and deploy to Apache Nexus staging
./script/release/release-all.sh \
    --version 10.3.0 --tag 10.3.0-rc1 \
    --skip-tests --push-tag --deploy
```

---

## Scripts in detail

### `01-update-version.sh`

Updates every Maven module's `<version>` in the reactor and the
`data-index-ephemeral.image.tagVersion` property in
`kogito-quarkus-workflow-common-deployment`.

```bash
# Switch to a dev (SNAPSHOT) version — D commit (Automation A)
./script/release/01-update-version.sh 10.3.999-SNAPSHOT

# Switch to the exact release version — R commit (usually automated by 02-rc-commit.sh)
./script/release/01-update-version.sh 10.3.0
```

> **Stream vs release version.** Development branches keep
> `major.minor.999-SNAPSHOT` as their version. The version is only set to
> the exact release version (`10.3.0`) in the short-lived R commit created by
> `02-rc-commit.sh`.

---

### `02-rc-commit.sh`

Creates the **R commit** (version bump to the exact release version) on a
short-lived *local-only* release branch, then tags that commit. The release
branch is deleted afterwards — only the tag survives.

```bash
# Local tag only (safe — nothing is pushed)
./script/release/02-rc-commit.sh --version 10.3.0 --tag 10.3.0-rc1

# Push the tag to origin when you're ready
./script/release/02-rc-commit.sh --version 10.3.0 --tag 10.3.0-rc1 --push

# See what would happen without making changes
./script/release/02-rc-commit.sh --version 10.3.0 --tag 10.3.0-rc1 --dry-run
```

The script refuses to run if there are any uncommitted changes in the working
tree.

---

### `03-build.sh`

Builds the full reactor. JARs are installed to your local `~/.m2` repository.

```bash
# Standard release build (tests skipped)
./script/release/03-build.sh --skip-tests

# With extra Maven options (e.g. parallelism)
./script/release/03-build.sh --skip-tests --maven-opts "-T 4"
```

---

### `04-deploy-to-staging.sh`

Deploys the locally-built JARs to Apache Nexus staging. **Dry run by
default** — the `--deploy` flag is required to actually upload anything.

```bash
# Dry run — prints the Maven command without executing it
./script/release/04-deploy-to-staging.sh --tag 10.3.0-rc1

# Actually deploy to Apache Nexus staging
MAVEN_SETTINGS=/path/to/settings.xml \
MAVEN_GPG_PASSPHRASE=secret \
./script/release/04-deploy-to-staging.sh --tag 10.3.0-rc1 --deploy
```

After deployment, log in to <https://repository.apache.org>, close the
staging repository, and share the URL with the release vote thread on
`dev@kie.apache.org`.

**Required environment variables when `--deploy` is active:**

| Variable | Description |
|---|---|
| `MAVEN_SETTINGS` | Path to a `settings.xml` with Nexus deployer credentials |
| `MAVEN_GPG_PASSPHRASE` | GPG key passphrase for signing release artifacts |

---

### `05-tag-release.sh`

Promotes a winning RC tag to the final release tag (AUTOMATION F).

```bash
# Local tag only
./script/release/05-tag-release.sh --rc-tag 10.3.0-rc2

# Push the release tag to origin
./script/release/05-tag-release.sh --rc-tag 10.3.0-rc2 --push
```

---

### `release-all.sh`

Orchestrates the full RC cycle in one command.

```bash
./script/release/release-all.sh \
    --version 10.3.0 \
    --tag 10.3.0-rc1 \
    [--skip-tests] \
    [--maven-opts "<opts>"] \
    [--deploy] \
    [--staging-url <url>] \
    [--push-tag] \
    [--dry-run]
```

Steps executed in order:

1. `rc-commit.sh` — R commit + tag
2. `build.sh` — full build at the tag commit
3. `deploy-to-staging.sh` — Nexus staging upload *(only if `--deploy`)*

---

## Development stream vs release version

| Situation | Version | Command |
|---|---|---|
| Daily development on `main` | `999-SNAPSHOT` | `update-version.sh 999-SNAPSHOT` |
| Daily development on `10.3.x` | `10.3.999-SNAPSHOT` | `update-version.sh 10.3.999-SNAPSHOT` |
| Creating RC / release | `10.3.0` | Handled automatically by `rc-commit.sh` |

---

## Jenkins integration

Jenkins calls these exact same scripts.  The only difference is that Jenkins
supplies credentials via environment variables:

```groovy
withCredentials([
    file(credentialsId: 'kie-release-settings', variable: 'MAVEN_SETTINGS'),
    string(credentialsId: 'GPG_KEY_PASSPHRASE', variable: 'MAVEN_GPG_PASSPHRASE')
]) {
    sh """
        ./script/release/release-all.sh \
            --version \${RELEASE_VERSION} \
            --tag \${GIT_TAG_NAME} \
            --skip-tests --deploy --push-tag
    """
}
```

---

## Relationship to the 10.2.x multi-repo flow

In 10.2.x the release involved five separate repositories
(drools → optaplanner → kogito-runtimes → kogito-apps → kie-tools), each with
its own Jenkins job.  Now that the first four repos are merged, the steps map
as follows:

| Old step | New equivalent |
|---|---|
| Trigger Drools release job | `./script/release/release-all.sh` |
| Trigger OptaPlanner release job | *(same command — same reactor)* |
| Trigger Kogito Runtimes release job | *(same command — same reactor)* |
| Trigger Kogito Apps release job | *(same command — same reactor)* |
| `git tag 10.x.0 10.x.0-rcN` in all repos | `./script/release/tag-release.sh` |

`kie-tools` remains a separate repository and is unaffected by these scripts.
