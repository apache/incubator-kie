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

# 10 — Plugin `<dependency>` counts as a reactor dependency

**Input:** any change inside
`drools-test-coverage/test-integration-prebuilt/test-integration-prebuilt-tests` (`org.drools.testcoverage:test-integration-prebuilt-tests`).

**What this tests:** that a `<dependency>` declared *inside a build
plugin* (`maven-surefire-plugin` → `<dependencies>` → `org.kie:kie-maven-plugin`)
is treated as a real reactor edge by the dep-graph extractor — i.e. it
shows up in `upstream` for the consuming module exactly as a regular
`<dependency>` would.

**Expected structure:**
- `changed`  = `{org.drools.testcoverage:test-integration-prebuilt-tests}`
- `affected` = `test-integration-prebuilt-tests` + its reactor downstreams
- `upstream` MUST contain `org.kie:kie-maven-plugin` (and its transitive
  reactor deps), proving the plugin-level dependency was followed

**Why snapshot:** if the extractor stops walking plugin-level
`<dependencies>`, `kie-maven-plugin` silently drops out of `upstream`
and the wrong subset of the reactor gets rebuilt. The test breaks
loudly so the regression can't slip through. Regenerate with
`CI_UPDATE_GOLDEN=1`.
