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

# Other components

Besides Drools, OptaPlanner, jBPM, and Kogito, Apache KIE includes other
components worth knowing about.

## PMML engine

The PMML engine executes predictive models conforming to the
[PMML standard](https://dmg.org/pmml/pmml-v4-4-1.html) (Predictive Model
Markup Language, maintained by the Data Mining Group). It allows machine
learning models exported from data science tools to run alongside rules,
decisions, and processes — for example, a DMN decision invoking a predictive
model as part of its logic.

The engine lives in this repository, under the `kie-pmml-trusty` module.

## KIE Tools

[KIE Tools](https://github.com/apache/incubator-kie-tools) is a separate
repository hosting the tooling for authoring the business assets executed by
the engines: editors for BPMN, DMN, and DRL extensions for VS Code and Chrome, the online KIE Sandbox for authoring and sharing assets without any local setup.
