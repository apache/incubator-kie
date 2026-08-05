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

# History

KIE — short for *Knowledge Is Everything* — is a community of solutions and
supporting tooling for knowledge engineering and process automation, focusing
on events, rules, and workflows. The name has a history going back more than
two decades.

## Origins

The projects that make up Apache KIE predate the umbrella by many years.
Drools began in 2001 as an open source rule engine and grew into a full
business rule management system at JBoss, later Red Hat. OptaPlanner started
in 2006 as *Drools Planner* before becoming a project in its own right. jBPM
emerged at JBoss as a workflow engine for long-running business processes,
and from jBPM 5 (2010) onwards it was rebuilt around the same knowledge core
as Drools. The *KIE* umbrella name was introduced alongside Drools and jBPM 6
to bring the projects — engines, tooling, and shared infrastructure — under
one roof.

The community projects also served as the upstream for a line of commercial
offerings by Red Hat: JBoss Enterprise BRMS 5, Red Hat JBoss BPM Suite 6, and
Red Hat Process Automation Manager and Red Hat Decision Manager 7.

The Drools and jBPM 6 and 7 generations came with distinct components of
their own. **Guvnor** provided the web-based repository and authoring
environment for business rules and knowledge assets. It evolved into the KIE
Workbench, later rebranded as **Business Central** — a full web environment
for authoring, building, and managing rules, processes, forms, and dashboards.
**KIE Server** was the standalone execution server of that generation,
exposing REST APIs to run rules, processes, and plannings deployed as kjars.
These components were superseded by the cloud-native generation, with
authoring moving to KIE Tools and execution to Kogito.

For most of their history the projects were developed in the open on GitHub
under the [kiegroup](https://github.com/kiegroup) organization, sponsored by
Red Hat. In 2019, Kogito was announced — a continuation of Drools, jBPM, and
OptaPlanner, completely redesigned to be cloud-native — building on Quarkus
and code generation to run rules, decisions, and processes as microservices.

## Initiatives along the way

Beyond the engines, the KIE community incubated a number of initiatives over
the years, some of which grew into projects of their own:

- **[Dashbuilder](https://www.dashbuilder.org)** — a tool for authoring
  dashboards and visualizing business data, today part of KIE Tools.
- **[TrustyAI](https://github.com/trustyai-explainability)** — explainability
  and auditing for AI-augmented decision services; it continues today as an
  independent open source project. Traces of it remain in this repository
  (e.g. the `kogito-trusty` and `kogito-explainability` modules).
- **[SonataFlow](https://sonataflow.org)** — a workflow engine for building
  serverless applications, implementing the CNCF Serverless Workflow
  specification, grown out of Kogito.

## The move to Apache

In July 2022, Red Hat and IBM announced that IBM was joining the KIE
community, combining IBM's leadership in business automation with Red Hat's
leadership in open source, and shared their intent to move the technologies
to a foundation to widen the network of collaborators (see the
[announcement](https://web.archive.org/web/20240719040925/https://blog.kie.org/2022/07/ibm-rht.html)).

On January 13th, 2023, KIE entered the
[Apache Incubator](https://incubator.apache.org/projects/kie.html). The
repositories moved from `github.com/kiegroup` to `github.com/apache` under
the `incubator-kie-*` prefix, the community adopted the Apache Way, and the
project's home became [kie.apache.org](https://kie.apache.org).

The [kiegroup](https://github.com/kiegroup) organization forked back some repositories from Apache KIE as midstreams in order for Red Hat to keep maintaining its commercial offerings based on KIE.
