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

# The entrypoint for partial builds. What `make dev` does, the subcommands it
# takes and the settings it reads are all documented in docs/DEV.md; full builds
# are plain Maven, see docs/BUILDING.md.
#
# What is worth knowing to edit this file: `dev` is the only target, and the
# subcommand and Maven arguments reach Make as goals of its own. `--` is what
# stops Make from parsing `-D`/`-P`/`-T` as its own options; DEV_ARGS then
# collects every goal except `dev` and passes them to the script unchanged and in
# order, so `dev mvn -- clean install` arrives as `Dev.java mvn clean install`.
# The catch-all rule at the bottom keeps Make from trying to build the leftovers
# as targets.

DEV_ARGS = $(filter-out dev,$(MAKECMDGOALS))
DEV_MODE := $(filter dev,$(MAKECMDGOALS))

since=
uncommitted=
breadth=
upstream=
dev_cmd=KIE_DEV_SINCE=$(since) KIE_DEV_UNCOMMITTED=$(uncommitted) KIE_DEV_BREADTH=$(breadth) KIE_DEV_UPSTREAM=$(upstream) jbang script/dev/Dev.java

default: help

.PHONY: dev
## Build only what your changes affect. Also `make dev scope`, `make dev config`, `make dev mvn -- <maven args>`
dev:
	@$(dev_cmd) $(DEV_ARGS)

.PHONY: help
## This help screen
help:
	@printf "Available targets:\n\n"
	@awk '/^[a-zA-Z\-_0-9%:\\]+/ { \
		helpMessage = match(lastLine, /^## (.*)/); \
		if (helpMessage) { \
			helpCommand = $$1; \
			helpMessage = substr(lastLine, RSTART + 3, RLENGTH); \
			gsub("\\\\", "", helpCommand); \
			gsub(":+$$", "", helpCommand); \
			printf "  \x1b[32;01m%-14s\x1b[0m %s\n", helpCommand, helpMessage; \
		} \
	} \
	{ lastLine = $$0 }' $(MAKEFILE_LIST)
	@printf "\n"
	@printf "  make dev                       build only what your changes affect, fast\n"
	@printf "  make dev scope                 show what would be built, build nothing\n"
	@printf "  make dev config                show the current settings\n"
	@printf "  make dev mvn -- install        build them with your own Maven arguments\n"
	@printf "\n"
	@printf "For full builds, use Maven directly. See docs/BUILDING.md and docs/DEV.md.\n"
	@printf "\n"

# Absorb the subcommand and Maven arguments that reached us as goals. Scoped to
# `dev` on purpose: outside it, a mistyped target still fails loudly.
ifneq (,$(DEV_MODE))
%:
	@:
endif
