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

# Deploys the locally-built JARs (already in ~/.m2) to a Nexus staging
# repository for Apache release voting.
#
# By default this script runs in DRY RUN mode and will NOT push anything
# remotely.  Pass --deploy to actually upload to Nexus.
#
# Usage:
#   ./script/release/deploy-to-staging.sh --tag <rc-tag> [--deploy] [--staging-url <url>]
#
# Examples:
#   # dry run (safe — prints the Maven command that would be run)
#   ./script/release/deploy-to-staging.sh --tag 10.3.0-rc1
#
#   # actually deploy to Apache Nexus staging
#   ./script/release/deploy-to-staging.sh --tag 10.3.0-rc1 --deploy
#
#   # deploy to a custom staging URL
#   ./script/release/deploy-to-staging.sh --tag 10.3.0-rc1 --deploy \
#       --staging-url https://repository.apache.org/service/local/staging/deploy/maven2
#
# Environment variables consumed when --deploy is active:
#   MAVEN_SETTINGS   Path to a settings.xml with Nexus credentials (required).
#                    The settings file must define a server with id "apache.releases.https"
#                    (or the id set via --server-id) carrying the deployer credentials.
#   MAVEN_GPG_PASSPHRASE   GPG passphrase for signing (required for Apache releases).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

TAG_NAME=""
DEPLOY=false
STAGING_URL="https://repository.apache.org/service/local/staging/deploy/maven2"
SERVER_ID="apache.releases.https"

while [[ $# -gt 0 ]]; do
    case $1 in
        --tag)
            TAG_NAME="${2:-}"
            shift 2
            ;;
        --deploy)
            DEPLOY=true
            shift
            ;;
        --staging-url)
            STAGING_URL="${2:-}"
            shift 2
            ;;
        --server-id)
            SERVER_ID="${2:-}"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --tag <rc-tag> [--deploy] [--staging-url <url>] [--server-id <id>]"
            exit 1
            ;;
    esac
done

if [[ -z "${TAG_NAME}" ]]; then
    echo "ERROR: --tag is required."
    echo "Usage: $0 --tag 10.3.0-rc1 [--deploy]"
    exit 1
fi

cd "${REPO_ROOT}"

# ── Resolve the version from the tag name ────────────────────────────────────
# Tag format: <version>-rc<N>  (e.g. 10.3.0-rc1)
RELEASE_VERSION="$(echo "${TAG_NAME}" | sed 's/-rc[0-9]*$//')"

echo "========================================"
echo "Drools unified repo — deploy to staging"
echo "Tag             : ${TAG_NAME}"
echo "Release version : ${RELEASE_VERSION}"
echo "Staging URL     : ${STAGING_URL}"
echo "Server ID       : ${SERVER_ID}"
echo "Deploy (real)   : ${DEPLOY}"
echo "========================================"

# ── Verify we are on / can resolve the RC tag ────────────────────────────────
if ! git rev-parse "${TAG_NAME}" &>/dev/null; then
    echo ""
    echo "ERROR: Git tag '${TAG_NAME}' not found in this repository."
    echo "       Run rc-commit.sh first, or check out the tag manually."
    exit 1
fi

# ── Assemble Maven flags ─────────────────────────────────────────────────────

DEPLOY_MVN_FLAGS=(
    "-DskipTests"
    "-Dfull"
    "-DaltDeploymentRepository=${SERVER_ID}::default::${STAGING_URL}"
)

if [[ -n "${MAVEN_SETTINGS:-}" ]]; then
    DEPLOY_MVN_FLAGS+=("-s" "${MAVEN_SETTINGS}")
fi

if [[ -n "${MAVEN_GPG_PASSPHRASE:-}" ]]; then
    DEPLOY_MVN_FLAGS+=("-Dgpg.passphrase=${MAVEN_GPG_PASSPHRASE}")
fi

echo ""
if [[ "${DEPLOY}" == "false" ]]; then
    echo "[DRY RUN] Would run from tag ${TAG_NAME}:"
    echo ""
    echo "  git checkout ${TAG_NAME}"
    echo "  mvn deploy ${DEPLOY_MVN_FLAGS[*]}"
    echo "  git checkout -"
    echo ""
    echo "Pass --deploy to actually upload artifacts."
    exit 0
fi

# ── Checkout the exact tag commit before deploying ───────────────────────────
ORIG_REF="$(git rev-parse --abbrev-ref HEAD)"

echo "--- Checking out tag ${TAG_NAME} ---"
git checkout "${TAG_NAME}"

echo ""
echo "--- Deploying to Nexus staging (${STAGING_URL}) ---"
# shellcheck disable=SC2068
mvn deploy ${DEPLOY_MVN_FLAGS[@]}

echo ""
echo "--- Returning to ${ORIG_REF} ---"
git checkout "${ORIG_REF}"

echo ""
echo "========================================"
echo "Deploy to staging complete."
echo ""
echo "Next steps:"
echo "  1. Log in to https://repository.apache.org and close the staging repo."
echo "  2. Share the staging repo URL with the release vote thread."
echo "  3. Once the vote passes, run:"
echo "     ./script/release/tag-release.sh --rc-tag ${TAG_NAME}"
echo "========================================"
