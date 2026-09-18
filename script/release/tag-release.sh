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

# Promotes an approved RC tag to the final release tag (AUTOMATION F).
#
# After the community vote passes on an RC (e.g. 10.3.0-rc2), this script
# creates the final release tag pointing to the same commit:
#
#   git tag 10.3.0 10.3.0-rc2
#
# By default the tag is created locally only.  Pass --push to publish it.
#
# Usage:
#   ./script/release/tag-release.sh --rc-tag <rc-tag> [--push] [--dry-run]
#
# Examples:
#   ./script/release/tag-release.sh --rc-tag 10.3.0-rc2
#   ./script/release/tag-release.sh --rc-tag 10.3.0-rc2 --push

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RC_TAG=""
PUSH=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --rc-tag)
            RC_TAG="${2:-}"
            shift 2
            ;;
        --push)
            PUSH=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --rc-tag <rc-tag> [--push] [--dry-run]"
            exit 1
            ;;
    esac
done

if [[ -z "${RC_TAG}" ]]; then
    echo "ERROR: --rc-tag is required."
    echo "Usage: $0 --rc-tag 10.3.0-rc2 [--push]"
    exit 1
fi

# Derive the final release tag (strip -rcN suffix).
RELEASE_TAG="$(echo "${RC_TAG}" | sed 's/-rc[0-9]*$//')"

cd "${REPO_ROOT}"

# Verify the RC tag exists.
if ! git rev-parse "${RC_TAG}" &>/dev/null; then
    echo "ERROR: RC tag '${RC_TAG}' not found."
    echo "       Make sure you have fetched the tag: git fetch origin ${RC_TAG}"
    exit 1
fi

RC_COMMIT="$(git rev-parse "${RC_TAG}")"

echo "========================================"
echo "Drools unified repo — tag release"
echo "RC tag       : ${RC_TAG} (${RC_COMMIT:0:12})"
echo "Release tag  : ${RELEASE_TAG}"
echo "Push         : ${PUSH}"
echo "Dry run      : ${DRY_RUN}"
echo "========================================"

if [[ "${DRY_RUN}" == "true" ]]; then
    echo ""
    echo "[DRY RUN] Would execute:"
    echo "  git tag -a ${RELEASE_TAG} ${RC_TAG} -m \"Release ${RELEASE_TAG}\""
    if [[ "${PUSH}" == "true" ]]; then
        echo "  git push origin ${RELEASE_TAG}"
    fi
    exit 0
fi

echo ""
echo "--- Creating release tag ${RELEASE_TAG} at ${RC_TAG} ---"
git tag -a "${RELEASE_TAG}" "${RC_TAG}" -m "Release ${RELEASE_TAG}"

if [[ "${PUSH}" == "true" ]]; then
    echo ""
    echo "--- Pushing release tag ${RELEASE_TAG} to origin ---"
    git push origin "${RELEASE_TAG}"
fi

echo ""
echo "========================================"
echo "Release tag complete: ${RELEASE_TAG}"
if [[ "${PUSH}" == "false" ]]; then
    echo ""
    echo "Tag is LOCAL only. To push when ready:"
    echo "  git push origin ${RELEASE_TAG}"
fi
echo ""
echo "Next steps (AUTOMATION G — promote artifacts):"
echo "  1. Promote the Nexus staging repository to release."
echo "  2. Run svn mv to move dist artifacts from dev/ to release/."
echo "  3. Announce the release on dev@kie.apache.org."
echo "========================================"
