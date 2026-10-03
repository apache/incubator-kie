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

# Apache KIE Release Guide

The canonical release documentation and single source of truth for the Apache KIE release procedure is maintained on the Apache KIE community website:

👉 **[Apache KIE Release Procedure](https://kie.apache.org/community/release-procedure)** (hosted in the [`apache/incubator-kie-website`](https://github.com/apache/incubator-kie-website) repository under `docs/community/devs/release-procedure.md`).

---

## Repository-Specific Release Tooling

This repository (`incubator-kie`) provides local-first release scripts located in [`script/release/`](../script/release/):

- **[`script/release/README.md`](../script/release/README.md)**: Detailed documentation of all scripts, lifecycle definitions, and workflows.
- **`01-update-version.sh`**: (Automation A / D) Updates reactor POM versions across all modules.
- **`02-rc-commit.sh`**: (Automation D.1) Generates the temporary release branch, updates version to release version, tags the RC commit, and deletes the temporary branch.
- **`03-build.sh`**: (Automation D.2) Builds the full Maven reactor and installs artifacts to local `~/.m2/repository`.
- **`04-deploy-to-staging.sh`**: (Automation D.3) Deploys signed artifacts to the Apache Nexus staging repository.
- **`05-tag-release.sh`**: (Automation F) Promotes an approved RC tag to the final release tag after a successful VOTE.
- **`release-all.sh`**: Master orchestration script executing the RC workflow (`02` → `03` → `04`) in sequence.

### Common Commands:
```bash
# 1. Update version across reactor modules (Automation A or D)
./script/release/01-update-version.sh 10.3.999-SNAPSHOT   # stream snapshot
./script/release/01-update-version.sh 10.3.0              # exact release version

# 2. Master RC workflow (aligned CLI: accepts positional or --version flag, --tag, --deploy, --push-tag)
./script/release/release-all.sh 10.3.0 --rc --skip-tests
./script/release/release-all.sh --version 10.3.0 --tag 10.3.0-rc1 --skip-tests --deploy --push-tag

# 3. Promote approved RC tag to final release tag (Automation F)
./script/release/05-tag-release.sh --rc-tag 10.3.0-rc1 --push
```

For Jenkins pipeline jobs, see `.ci/jenkins/Jenkinsfile.103xplus.*`.

