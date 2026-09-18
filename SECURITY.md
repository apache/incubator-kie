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

# Security Policy

## Reporting a vulnerability

Apache KIE follows the [ASF vulnerability reporting and handling
process](https://www.apache.org/security/). Please report suspected vulnerabilities
privately through that process, rather than in public issues or pull requests.
Include the affected component and version, reproduction steps, and the
permissions an attacker needs. If unsure whether a finding is a vulnerability,
please report it privately for assessment.

## Security model

This policy covers all components in this repository.

Rules, models, executable expressions, application code, and configuration are
trusted inputs. This includes:

- Drools rules, rule templates, and decision tables.
- DMN/PMML models and FEEL expressions supplied for evaluation.
- OptaPlanner solver configuration, constraint definitions, and score calculators.
- Kogito/jBPM process definitions, scripts, handlers, and service configuration.

Treat these inputs as application source code, including when supplied through
runtime APIs.

Depending on the language and enabled features, these inputs can execute code
with the permissions of the hosting process. For example, a DRL rule that
deliberately executes an operating system command or reads a file uses an
intended capability; this alone is not a vulnerability in Drools.

Runtime data, such as facts, decision inputs, planning problem data, process
variables, and service requests, is distinct from these trusted inputs.
Permission to submit data does not imply permission to supply executable logic.
A KIE defect that allows unintended code execution from runtime data, or bypasses
a documented authentication or authorization control, can be a vulnerability.
