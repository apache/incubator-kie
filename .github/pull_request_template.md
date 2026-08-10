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

**Thank you for submitting this pull request**

**NOTE!:** Double-check the target branch for this PR.
The default is `main`.

**Ports** If a forward-port or a backport is needed, paste the forward port PR here

* [link](https://www.example.com)

**Issue**: _(please edit the GitHub Issues link if it exists)_

* [link](https://www.example.com)

**Referenced Pull Requests**: _(please edit the URLs of referenced pull requests if they exist)_

* paste the link(s) from GitHub here
* link 2
* link 3 etc.

<details>
<summary>
How to replicate the CI locally
</summary>

The CI does "simple" maven build(s). All commands can be run from the repository root.

**Quick build of just your changed modules and their dependents** (mirrors what CI does on a PR):

```shell
# 1. Build upstream dependencies of your changes (skip tests for speed)
mvn -T 1C --batch-mode -fae -DskipTests -DskipITs \
    -Denforcer.skip=true -Dcheckstyle.skip=true -Dformatter.skip=true -Darchunit.skip=true \
    -pl <upstream-modules> install

# 2. Build your changed modules and their transitive dependents (with tests)
mvn --batch-mode -fae -Dreproducible \
    -pl <affected-modules> install
```

To compute `<upstream-modules>` and `<affected-modules>` automatically (requires [JBang](https://www.jbang.dev/)):

```shell
jbang script/ci/CiComputeBuildScopes.java \
    <changed-files.txt> \
    <upstream-modules.txt> \
    <affected-modules.txt> \
    <changed-modules.txt>
```

**Full build** (equivalent to what runs on every push to `main`):

```shell
mvn --batch-mode -fae -Dfull -Dreproducible install
```

`-Dfull` additionally builds the distribution modules and generates Javadoc JARs.

**Useful flags:**

| Flag | Effect |
|---|---|
| `-Dquickly` | Skip tests, Checkstyle, formatter, Enforcer, ArchUnit |
| `-DskipTests` | Skip unit tests |
| `-DskipITs` | Skip integration tests |
| `-Denforcer.skip=true` | Skip Enforcer plugin |
| `-Dcheckstyle.skip=true` | Skip Checkstyle |
| `-Dformatter.skip=true` | Skip formatter |
| `-Darchunit.skip=true` | Skip ArchUnit |

</details>

<details>
<summary>
How to retest this PR or trigger a specific build
</summary>

- To **re-run CI**: push a new commit to the PR (an empty commit is enough: `git commit --allow-empty -m "re-trigger CI"`).

</details>
