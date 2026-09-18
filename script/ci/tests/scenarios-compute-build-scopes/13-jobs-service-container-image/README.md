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

# 13 — Jobs Service container image dependency

**Input:** a single integration-test source file that uses the shared Jobs
Service test resource.

**What this tests:** the affected test stays in partition 2 and its upstream
image-producer set includes the concrete `jobs-service-postgresql` module, not
only the `jobs-service-quarkus` aggregator or PostgreSQL common library.

**Expected structure:**

- `affected-partition2` contains the Jobs Service management integration test.
- `image-producers-partition2` contains `jobs-service-postgresql`,
  `jobs-service-common`, and `data-index-service-postgresql`.
- Other partitions have no affected modules or image producers.

**Why snapshot:** reactor aggregation does not create a Maven dependency edge.
Without a direct build-ordering dependency, a sparse partition can run these
tests without first building the local PostgreSQL Jobs Service image.
