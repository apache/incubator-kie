<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  -->

# Working in this repository

When verifying changes in this repository, take advantage of the `make dev`
script to perform partial builds:

```bash
make dev
```

It works out which modules your changes touch, rebuilds any upstream modules that
are missing or out of date, and builds the rest.

| Command | Use it when |
| --- | --- |
| `make dev` | You changed code and want to check it still builds |
| `make dev scope` | You want to see what would be built, without building |
| `make dev config` | You want to see the current settings |
| `make dev mvn -- <mvn args>` | You need a specific Maven command, e.g. to run tests |

Never commit `.kie-dev/` — it is local state, and gitignored.

## Everything else

**[docs/README.md](./docs/README.md)** is the index: how this repository is
structured, how to build it, its conventions, and what CI checks on a pull
request.

The two that come up most often are [docs/DEV.md](./docs/DEV.md), which covers
how `make dev` decides what to build and every setting it takes, and
[docs/BUILDING.md](./docs/BUILDING.md) for full builds.

If you change the scripts behind `make dev` or the CI scripts, run their tests
first — see [script/dev/README.md](./script/dev/README.md) and
[script/ci/README.md](./script/ci/README.md).
