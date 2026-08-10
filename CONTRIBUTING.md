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

# Contribution guide

**Want to contribute? Great!**
We try to make it easy, and all contributions, even the smaller ones, are more than welcome.
This includes bug reports, fixes, documentation, examples...
But first, read this page (including the small print at the end).

This repository is part of [Apache KIE](https://kie.apache.org) (incubating),
the umbrella project for Drools, OptaPlanner, jBPM, and Kogito. Everything
below applies to contributions to any of them.

## Legal

All original contributions to Apache KIE are licensed under the
[ASL - Apache License](https://www.apache.org/licenses/LICENSE-2.0),
version 2.0 or later, or, if another license is specified as governing the file or directory being
modified, such other license.

## Issues

Apache KIE uses a shared issue tracker for all of its components:
[apache/incubator-kie-issues](https://github.com/apache/incubator-kie-issues/issues).

If you believe you found a bug, please indicate a way to reproduce it, what you are seeing and what you would expect to see.
Don't forget to indicate the KIE component (Drools, OptaPlanner, jBPM, Kogito) and your Java, Maven, Quarkus/Spring Boot, and GraalVM versions.

### Checking an issue is fixed in main

Sometimes a bug has been fixed in the `main` branch and you want to confirm it is fixed for your own application.
You can build `main` yourself — see the [Build](#build) section — and the artifacts will be available in your local Maven repository.

## Creating a Pull Request (PR)

To contribute, use GitHub Pull Requests, from your **own** fork.

- PRs should always be related to an open [issue](https://github.com/apache/incubator-kie-issues/issues).
  If there is none, you should [create one](https://github.com/apache/incubator-kie-issues/issues/new) describing what problem you see that we need to fix.
- Try to fix only one issue per PR.
- Make sure to create a new branch. Usually branches are named after the issue they are addressing. E.g.:

        git checkout -b Fix_#XYZ
        # or
        git checkout -b Fix_#XYZ-my-fix

- When you submit your PR, make sure to include the issue ID and its title; e.g., "Fix_#XYZ An example issue".
- The description of your PR should describe the code you wrote. The problem that is solved should be described in the corresponding issue.
- If your contribution spans across multiple Apache KIE repositories, use the same branch name in each PR, and make sure to list all the related PRs in each of them.

### Java Coding Guidelines

We decided to disallow `@author` tags in the Javadoc: they are hard to maintain, especially in a very active project, and we use the Git history to track authorship.

Copyright headers format is enforced during build time. In order to automatically format your files, you could run the following Maven command:
```bash
mvn com.mycila:license-maven-plugin:format
```

Make sure you have configured your IDE according to the project code style — see [docs/CONVENTIONS.md](./docs/CONVENTIONS.md#code-style).

### Requirements for Dependencies

Any dependency used in any KIE project must fulfill these hard requirements:

- The dependency must have **an Apache 2.0 compatible license**.
    - Good: BSD, MIT, Apache 2.0
    - Avoid: EPL, LGPL
        - Especially LGPL is a last resort and should be abstracted away or contained behind an SPI.
        - Test scope dependencies pose no problem if they are EPL or LPGL.
    - Forbidden: no license, GPL, AGPL, proprietary license, field of use restrictions ("this software shall be used for good, not evil"), ...
        - Even test scope dependencies cannot use these licenses.
    - To check the ASL compatibility license please visit these links: [Similarity in terms to the Apache License 2.0](http://www.apache.org/legal/resolved.html#category-a)&nbsp;
    [How should so-called "Weak Copyleft" Licenses be handled](http://www.apache.org/legal/resolved.html#category-b)

- The dependency shall be **available in [Maven Central](http://search.maven.org/)**.
    - Never add a `<repository>` element in a `pom.xml` when the artifact is intended for public usages, samples/demos are excluded from this.
    - Why?
        - Build reproducibility. Any repository server we use, must still run in future from now.
        - Build speed. More repositories slow down the build.
        - Build reliability. A repository server that is temporarily down can break builds.

- **Do not release the dependency yourself** (by building it from source).
    - Why? Because it's not an official release, by the official release guys.
        - A release must be 100% reproducible.
        - A release must be reliable (sometimes the release person does specific things you might not reproduce).

- **The sources are publicly available**
    - We may need to rebuild the dependency from sources ourselves in future. This may be in the rare case when
      the dependency is no longer maintained, but we need to fix a specific CVE there.
    - Make sure the dependency's pom.xml contains link to the source repository (`scm` tag).

- The dependency needs to use **reasonable build system**
    - Since we may need to rebuild the dependency from sources, we also need to make sure it is easily buildable.
      Maven or Gradle are acceptable as build systems.

Any dependency used in any KIE project should fulfill these soft requirements:

- **Edit dependencies in the appropriate BOM or build parent** — see
  [docs/STRUCTURE.md](./docs/STRUCTURE.md#parent-poms-and-boms).
    - Dependencies in subprojects should avoid overwriting the dependency versions of their build parent if there is no special case or need for that.

- Only use dependencies with **an active community**.
    - Check for activity in the last year through [Open Hub](https://www.openhub.net).

- Less is more: **less dependencies is better**. Bloat is bad.
    - Try to use existing dependencies if the functionality is available in those dependencies
        - For example: use `poi` instead of `jexcelapi` if `poi` is already a KIE dependency

- **Do not use fat jars, nor shading jars.**
    - A fat jar is a jar that includes another jar's content. For example: `weld-se.jar` which includes `org/slf4j/Logger.class`
    - A shaded jar is a fat jar that shades that other jar's content. For example: `weld-se.jar` which includes `org/weld/org/slf4j/Logger.class`
    - Both are bad because they cause dependency tree trouble. Use the non-fat jar instead, for example: `weld-se-core.jar`

There are currently a few dependencies which violate some of these rules. They should be properly commented with a
warning and an explanation as to why they are needed.
If you want to add a dependency that violates any of the rules above, get approval from the project leads.

### Tests and Documentation

Don't forget to include tests in your pull requests, and documentation (reference documentation, javadoc...).
Guides and reference documentation should be submitted to the [Apache KIE docs repository](https://github.com/apache/incubator-kie-docs).

- For Quarkus tests, basically use `@QuarkusTest` as unit tests for surefire-plugin and `@QuarkusIntegrationTest` as integration tests (`*IT.java`) for failsafe-plugin. Static http resources generated by `kogito-codegen` (`META-INF/resources/`) are available with `@QuarkusIntegrationTest`. If you need to access static http resources in `@QuarkusTest`, add `quarkus-undertow` dependency with `test` scope. Also note that you cannot mix `@QuarkusTest` and `@QuarkusIntegrationTest` in the same `integration-test` phase.

### Code Reviews and Continuous Integration

All submissions, including those by project members, need to be reviewed by others before being merged.
Our CI runs on GitHub Actions and should successfully execute your PR, marking the GitHub checks as green — see
[docs/PR_CHECKS_AND_CI.md](./docs/PR_CHECKS_AND_CI.md) for what runs on pull requests.

## Feature Proposals

If you would like to see some feature in Apache KIE, start with an email to the
[dev mailing list](mailto:dev@kie.apache.org) ([subscribe](mailto:dev-subscribe@kie.apache.org))
or just pop into our [Zulip chat](https://kie.zulipchat.com/) and tell us what you would like to see.

Great feature proposals should include a short **Description** of the feature, the **Motivation** that makes that feature necessary and the **Goals** that are achieved by realizing it.

## Setup

If you have not done so on this machine, you need to:

* Install Git and configure your GitHub access
* Install Java SDK (OpenJDK recommended)
* For Native Image, follow Quarkus instructions at [GraalVM](https://quarkus.io/guides/building-native-image)
* On macOS, check [Developing on macOS](./docs/DEVELOP_ON_MACOS.md) for further instructions.

Docker is not strictly necessary, but it is required to run some of the integration tests.
These tests can be skipped, but we recommend installing it to run these tests locally.

* Check [the installation guide](https://docs.docker.com/install/), and [the MacOS installation guide](https://docs.docker.com/docker-for-mac/install/)
* If you just install docker, be sure that your current user can run a container (no root required).
On Linux, check [the post-installation guide](https://docs.docker.com/install/linux/linux-postinstall/)

## Build

Clone the repository, navigate to the directory, and build with the Maven wrapper:

```bash
git clone https://github.com/apache/incubator-kie.git
cd incubator-kie
mvn clean install -DskipTests
# Wait... success!
```

By removing the `-DskipTests` flag, you will run the unit and integration tests.
It will take much longer to build but will give you more guarantees on your code.

Alternatively, you can invoke `mvn clean install -DquickTests` from the root directory.
It will perform the basic formatting validation and will run all the unit tests.
Use this command for quick checks.

See [docs/BUILDING.md](./docs/BUILDING.md) for more flags, test profiles, and troubleshooting.

### Test execution tips

Some tests are meant to be executed on a machine with the _en_US_ locale.
A specific profile is provided to execute them on machines with a different locale, namely `test-en`.
There are two ways to activate such profile during the Maven build:
1. `-Ptest-en` (profile-id based)
2. `-DTestEn` (property based)

The following two commands will execute tests on a machine with a locale different than _en_US_:

1. `mvn clean verify -Ptest-en`
2. `mvn clean verify -DTestEn`

## Usage

After the build is successful, the artifacts are available in your local Maven repository.

### Test Coverage

Jacoco is used to generate test coverage reports. If you would like to generate the report run `mvn clean verify -Ptest-coverage`.
The code coverage report will be generated in `target/site/jacoco/`.

## Documenting tips

UML diagrams are used for architectural and design documentation. Those diagrams are in `.puml` format and have been created with the [PlantUML](https://plantuml.com) tool.
Plugins exist to use it in different IDEs:
* [IntelliJ IDEA](https://plugins.jetbrains.com/plugin/7017-plantuml-integration)
* [Eclipse](https://marketplace.eclipse.org/content/plantuml-plugin)
* [VS Code](https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml)

## The small print

This project is an open source project, please act responsibly, be nice, polite and enjoy!
