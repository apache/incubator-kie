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

# dep-graph-extractor

A small Maven extension that dumps the reactor's module dependency graph to a TSV
file, then aborts the build before any goals run.

Used by [`CiComputeBuildScopes`](../CiComputeBuildScopes.java) to figure out, from a
list of changed files, which modules need to be rebuilt and retested.

## What it writes

One file at the path given by `-DdepGraphExtractor.out=<path>` (default `dep-graph.tsv`),
with three record kinds:

```
P    groupId:artifactId    /abs/path/to/module                   # one per reactor module
D    groupId:artifactId    upstream-groupId:artifactId    scope  # one per direct dep edge
B    groupId:artifactId                                          # marks an in-reactor BOM
```

> Separators in the TSV are \<TAB> entries.

- `P` records list every module in the reactor along with its absolute basedir on disk.
Consumers use them to map `groupId:artifactId` back to a path so changed files can be
attributed to the module they live under.

- `D` edges include both regular `<dependency>` edges (via Maven's `ProjectDependencyGraph`)
and `<scope>import</scope>` BOM edges from `<dependencyManagement>` — the latter are
invisible to the standard graph but matter for change propagation, so we read them off
the original POM model and emit them too.

- The 4th field on a `D` record says **why** the edge exists: a Maven dependency scope
(`compile`, `test`, `provided`, `runtime`, `system`), or `parent` when the only link is
the parent chain, or `plugin` when it comes from a plugin or extension, or `import` for a
BOM import. A declared dependency wins over the parent chain. This does not change which
edges are emitted, and build scoping ignores it — a test-scope edge still forces a
rebuild. It exists so tooling can tell what a module *ships* against from what it only
*tests* against (for example, to separate test-only modules from production ones).

- `B` records call out which of the modules listed in `P` are in-reactor BOMs (a module
that other modules import via `<scope>import</scope>`). Downstream tooling like
`CiSummary` uses this to render BOM modules differently from regular modules.

## Why it aborts

The extension's only job is to capture the graph; once the TSV is written, running the
actual build would be wasted work — so it calls `System.exit(0)` from
`afterProjectsRead`.

## Tests

There are no tests in this directory. The extension's behavior is exercised end-to-end
by [`CiComputeBuildScopesTest`](../tests/CiComputeBuildScopesTest.java), which builds
the extension, runs it against the real reactor, and snapshot-tests the resulting
`changed` / `affected` / `upstream` lists.
