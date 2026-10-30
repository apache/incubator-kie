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

# Branch cut (Automation A): creates the X.Y.x development stream branch from
# the base branch (default: main) and moves it to the X.Y.999-SNAPSHOT dev
# version (the "D commit").  Run ONCE per minor version, BEFORE the first RC.
#
#   main  ──── c ──── c ──── c ───────────────────────────>  999-SNAPSHOT
#                            └── [D commit] ──── c ────>  X.Y.x (X.Y.999-SNAPSHOT)
#
# Usage:
#   ./script/release/00-create-stream-branch.sh <version> [--base <branch>] [--push] [--dry-run]
#   ./script/release/00-create-stream-branch.sh --version <version> [--base <branch>] [--push] [--dry-run]
#
# Examples:
#   ./script/release/00-create-stream-branch.sh 10.3.0 --push
#   ./script/release/00-create-stream-branch.sh --version 10.3 --base main --dry-run
#
# Options:
#   <version> | --version <ver>   Release version (10.3.0) or minor (10.3).  Only major.minor is used.
#   --base <branch>               Branch to cut from (default: main)
#   --remote <name>               Git remote (default: origin)
#   --push                        Push the new branch to the remote (default: local only)
#   --dry-run                     Print what would happen without making any changes
#
# After this script the working tree is left checked out on X.Y.x.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

VERSION=""
BASE_BRANCH="main"
REMOTE="origin"
PUSH=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --version)  VERSION="${2:-}"; shift 2 ;;
        --base)     BASE_BRANCH="${2:-}"; shift 2 ;;
        --remote)   REMOTE="${2:-}"; shift 2 ;;
        --push)     PUSH=true; shift ;;
        --dry-run)  DRY_RUN=true; shift ;;
        *)
            if [[ -z "${VERSION}" && ! "$1" =~ ^- ]]; then
                VERSION="$1"
                shift
            else
                echo "Unknown option: $1"
                echo "Usage: $0 [<version> | --version <version>] [--base <branch>] [--push] [--dry-run]"
                exit 1
            fi
            ;;
    esac
done

if [[ -z "${VERSION}" ]]; then
    echo "ERROR: Version is required."
    echo "Usage: $0 10.3.0 [--base main] [--push] [--dry-run]"
    exit 1
fi

if [[ ! "${VERSION}" =~ ^([0-9]+)\.([0-9]+)(\.[0-9A-Za-z.-]+)?$ ]]; then
    echo "ERROR: Cannot parse version '${VERSION}'. Expected e.g. 10.3.0 or 10.3"
    exit 1
fi

MAJOR_MINOR="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
STREAM_BRANCH="${MAJOR_MINOR}.x"
DEV_VERSION="${MAJOR_MINOR}.999-SNAPSHOT"

cd "${REPO_ROOT}"

echo "========================================"
echo "Apache KIE repo — create stream branch"
echo "Stream branch   : ${STREAM_BRANCH}"
echo "Cut from        : ${REMOTE}/${BASE_BRANCH}"
echo "Dev version     : ${DEV_VERSION}"
echo "Push            : ${PUSH}"
echo "Dry run         : ${DRY_RUN}"
echo "========================================"

# ── Guards ───────────────────────────────────────────────────────────────────
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: There are uncommitted changes in the working tree."
    exit 1
fi
if [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    echo "ERROR: There are untracked files in the working tree:"
    git ls-files --others --exclude-standard | sed 's/^/  /'
    exit 1
fi

if git ls-remote --exit-code --heads "${REMOTE}" "${STREAM_BRANCH}" &>/dev/null; then
    echo "ERROR: Branch '${STREAM_BRANCH}' already exists on '${REMOTE}'."
    echo "       The branch cut is done once per minor version. For rc2 / patch"
    echo "       releases reuse the existing branch and skip this step."
    exit 1
fi

if git rev-parse --verify "${STREAM_BRANCH}" &>/dev/null; then
    echo "ERROR: Local branch '${STREAM_BRANCH}' already exists. Delete it first:"
    echo "  git branch -D ${STREAM_BRANCH}"
    exit 1
fi

if [[ "${DRY_RUN}" == "true" ]]; then
    echo ""
    echo "[DRY RUN] Would execute:"
    echo "  git fetch ${REMOTE} +refs/heads/${BASE_BRANCH}:refs/remotes/${REMOTE}/${BASE_BRANCH}"
    echo "  git checkout -b ${STREAM_BRANCH} ${REMOTE}/${BASE_BRANCH}"
    echo "  ./script/release/01-update-version.sh ${DEV_VERSION}"
    echo "  git add -A"
    echo "  git commit -m \"Update version to ${DEV_VERSION}\""
    if [[ "${PUSH}" == "true" ]]; then
        echo "  git push ${REMOTE} ${STREAM_BRANCH}"
    fi
    exit 0
fi

# ── Branch cut ───────────────────────────────────────────────────────────────
echo ""
echo "--- Creating ${STREAM_BRANCH} from ${REMOTE}/${BASE_BRANCH} ---"
git fetch "${REMOTE}" "+refs/heads/${BASE_BRANCH}:refs/remotes/${REMOTE}/${BASE_BRANCH}"
git checkout -b "${STREAM_BRANCH}" "${REMOTE}/${BASE_BRANCH}"

echo ""
echo "--- Updating version to ${DEV_VERSION} ---"
"${SCRIPT_DIR}/01-update-version.sh" "${DEV_VERSION}"

echo ""
echo "--- Committing D commit ---"
git add -A
git commit -m "Update version to ${DEV_VERSION}"

if [[ "${PUSH}" == "true" ]]; then
    echo ""
    echo "--- Pushing ${STREAM_BRANCH} to ${REMOTE} ---"
    git push "${REMOTE}" "${STREAM_BRANCH}"
fi

echo ""
echo "========================================"
echo "Stream branch ${STREAM_BRANCH} ready (${DEV_VERSION})."
echo ""
if [[ "${PUSH}" != "true" ]]; then
    echo "Next step (not pushed yet):"
    echo "  git push ${REMOTE} ${STREAM_BRANCH}"
    echo ""
fi
echo "Next: run the RC release, e.g."
echo "  ./script/release/release-all.sh --version ${MAJOR_MINOR}.0 --tag ${MAJOR_MINOR}.0-rc1 --deploy --push-tag"
echo "========================================"
