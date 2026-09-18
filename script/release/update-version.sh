#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

set -euo pipefail

# Updates the Maven version across the unified drools repo (drools + optaplanner +
# kogito-runtimes + kogito-apps are all modules of the same root POM since the
# 10.3.x consolidation).
#
# Usage:
#   ./script/release/update-version.sh <version>
#
# Examples:
#   ./script/release/update-version.sh 10.3.999-SNAPSHOT   # dev/stream version
#   ./script/release/update-version.sh 10.3.0              # release version
#
# The script also updates the data-index ephemeral image tag property that lives
# inside kogito-quarkus (was a separate step in the old multi-repo flow).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

NEW_VERSION="${1:-}"

if [[ -z "${NEW_VERSION}" ]]; then
    echo "Usage: $0 <version>"
    echo "  e.g. $0 10.3.999-SNAPSHOT"
    echo "  e.g. $0 10.3.0"
    exit 1
fi

# Derive the stream name (e.g. 10.3.0 → 10.3.x, 10.3.999-SNAPSHOT → 10.3.x).
# Used for the data-index image tag property.
STREAM_NAME="$(echo "${NEW_VERSION}" | sed 's/^\([0-9]*\.[0-9]*\)\..*/\1.x/')"

# Detect the current version from the root POM so we can pass -DoldVersion,
# which skips the full reactor scan and is much faster on a large multi-module
# repo like this one.
CURRENT_VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout -f "${REPO_ROOT}/pom.xml" 2>/dev/null)"

echo "========================================"
echo "Drools unified repo — version update"
echo "Current version : ${CURRENT_VERSION}"
echo "New version     : ${NEW_VERSION}"
echo "Stream name     : ${STREAM_NAME}"
echo "========================================"

cd "${REPO_ROOT}"

echo ""
echo "--- Updating all Maven module versions ---"
mvn versions:set \
    -DoldVersion="${CURRENT_VERSION}" \
    -DnewVersion="${NEW_VERSION}" \
    -DprocessAllModules \
    -DgenerateBackupPoms=false

# The data-index ephemeral image tag property is controlled via a dedicated
# property inside kogito-quarkus-workflow-common-deployment.  Update it to
# track the stream name so nightly image pulls resolve correctly.
DATA_INDEX_MODULE=":kogito-quarkus-workflow-common-deployment"
if mvn -q help:evaluate -Dexpression=project.artifactId -pl "${DATA_INDEX_MODULE}" 2>/dev/null; then
    echo ""
    echo "--- Updating data-index ephemeral image tag (${STREAM_NAME}) ---"
    mvn -pl "${DATA_INDEX_MODULE}" versions:set-property \
        -Dproperty=data-index-ephemeral.image.tagVersion \
        -DnewVersion="${STREAM_NAME}" \
        -DgenerateBackupPoms=false
else
    echo "(skipping data-index image tag update — module not found)"
fi

echo ""
echo "========================================"
echo "Version update complete: ${NEW_VERSION}"
echo "========================================"
echo ""
echo "Next steps:"
echo "  git diff          # review changes"
echo "  git add -A && git commit -m \"chore: update version to ${NEW_VERSION}\""
