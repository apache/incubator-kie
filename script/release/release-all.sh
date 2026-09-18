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
# candidate cycle (R commit → build → deploy to staging).  Each step can also
# be run independently — see script/release/README.md.
#
# Usage:
#   ./script/release/release-all.sh --version <version> --tag <rc-tag> [OPTIONS]
#
# Required:
#   --version <ver>           Exact release version, e.g. 10.3.0
#   --tag <tag>               RC tag name, e.g. 10.3.0-rc1
#
# Optional build flags:
#   --jitexecutor-native      Also build the jitexecutor native binary
#                             (requires GraalVM + Docker)
#   --maven-opts <opts>       Extra Maven options forwarded to build.sh
#
# Optional deploy flags:
#   --deploy                  Deploy JARs to Nexus staging after the build
#   --staging-url <url>       Nexus staging URL (default: Apache Nexus)
#
# Optional git flags:
#   --push-tag                Push the RC tag to origin after creating it
#
# Other:
#   --skip-tests              Skip tests during the build (default: tests run)
#   --dry-run                 Print what would happen without executing anything
#
# Examples:
#   # Full local RC candidate — no remote effects
#   ./script/release/release-all.sh --version 10.3.0 --tag 10.3.0-rc1 --skip-tests
#
#   # Full RC with deploy to Apache Nexus staging and push tag to origin
#   ./script/release/release-all.sh \
#       --version 10.3.0 --tag 10.3.0-rc1 \
#       --skip-tests --deploy --push-tag \
#       --jitexecutor-native

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RELEASE_VERSION=""
TAG_NAME=""
SKIP_TESTS=false
JITEXECUTOR_NATIVE=false
EXTRA_MVN_OPTS=""
DEPLOY=false
STAGING_URL=""
PUSH_TAG=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --version)       RELEASE_VERSION="${2:-}"; shift 2 ;;
        --tag)           TAG_NAME="${2:-}"; shift 2 ;;
        --skip-tests)    SKIP_TESTS=true; shift ;;
        --jitexecutor-native) JITEXECUTOR_NATIVE=true; shift ;;
        --maven-opts)    EXTRA_MVN_OPTS="${2:-}"; shift 2 ;;
        --deploy)        DEPLOY=true; shift ;;
        --staging-url)   STAGING_URL="${2:-}"; shift 2 ;;
        --push-tag)      PUSH_TAG=true; shift ;;
        --dry-run)       DRY_RUN=true; shift ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --version <version> --tag <tag> [OPTIONS]"
            exit 1
            ;;
    esac
done

if [[ -z "${RELEASE_VERSION}" || -z "${TAG_NAME}" ]]; then
    echo "ERROR: --version and --tag are required."
    echo "Usage: $0 --version 10.3.0 --tag 10.3.0-rc1 [OPTIONS]"
    exit 1
fi

cd "${REPO_ROOT}"

echo ""
echo "=========================================="
echo "Apache KIE Drools — release-all"
echo "Version         : ${RELEASE_VERSION}"
echo "RC tag          : ${TAG_NAME}"
echo "Skip tests      : ${SKIP_TESTS}"
echo "Jitexecutor nat.: ${JITEXECUTOR_NATIVE}"
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

# ── STEP 1: R commit + RC tag ─────────────────────────────────────────────────

RC_COMMIT_ARGS=("${SCRIPT_DIR}/rc-commit.sh" "--version" "${RELEASE_VERSION}" "--tag" "${TAG_NAME}")
[[ "${PUSH_TAG}" == "true" ]] && RC_COMMIT_ARGS+=("--push")
[[ "${DRY_RUN}" == "true" ]]  && RC_COMMIT_ARGS+=("--dry-run")

run_step "R commit + RC tag" "${RC_COMMIT_ARGS[@]}"

# ── STEP 2: Build ─────────────────────────────────────────────────────────────

BUILD_ARGS=("${SCRIPT_DIR}/build.sh")
[[ "${SKIP_TESTS}" == "true" ]]       && BUILD_ARGS+=("--skip-tests")
[[ "${JITEXECUTOR_NATIVE}" == "true" ]] && BUILD_ARGS+=("--jitexecutor-native")
[[ -n "${EXTRA_MVN_OPTS}" ]]          && BUILD_ARGS+=("--maven-opts" "${EXTRA_MVN_OPTS}")

# The build must run at the RC tag commit.  rc-commit.sh leaves HEAD on the
# development branch, so we check out the tag, build, then return.
if [[ "${DRY_RUN}" == "true" ]]; then
    run_step "Build @ ${TAG_NAME}" echo "[DRY RUN] git checkout ${TAG_NAME} && ${BUILD_ARGS[*]} && git checkout -"
else
    echo ""
    echo "────────────────────────────────────────"
    echo "STEP: Build @ ${TAG_NAME}"
    echo "────────────────────────────────────────"
    git checkout "${TAG_NAME}"
    "${BUILD_ARGS[@]}"
    git checkout -
    echo "✅  Build — done"
fi

# ── STEP 3: Deploy to Nexus staging (optional) ───────────────────────────────

if [[ "${DEPLOY}" == "true" ]]; then
    DEPLOY_ARGS=("${SCRIPT_DIR}/deploy-to-staging.sh" "--tag" "${TAG_NAME}" "--deploy")
    [[ -n "${STAGING_URL}" ]] && DEPLOY_ARGS+=("--staging-url" "${STAGING_URL}")
    run_step "Deploy to Nexus staging" "${DEPLOY_ARGS[@]}"
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
    echo "Run deploy-to-staging.sh --tag ${TAG_NAME} --deploy when ready."
fi
echo ""
echo "When the vote passes, promote the RC to a final release tag:"
echo "  ./script/release/tag-release.sh --rc-tag ${TAG_NAME} --push"
echo "=========================================="
