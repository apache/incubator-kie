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

# Master orchestrator for the local-first release workflow.
#
# Runs the individual release scripts in the correct order for a full release
# candidate cycle (02-rc-commit.sh -> 03-build.sh -> 04-deploy-to-staging.sh).
# Each step can also be run independently — see script/release/README.md.
#
# Usage:
#   ./script/release/release-all.sh <version> [--tag <tag>] [OPTIONS]
#   ./script/release/release-all.sh --version <version> [--tag <tag>] [OPTIONS]
#
# Required (one of):
#   <version> or --version <ver>   Exact release version, e.g. 10.3.0
#
# Optional tags:
#   --tag <tag> | --rc-tag <tag>   RC tag name, e.g. 10.3.0-rc1 (defaults to <version>-rc1)
#   --rc                           Explicit flag indicating RC mode
#
# Optional build flags:
#   --skip-tests | --skip-build    Skip tests during the build
#   --maven-opts <opts>            Extra Maven options forwarded to build.sh
#
# Optional deploy/publish flags:
#   --deploy | --publish           Deploy JARs to Nexus staging after the build
#   --staging-url <url>            Nexus staging URL (default: Apache Nexus)
#
# Optional git flags:
#   --push | --push-tag            Push the RC tag to origin after creating it
#
# Other:
#   --dry-run                      Print what would happen without executing anything
#
# Examples:
#   # Positional or flag version
#   ./script/release/release-all.sh 10.3.0 --rc --skip-tests
#   ./script/release/release-all.sh --version 10.3.0 --tag 10.3.0-rc1 --skip-tests
#
#   # Full RC with deploy to Apache Nexus staging and push tag to origin
#   ./script/release/release-all.sh 10.3.0 --tag 10.3.0-rc1 --skip-tests --deploy --push-tag

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RELEASE_VERSION=""
TAG_NAME=""
SKIP_TESTS=false
EXTRA_MVN_OPTS=""
DEPLOY=false
STAGING_URL=""
PUSH_TAG=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --version)       RELEASE_VERSION="${2:-}"; shift 2 ;;
        --tag|--rc-tag)  TAG_NAME="${2:-}"; shift 2 ;;
        --rc)            shift ;; # Accepted for parity with tools repo
        --skip-tests|--skip-build) SKIP_TESTS=true; shift ;;
        --maven-opts)    EXTRA_MVN_OPTS="${2:-}"; shift 2 ;;
        --deploy|--publish) DEPLOY=true; shift ;;
        --staging-url)   STAGING_URL="${2:-}"; shift 2 ;;
        --push|--push-tag) PUSH_TAG=true; shift ;;
        --dry-run)       DRY_RUN=true; shift ;;
        *)
            if [[ -z "${RELEASE_VERSION}" && ! "$1" =~ ^- ]]; then
                RELEASE_VERSION="$1"
                shift
            else
                echo "Unknown option: $1"
                echo "Usage: $0 [<version> | --version <version>] [--tag <tag>] [OPTIONS]"
                exit 1
            fi
            ;;
    esac
done

if [[ -z "${RELEASE_VERSION}" ]]; then
    echo "ERROR: Version is required."
    echo "Usage: $0 10.3.0 [--tag 10.3.0-rc1] [OPTIONS]"
    exit 1
fi

if [[ -z "${TAG_NAME}" ]]; then
    TAG_NAME="${RELEASE_VERSION}-rc1"
fi

cd "${REPO_ROOT}"

echo ""
echo "=========================================="
echo "Apache KIE repo — release-all"
echo "Version         : ${RELEASE_VERSION}"
echo "RC tag          : ${TAG_NAME}"
echo "Skip tests      : ${SKIP_TESTS}"
echo "Deploy staging  : ${DEPLOY}"
echo "Push tag        : ${PUSH_TAG}"
echo "Dry run         : ${DRY_RUN}"
echo "=========================================="
echo ""

# ── Helper ───────────────────────────────────────────────────────────────────

run_step() {
    local step_name="$1"
    shift
    echo ""
    echo "────────────────────────────────────────"
    echo "STEP: ${step_name}"
    echo "────────────────────────────────────────"
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo "[DRY RUN] $*"
    else
        "$@"
    fi
    echo "✅  ${step_name} — done"
}

# ── STEP 1 (Automation D.1): R commit + RC tag ───────────────────────────────

RC_COMMIT_SCRIPT="${SCRIPT_DIR}/02-rc-commit.sh"
[[ ! -f "${RC_COMMIT_SCRIPT}" ]] && RC_COMMIT_SCRIPT="${SCRIPT_DIR}/rc-commit.sh"

RC_COMMIT_ARGS=("${RC_COMMIT_SCRIPT}" "--version" "${RELEASE_VERSION}" "--tag" "${TAG_NAME}")
[[ "${PUSH_TAG}" == "true" ]] && RC_COMMIT_ARGS+=("--push")
[[ "${DRY_RUN}" == "true" ]]  && RC_COMMIT_ARGS+=("--dry-run")

run_step "R commit + RC tag (02-rc-commit.sh)" "${RC_COMMIT_ARGS[@]}"

# ── STEP 2 (Automation D.2): Build ───────────────────────────────────────────

BUILD_SCRIPT="${SCRIPT_DIR}/03-build.sh"
[[ ! -f "${BUILD_SCRIPT}" ]] && BUILD_SCRIPT="${SCRIPT_DIR}/build.sh"

BUILD_ARGS=("${BUILD_SCRIPT}")
[[ "${SKIP_TESTS}" == "true" ]]       && BUILD_ARGS+=("--skip-tests")
[[ -n "${EXTRA_MVN_OPTS}" ]]          && BUILD_ARGS+=("--maven-opts" "${EXTRA_MVN_OPTS}")

# The build must run at the RC tag commit. 02-rc-commit.sh leaves HEAD on the
# development branch, so we check out the tag, build, then return.
if [[ "${DRY_RUN}" == "true" ]]; then
    run_step "Build @ ${TAG_NAME} (03-build.sh)" echo "[DRY RUN] git checkout ${TAG_NAME} && ${BUILD_ARGS[*]} && git checkout -"
else
    echo ""
    echo "────────────────────────────────────────"
    echo "STEP: Build @ ${TAG_NAME} (03-build.sh)"
    echo "────────────────────────────────────────"
    git checkout "${TAG_NAME}"
    "${BUILD_ARGS[@]}"
    git checkout -
    echo "✅  Build — done"
fi

# ── STEP 3 (Automation D.3): Deploy to Nexus staging (optional) ──────────────

if [[ "${DEPLOY}" == "true" ]]; then
    DEPLOY_SCRIPT="${SCRIPT_DIR}/04-deploy-to-staging.sh"
    [[ ! -f "${DEPLOY_SCRIPT}" ]] && DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy-to-staging.sh"

    DEPLOY_ARGS=("${DEPLOY_SCRIPT}" "--tag" "${TAG_NAME}" "--deploy")
    [[ -n "${STAGING_URL}" ]] && DEPLOY_ARGS+=("--staging-url" "${STAGING_URL}")
    run_step "Deploy to Nexus staging (04-deploy-to-staging.sh)" "${DEPLOY_ARGS[@]}"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo "=========================================="
echo "release-all complete ✅"
echo ""
echo "  RC tag  : ${TAG_NAME}"
echo "  Version : ${RELEASE_VERSION}"
echo ""
if [[ "${DEPLOY}" == "true" ]]; then
    echo "Artifacts are in Nexus staging."
    echo "Close the staging repo, then start the vote on dev@kie.apache.org."
else
    echo "Artifacts are in your local ~/.m2 repository only."
    echo "Run 04-deploy-to-staging.sh --tag ${TAG_NAME} --deploy when ready."
fi
echo ""
echo "When the vote passes, promote the RC to a final release tag:"
echo "  ./script/release/05-tag-release.sh --rc-tag ${TAG_NAME} --push"
echo "=========================================="
