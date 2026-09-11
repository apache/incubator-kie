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

# CiSummary snapshot scenarios

Each subdirectory is a self-contained fixture for `CiSummaryTest`.

Layout:

```
<scenario>/
  env.properties        KEY=VALUE lines, passed as env vars to CiSummary.
                        Common keys: MAVEN_PL_UPSTREAM, MAVEN_PL_AFFECTED,
                        MAVEN_PL_CHANGED, MAVEN_PL_IMAGE_PRODUCERS,
                        CI_PARALLEL_BUILD, MERMAID_EXPANDED, MATRIX_OS, MATRIX_JAVA.
  graph.tsv             (optional) Dep-graph TSV. The token {ROOT} is replaced with
                        the absolute path to this scenario's root/ before being passed
                        to CiSummary via DEP_GRAPH_EXTRACTOR__OUTPUT_FILE.
  root/                 Acts as CI_REPO_ROOT. Place surefire/failsafe XML reports
                        under <module>/target/{surefire,failsafe}-reports/.
  expected-summary.md   Golden stdout of CiSummary (with [CI] log lines stripped).
```

Regenerate goldens after an intentional output change:

```bash
CI_UPDATE_GOLDEN=1 jbang script/ci/tests/CiSummaryTest.java
```
