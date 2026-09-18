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

# Creates the "R commit" (release commit) and the corresponding RC git tag.
#
# The R commit contains only the version bump to the exact release version.
# It is made on a short-lived local release branch that is never pushed — only
# the tag is pushed.  This mirrors the branch strategy from release.txt:
#
#   10.2.x branch  ──── c ──── c ──── [D commit] ──── c ────> ...
#                                         |
#                                 (local release branch)
#                                         └── [R commit] ──> tag: 10.3.0-rc1
#
# Usage:
#   ./script/release/rc-commit.sh --version <release-version> --tag <tag-name>
#
# Examples:
#   ./script/release/rc-commit.sh --version 10.3.0 --tag 10.3.0-rc1
#   ./script/release/rc-commit.sh --version 10.3.0 --tag 10.3.0-rc2
#
# Options:
#   --version <ver>    Exact release version, e.g. 10.3.0
#   --tag <tag>        Git tag name, e.g. 10.3.0-rc1
#   --push             Push the tag to origin (default: local only)
#   --dry-run          Print what would happen without making any changes

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RELEASE_VERSION=""
TAG_NAME=""
PUSH=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --version)
            RELEASE_VERSION="${2:-}"
            shift 2
            ;;
        --tag)
            TAG_NAME="${2:-}"
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
            echo "Usage: $0 --version <version> --tag <tag> [--push] [--dry-run]"
            exit 1
            ;;
    esac
done

if [[ -z "${RELEASE_VERSION}" || -z "${TAG_NAME}" ]]; then
    echo "ERROR: --version and --tag are both required."
    echo "Usage: $0 --version 10.3.0 --tag 10.3.0-rc1"
    exit 1
fi

# Derive the expected development branch name (e.g. 10.3.0 → 10.3.x).
STREAM_BRANCH="$(echo "${RELEASE_VERSION}" | sed 's/^\([0-9]*\.[0-9]*\)\..*/\1.x/')"
RELEASE_BRANCH="release/${TAG_NAME}"

cd "${REPO_ROOT}"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

echo "========================================"
echo "Drools unified repo — RC commit + tag"
echo "Release version : ${RELEASE_VERSION}"
echo "Tag             : ${TAG_NAME}"
echo "Release branch  : ${RELEASE_BRANCH} (local only)"
echo "Push tag        : ${PUSH}"
echo "Dry run         : ${DRY_RUN}"
echo "Current branch  : ${CURRENT_BRANCH}"
echo "========================================"

# Warn if we're not on the expected stream branch (not a fatal error — someone
# may legitimately be on a different commit).
if [[ "${CURRENT_BRANCH}" != "${STREAM_BRANCH}" ]]; then
    echo ""
    echo "WARNING: Current branch '${CURRENT_BRANCH}' is not the expected stream"
    echo "         branch '${STREAM_BRANCH}'. Proceed with caution."
    echo ""
fi

# Check there are no uncommitted changes before we do anything.
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: There are uncommitted changes in the working tree."
    echo "       Commit or stash them before running this script."
    exit 1
fi

if [[ "${DRY_RUN}" == "true" ]]; then
    echo ""
    echo "[DRY RUN] Would execute:"
    echo "  git checkout -b ${RELEASE_BRANCH}"
    echo "  ./script/release/update-version.sh ${RELEASE_VERSION}"
    echo "  git add -A"
    echo "  git commit -m \"chore: release ${RELEASE_VERSION}\""
    echo "  git tag -a ${TAG_NAME} -m \"Release ${TAG_NAME}\""
    if [[ "${PUSH}" == "true" ]]; then
        echo "  git push origin ${TAG_NAME}"
    fi
    echo "  git checkout ${CURRENT_BRANCH}"
    echo "  git branch -D ${RELEASE_BRANCH}"
    exit 0
fi

# ── Guard: stale release branch from a previous interrupted run ──────────────
if git rev-parse --verify "${RELEASE_BRANCH}" &>/dev/null; then
    echo ""
    echo "ERROR: Branch '${RELEASE_BRANCH}' already exists from a previous"
    echo "       (possibly interrupted) run."
    echo ""
    echo "To clean up and start fresh:"
    echo "  git checkout ${CURRENT_BRANCH}"
    echo "  git branch -D ${RELEASE_BRANCH}"
    if git rev-parse --verify "${TAG_NAME}" &>/dev/null; then
        echo "  git tag -d ${TAG_NAME}"
    fi
    exit 1
fi

# ── Guard: tag already exists ─────────────────────────────────────────────────
if git rev-parse --verify "${TAG_NAME}" &>/dev/null; then
    echo ""
    echo "ERROR: Tag '${TAG_NAME}' already exists."
    echo "       Delete it first if you want to recreate it:"
    echo "  git tag -d ${TAG_NAME}"
    exit 1
fi

echo ""
echo "--- Creating local release branch: ${RELEASE_BRANCH} ---"
git checkout -b "${RELEASE_BRANCH}"

echo ""
echo "--- Updating version to ${RELEASE_VERSION} ---"
"${SCRIPT_DIR}/update-version.sh" "${RELEASE_VERSION}"

echo ""
echo "--- Committing R commit ---"
git add -A
git commit -m "chore: release ${RELEASE_VERSION}"

echo ""
echo "--- Tagging ${TAG_NAME} ---"
git tag -a "${TAG_NAME}" -m "Release ${TAG_NAME}"

if [[ "${PUSH}" == "true" ]]; then
    echo ""
    echo "--- Pushing tag ${TAG_NAME} to origin ---"
    git push origin "${TAG_NAME}"
fi

echo ""
echo "--- Returning to ${CURRENT_BRANCH} ---"
git checkout "${CURRENT_BRANCH}"

echo ""
echo "--- Deleting local release branch ${RELEASE_BRANCH} ---"
git branch -D "${RELEASE_BRANCH}"

echo ""
echo "========================================"
echo "RC commit + tag complete."
echo ""
echo "Tag '${TAG_NAME}' points to the R commit (version ${RELEASE_VERSION})."
if [[ "${PUSH}" == "false" ]]; then
    echo ""
    echo "Tag is LOCAL only. To push when ready:"
    echo "  git push origin ${TAG_NAME}"
fi
echo ""
echo "Next steps:"
echo "  ./script/release/build.sh --skip-tests"
echo "  ./script/release/deploy-to-staging.sh --tag ${TAG_NAME}"
echo "========================================"
