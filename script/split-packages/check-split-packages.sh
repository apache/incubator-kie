#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Usage: ./script/split-packages/check-split-packages.sh /path/to/your/root/dir
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${1:-$SCRIPT_DIR/../..}"  # default to the repository root if no argument passed
ALLOWED_SPLITS_FILE="${2:-$SCRIPT_DIR/check-split-packages-allowed.txt}"  # optional: path to file listing allowed split packages (one per line)

# Use process substitution instead of pipes to avoid subshell issues
declare -A package_map
declare -A seen
declare -A last_conflict_jar  # Track the last conflicting jar for each package

echo "🔍 Scanning all JARs under: $ROOT_DIR (excluding test JARs)"

# Get all JAR files first, excluding test JARs
jar_files=()
while IFS= read -r jar; do
  if [[ "$jar" != *"-test"*          \
     && "$jar" != *"-cli-"*          \
     && "$jar" != *"original-"*      \
     && "$jar" != *"-bootstrap-"*    \
     && "$jar" != */quarkus-app/*          \
     && "$jar" != */drools-distribution/*  \
     && "$jar" != */testing-maven-repo/*   \
     && "$jar" != */target/it/*            \
     && "$jar" != */target/surefire/*      \
     && "$jar" != */target/test-classes/* ]]; then
    jar_files+=("$jar")
  fi
done < <(find "$ROOT_DIR" -type f -name "*.jar")

echo "Found ${#jar_files[@]} non-test JAR files to analyze"

# Process each JAR file
for jar in "${jar_files[@]}"; do
  jarname=$(realpath "$jar")
  jarbasename=$(basename "$jarname")  # Get just the filename without path
  tmpdir=$(mktemp -d)
  unzip -qq "$jar" -d "$tmpdir"
  
  # Get all class files
  class_files=()
  while IFS= read -r classfile; do
    class_files+=("$classfile")
  done < <(find "$tmpdir" -type f -name "*.class")
  
  # Process each class file
  for classfile in "${class_files[@]}"; do
    pkg=$(dirname "${classfile#$tmpdir/}" | tr '/' '.')
    [[ "$pkg" == "." ]] && continue  # skip default package
    
    if [ -n "${package_map[$pkg]}" ]; then
      if [ "${package_map[$pkg]}" != "$jarbasename" ]; then
        # Check if this package is in the allowed splits list
        allowed=0
        if [[ -n "$ALLOWED_SPLITS_FILE" && -f "$ALLOWED_SPLITS_FILE" ]]; then
          # Strip blank lines and # comments before matching
          if grep -v '^\s*#' "$ALLOWED_SPLITS_FILE" | grep -v '^\s*$' | grep -qxF "$pkg"; then
            allowed=1
          fi
        fi
        if [[ $allowed -eq 0 ]]; then
          if [[ -z "${seen[$pkg]}" ]]; then
            # First time seeing this conflict
            echo "🚨 Split package detected: $pkg"
            echo "    ↳ in: ${package_map[$pkg]}"
            echo "    ↳ and: $jarbasename"
            seen[$pkg]=1
            last_conflict_jar[$pkg]="$jarbasename"
          elif [[ "${last_conflict_jar[$pkg]}" != "$jarbasename" ]]; then
            # New jar with same conflict
            echo "    ↳ also in: $jarbasename"
            last_conflict_jar[$pkg]="$jarbasename"
          fi
          # If it's the same jar as last time, we don't print anything
        fi
      fi
    else
      package_map[$pkg]=$jarbasename;
    fi
  done
  
  rm -rf "$tmpdir"
done

if [[ ${#seen[@]} -eq 0 ]]; then
    echo "✅ No split packages found!"
else
    echo "❌ Split packages detected — please fix before modularizing."
    exit 1
fi
