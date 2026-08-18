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

# Building partially, with `make dev`

This repository has more than 850 Maven modules, so building all of them to
check a change in one is rarely what you want. CI already avoids that on pull
requests — it works out which modules a change can possibly affect and builds
only those (see [PR_CHECKS_AND_CI.md](./PR_CHECKS_AND_CI.md)). `make dev` gives
you the same thing locally, driven by your working copy instead of a pull
request.

It uses the very same scope computation CI runs, so the modules it builds are
the modules CI will build. By default it looks at what you have edited but not
committed, and skips tests — that is the inner loop, and CI is the safety net;
`make dev mvn -- install` runs the tests when you want them.

## What it does

- **Builds only what your change affects.** The same computation CI runs on pull
  requests, applied to your working copy.
- **Optimises for the inner loop.** By default it looks at uncommitted work only,
  skips tests and the reporting plugins, and builds in parallel.
  `make dev mvn -- install` runs the tests when you want them.
- **Rebuilds stale dependencies for you.** Anything missing from `~/.m2`, or
  older than the sources it was built from, is rebuilt first — so switching
  branches or a fresh clone does not silently build against the wrong artifacts.
- **Remembers its settings.** Four lines in `.kie-dev/config.properties`, written on the
  first run and editable by hand.
- **Lets you say what "your changes" means.** Since this branch started, or since
  your last commit — with or without uncommitted work.
- **Shows you before it builds.** `make dev scope` prints the module list and
  builds nothing.
- **Stays out of the way of Maven.** `make dev mvn -- …` passes your command
  straight through, so any goal, profile or flag still works.
- **Caches the expensive part.** The reactor dependency graph is read once and
  reused until a `pom.xml` changes, which is the difference between a two-second
  and a forty-second start.
- **Works from a plain shell.** Your Maven if you have one, the devbox copy if
  you do not.

## Getting started

```bash
make dev
```

That is the whole thing. The first run writes `.kie-dev/config.properties` with sensible
defaults and tells you so; edit that file, or override any setting for a single
run.

The only prerequisites are [JBang](https://www.jbang.dev), Maven and git on your
`PATH` — this repository provides the first two through
[devbox](https://www.jetify.com/devbox), so `devbox shell` gets you all of them.
If one is missing you are told which, rather than left with a stack trace. You do not need to have built the repository first — dependencies that
are missing from `~/.m2`, or that are older than their sources, are rebuilt
automatically. See [Upstream modules](#upstream-modules) below.

The Makefile is dedicated to these partial builds. Full builds are plain
Maven — see [BUILDING.md](./BUILDING.md).

## Cheatsheet

Every knob, in one place.

### Commands

| Command | Does |
| --- | --- |
| `make dev` | Build what your changes affect, using the saved settings |
| `make dev scope` | Print what would be built; build nothing |
| `make dev config` | Show the current settings |
| `make dev mvn -- <mvn args>` | Build them with your own Maven command |
| `make help` | List the targets and summarise the above |

### Settings

Four settings, in precedence order: **command line** → **`.kie-dev/config.properties`** → **default**.

| Setting | Values | Default |
| --- | --- | --- |
| `since` | any git ref | `HEAD` |
| `uncommitted` | `true`, `false` | `true` |
| `breadth` | `changed`, `affected` | `affected` |
| `upstream` | `auto`, `always`, `never` | `auto` |

```bash
make dev since=origin/main   # this run only, not saved
make dev config              # show what is saved
```

<details>
<summary>What each value means</summary>

| `since` | Committed changes are measured from |
| --- | --- |
| `HEAD` | nothing committed — your working tree alone. The default |
| `origin/main` | where this branch was branched from main |
| `HEAD~1` | your last commit |
| any ref | `git merge-base <ref> HEAD` |


| `uncommitted` | The working tree                                |
| ------------- | ----------------------------------------------- |
| `true`        | added on top: `git diff HEAD` + untracked files |
| `false`       | ignored; only committed work counts             |

| `breadth`  | Modules built                                 |
| ---------- | --------------------------------------------- |
| `changed` | the modules whose files changed |
| `affected` | those plus everything transitively downstream — the default, as CI builds |

| `upstream` | Dependencies rebuilt first, with tests skipped                                                        |
| ---------- | ----------------------------------------------------------------------------------------------------- |
| `auto`     | those missing from the local repository or older than their sources, plus anything downstream of them |
| `always`   | all of them                                                                                           |
| `never`    | none; no check is run                                                                                 |

</details>

### Maven arguments

`make dev` builds fast: parallel, fail-at-end, and skipping tests, enforcer,
checkstyle, formatter and ArchUnit. That is the inner loop — CI is what runs the
tests.

When you want something else, `make dev mvn -- <mvn args>` takes the Maven command itself.
Everything after `--` replaces the default entirely:

```bash
make dev mvn -- install                    # the same modules, with tests
make dev mvn -- clean install -Pfull
make dev mvn -- test -Dtest=MyTest
```

`--` is required — without it Make treats `-DskipTests` as one of its own
options. `make dev mvn` with nothing after the `--` is an error rather than a silent
second spelling of `make dev`.

### Config file

`.kie-dev/config.properties`, an ordinary Java properties file, safe to edit by hand:

| Key             | Value                                                          |
| --------------- | -------------------------------------------------------------- |
| `since` | as above |
| `uncommitted` | as above |
| `breadth` | as above |
| `upstream` | as above |

### Environment variables

| Variable                                                                      | Effect                                                                                                         |
| ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `KIE_DEV_SINCE`, `KIE_DEV_UNCOMMITTED`, `KIE_DEV_BREADTH`, `KIE_DEV_UPSTREAM` | What the `since=`/`breadth=`/`uncommitted=`/`upstream=` overrides set |
| `MVN`                                                                         | Maven binary to run. Otherwise `mvn` from `PATH`, then the devbox copy under `.devbox/`                        |
| `MAVEN_OPTS`                                                                  | Passed through to Maven as usual                                                                               |
| `NO_COLOR`                                                                    | Disables coloured output                                                                                       |
| `DEP_GRAPH_EXTRACTOR__JAR`                                                    | Use a prebuilt extractor jar instead of building one                                                           |
| `DEP_GRAPH_EXTRACTOR__EXTRA_MAVEN_ARGS`                                       | Extra args for the graph extraction run, e.g. `-Psome-profile`                                                 |

`DEP_GRAPH_EXTRACTOR__OUTPUT_FILE` and `DEP_GRAPH_EXTRACTOR__REUSE_IF_FRESH`
also exist, but `make dev` sets both itself, so setting them in the
environment has no effect here. They are how CI runs the same script without the
cache — see [PR_CHECKS_AND_CI.md](./PR_CHECKS_AND_CI.md).

### State files

All under `.kie-dev/`, all disposable — delete the directory to reset.

| File                                                   | Contents                                      |
| ------------------------------------------------------ | --------------------------------------------- |
| `config.properties` | your saved settings |
| `dep-graph.tsv` | the reactor dependency graph, cached between runs |
| `dep-graph.tsv.stamp` | fingerprint of every `pom.xml`, which invalidates that graph |
| `changed-files.txt`                                    | changed files from the last run               |
| `modules-to-build.txt`                                 | the modules the last run built                |
| `upstream-modules.txt`                                 | the upstream modules it rebuilt first, if any |
| `last-maven-command.txt`                               | the Maven commands it issued, in full         |
| `pl-changed.txt`, `pl-affected.txt`, `pl-upstream.txt` | the computed scopes from the last run         |

Module lists and Maven commands are printed as a count rather than in full — a
`-pl` argument with several hundred coordinates in it is not something anyone
reads. The files above are where the real thing lives, for when you need to
copy, paste or debug it.

### Tests

```bash
jbang script/dev/tests/DevTest.java
jbang script/dev/tests/MakefileTest.java
```

## Upstream modules

A partial build can only resolve dependencies that are already installed in
`~/.m2`, and it only gives correct results if what is installed was built from
the sources you have now. Both go wrong routinely: the first time you use the
repository nothing is installed at all, and after switching branches or pulling,
what is installed no longer matches your working copy.

So before the main build, the tool works out which of its dependencies cannot be
trusted, and rebuilds exactly those with tests skipped:

- **not installed** — no artifact in the local Maven repository;
- **older than its sources** — the installed artifact predates a file in the
  module it was built from;
- **downstream of either** — a module whose own dependency is being rebuilt.

Typically nothing needs rebuilding and the check costs a second or so:

```
all 10 upstream modules are installed and up to date — nothing to rebuild
```

Otherwise it says what it is about to do, and why:

```
Upstream modules to rebuild first: 82  (.kie-dev/upstream-modules.txt)
    5 older than their sources, 77 downstream of those
```

This is controlled by `upstream`, which defaults to `auto` because `auto` is
almost always what you want:

| `upstream` | Behaviour                                                              |
| ---------- | ---------------------------------------------------------------------- |
| `auto`     | Rebuild only dependencies that are missing or out of date. The default |
| `always`   | Rebuild every dependency, like CI does                                 |
| `never`    | Trust `~/.m2` as it is, and skip the check                             |

```bash
make dev upstream=always   # when in doubt
make dev upstream=never    # when you know ~/.m2 is good and want the seconds back
```

Staleness is judged by file modification times, which is why `never` exists: if
something touches files without changing them, everything downstream will look
stale. Note also that this only reasons about modules *in this repository* —
dependencies from other repositories are ordinary Maven artifacts, and it is up
to you to have the right versions installed.

## How it works

1. `make dev` runs [`script/dev/Dev.java`](../script/dev/Dev.java), a single
   JBang script.
2. It turns `since` and `uncommitted` into a list of changed files, using
   `git diff` and `git ls-files`.
3. It hands that list to
   [`script/ci/CiComputeBuildScopes.java`](../script/ci/CiComputeBuildScopes.java) —
   the same script CI uses, pulled in directly as a JBang `//SOURCES`
   dependency rather than copied. It maps each changed file to its nearest
   reactor module and walks the dependency graph
   ([`DepGraph`](../script/ci/DepGraph.java), also shared) to produce three
   disjoint sets: `changed`, `affected` and `upstream`.
4. Your chosen breadth selects from those sets, and the result becomes a Maven
   `-pl` argument.
5. Separately, it walks the dependency graph *upwards* from what it is about to
   build, checks each of those modules against the local Maven repository, and
   rebuilds the ones that are missing or out of date in a first pass.

Step 5 does not reuse the `upstream` set from step 3. That set is relative to
the affected modules, whereas a `changed` build can need modules that sit inside
`affected` — everything is downstream of the root pom, including modules that
the root pom's own children depend on.

Reading the dependency graph means reading every `pom.xml` in the reactor, which
is too slow to repeat on every build. Locally the graph is therefore kept in
`.kie-dev/dep-graph.tsv` and reused until any `pom.xml` is added, removed or
edited — which takes a typical run from tens of seconds down to about two. CI
always computes the graph from scratch instead.

This is only about the dependency graph. It is unrelated to your local Maven
repository (`~/.m2`), which is what the [upstream modules](#upstream-modules)
check looks at.

## Troubleshooting

**"Nothing has changed since that point"** — nothing to build. If you have
committed your work and `since=HEAD`, switch to `since=origin/main`.

**"No reactor module matched the changed files"** — you only changed files that
live outside any Maven module, such as documentation or workflow files.

**"Cannot find a merge base"** — the recorded base is not present locally. Fetch
it (`git fetch origin main`), or point `since` at a ref you have.

**The build is far bigger than expected, and the base is a local branch** — it
has probably gone stale. `git fetch`, then either pull it or set
`since=origin/<branch>`.

**Maven cannot resolve a module of this repository** — the dependency check
believed it was installed and current. Force the issue with
`make dev upstream=always`, and please report it.

**It keeps rebuilding dependencies that look fine** — staleness is judged by
file modification times, so anything that rewrites files without changing their
contents will trigger it. Use `upstream=never` to skip the check.

**The build is far bigger than expected** — you have probably touched a parent
pom or a hub module, so almost everything is downstream of it. Confirm with
`make dev scope`, and use `breadth=changed` for a tighter loop.

## Tests

The script and the Makefile are covered by two suites. Run them if you change
[`script/dev/`](../script/dev/) or the [Makefile](../Makefile):

```bash
jbang script/dev/tests/DevTest.java
jbang script/dev/tests/MakefileTest.java
```

Both are hermetic and take a few seconds — they build throwaway git repositories
and dependency graphs in temp directories, and never build the reactor.

CI runs those in [dev-tests.yaml](../.github/workflows/dev-tests.yaml), along
with a job that runs `make dev` for real in a fresh clone.

## Related

- [BUILDING.md](./BUILDING.md) — building the whole repository, Maven flags and profiles
- [PR_CHECKS_AND_CI.md](./PR_CHECKS_AND_CI.md) — how CI uses the same scope computation
- [`script/dev/`](../script/dev/) and [`script/ci/`](../script/ci/) — the scripts themselves
