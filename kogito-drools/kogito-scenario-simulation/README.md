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

Kogito Test Scenario runner
===========================

This module is a wrapper for Kogito of test scenario runner from `drools` repo.


How to use
----------

If you have one or more `*.scesim` files in your project to execute, add this dependency to project POM

```xml
<dependency>
    <groupId>org.kie.kogito</groupId>
    <artifactId>kogito-scenario-simulation</artifactId>
    <version>${kogito.version}</version>
</dependency>
```

And then create `TestScenarioJunitActivatorTest.java` file in `src/test/java/testscenario` with this content

```java
package testscenario;

import org.drools.scenariosimulation.backend.runner.TestScenarioActivator;

/**
 * KogitoJunitActivator is a custom JUnit runner that enables the execution of Test Scenario files (*.scesim).
 * This activator class, when executed, will load all scesim files available in the project and run them.
 * Each row of the scenario will generate a test JUnit result.
 */
@TestScenarioActivator
public class TestScenarioJunitActivatorTest {
}
```

After that execute `mvn clean test` to execute it (you can also execute `TestScenarioJunitActivatorTest` in your IDE)