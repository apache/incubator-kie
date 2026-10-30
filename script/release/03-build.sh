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

# Builds the Apache KIE repo for a release (skipping tests by default).
#
# Because drools, optaplanner, kogito-runtimes, and kogito-apps now all live in
# the same reactor, a single `mvn clean install` is enough.
#
# Usage:
#   ./script/release/03-build.sh [--skip-tests] [--maven-opts <opts>]
#
# Flags:
#   --skip-tests          Skip all tests (default: tests are run)
#   --maven-opts <opts>   Extra Maven options appended to the command. Split on
#                         whitespace only; shell-style quoting is NOT honoured,
#                         so individual options must not contain spaces.
#
# Examples:
#   ./script/release/03-build.sh --skip-tests
#   ./script/release/03-build.sh --skip-tests --maven-opts "-T 4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

SKIP_TESTS=false
# Extra Maven opts stored as an array and expanded quoted, so no glob expansion,
# eval, or command substitution happens. Options are split on whitespace only.
EXTRA_MVN_OPTS=()

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --maven-opts)
            # Accept a single-argument "extra opts" string and split it on
            # whitespace into array elements using read. Quotes are not parsed.
            IFS=' ' read -r -a _extra <<< "${2:-}"
            EXTRA_MVN_OPTS+=("${_extra[@]}")
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--skip-tests] [--maven-opts <opts>]"
            exit 1
            ;;
    esac
done

cd "${REPO_ROOT}"

# ── Assemble Maven flags as a Bash array ──────────────────────────────────────

MVN_FLAGS=("-Dfull")

if [[ "${SKIP_TESTS}" == "true" ]]; then
    MVN_FLAGS+=("-DskipTests")
fi

if [[ "${#EXTRA_MVN_OPTS[@]}" -gt 0 ]]; then
    MVN_FLAGS+=("${EXTRA_MVN_OPTS[@]}")
fi

# ── Build ──────────────────────────────────────────────────────────────────────

echo "========================================"
echo "Apache KIE repo — build"
echo "Skip tests        : ${SKIP_TESTS}"
echo "Maven flags       : ${MVN_FLAGS[*]}"
echo "========================================"
echo ""

mvn clean install "${MVN_FLAGS[@]}"

echo ""
echo "========================================"
echo "Build complete."
echo ""
echo "JARs installed to local Maven repository (~/.m2/repository)."
echo "========================================"
