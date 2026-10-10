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

# Uploads Drools release binaries and documentation to the Apache file
# management server (people.apache.org) via SSH/rsync.
#
# Called by Jenkinsfile.103xplus.promote (and the original Jenkinsfile.promote)
# inside an sshagent('drools-filemgmt') block, so SSH authentication is already
# in the agent — no credential parameters needed here.
#
# Usage:
#   ./script/release/upload_filemgmt.sh <release-version>
#
# Examples:
#   ./script/release/upload_filemgmt.sh 10.3.0
#
# Environment variables (all have defaults):
#   FILEMGMT_HOST        Remote host (default: people.apache.org)
#   FILEMGMT_BASE_PATH   Remote base path (default: /home/drools/filemgmt)
#   DRY_RUN              Set to 'true' to print commands without running them

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RELEASE_VERSION="${1:-}"

if [[ -z "${RELEASE_VERSION}" ]]; then
    echo "ERROR: release version argument is required."
    echo "Usage: $0 <release-version>"
    echo "  e.g. $0 10.3.0"
    exit 1
fi

FILEMGMT_HOST="${FILEMGMT_HOST:-people.apache.org}"
FILEMGMT_BASE_PATH="${FILEMGMT_BASE_PATH:-/home/drools/filemgmt}"
DRY_RUN="${DRY_RUN:-false}"

# Derive major.minor stream (e.g. 10.3.0 → 10.3)
STREAM="$(echo "${RELEASE_VERSION}" | sed 's/^\([0-9]*\.[0-9]*\)\..*/\1/')"

REMOTE_BINARIES_DIR="${FILEMGMT_BASE_PATH}/downloads_htdocs/drools/release/${STREAM}.x"
REMOTE_DOCS_DIR="${FILEMGMT_BASE_PATH}/docs_htdocs/drools/release/${STREAM}.x"

echo "========================================"
echo "Apache KIE — upload binaries & docs"
echo "Release version    : ${RELEASE_VERSION}"
echo "Stream             : ${STREAM}"
echo "Remote host        : ${FILEMGMT_HOST}"
echo "Remote binaries    : ${REMOTE_BINARIES_DIR}"
echo "Remote docs        : ${REMOTE_DOCS_DIR}"
echo "Dry run            : ${DRY_RUN}"
echo "========================================"

cd "${REPO_ROOT}"

# ── Locate locally-built distribution zips ───────────────────────────────────
# Maven -Dfull produces zip artifacts under each module's target/ directory.
# We upload everything matching the release version.
BINARY_ZIPS=()
while IFS= read -r -d '' f; do
    BINARY_ZIPS+=("${f}")
done < <(find . -path "*/target/*-${RELEASE_VERSION}*.zip" ! -path "*/target/checkout/*" -print0 2>/dev/null)

if [[ ${#BINARY_ZIPS[@]} -eq 0 ]]; then
    echo "WARNING: No distribution zips found for version ${RELEASE_VERSION}."
    echo "         Run 03-build.sh with -Dfull before uploading."
fi

# ── Helper: run or echo ───────────────────────────────────────────────────────
run() {
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo "[DRY RUN] $*"
    else
        "$@"
    fi
}

# ── Create remote directories ─────────────────────────────────────────────────
run ssh "${FILEMGMT_HOST}" "mkdir -p '${REMOTE_BINARIES_DIR}/${RELEASE_VERSION}' '${REMOTE_DOCS_DIR}/${RELEASE_VERSION}'"

# ── Upload distribution zips ──────────────────────────────────────────────────
if [[ ${#BINARY_ZIPS[@]} -gt 0 ]]; then
    echo ""
    echo "--- Uploading ${#BINARY_ZIPS[@]} distribution zip(s) ---"
    for zip in "${BINARY_ZIPS[@]}"; do
        echo "  ${zip}"
        run rsync -avz --progress \
            "${zip}" \
            "${FILEMGMT_HOST}:${REMOTE_BINARIES_DIR}/${RELEASE_VERSION}/"
    done
fi

# ── Upload HTML documentation (if generated) ─────────────────────────────────
DOCS_DIR="target/docs"
if [[ -d "${DOCS_DIR}" ]]; then
    echo ""
    echo "--- Uploading docs from ${DOCS_DIR} ---"
    run rsync -avz --progress --delete \
        "${DOCS_DIR}/" \
        "${FILEMGMT_HOST}:${REMOTE_DOCS_DIR}/${RELEASE_VERSION}/"
else
    echo "(skipping docs upload — ${DOCS_DIR} not found)"
fi

echo ""
echo "========================================"
echo "Upload complete for version ${RELEASE_VERSION}."
echo "========================================"
