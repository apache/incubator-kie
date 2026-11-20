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

# KIE API

The Apache KIE Public API module provides the core interfaces and classes for the Drools, DMN and jBPM rule engines. This API aims to maintain backwards compatibility within minor and patch releases, following semantic versioning principles.

## API Change Management

Apache KIE follows [Semantic Versioning](https://semver.org/) since release 10.3.0:
- **Major version** (X.0.0): Breaking changes allowed
- **Minor version** (0.X.0): New features, backwards compatible
- **Patch version** (0.0.X): Bug fixes only, backwards compatible

### API Deprecation and Removal

- APIs planned for removal must be **deprecated first** and can only be removed in a **major release**
- Deprecation must include:
  - `@Deprecated` annotation with `since` and `forRemoval` attributes
  - Javadoc explaining why it's deprecated and the recommended alternative
  - Exception: When an entire feature is removed, no alternative may exist

### API Changes

- **Changing an API**: Deprecate the existing API and introduce the new one. Redirect users via javadoc
- **New APIs**: Must include `@since` javadoc tag indicating the version when introduced
- **Documentation**: All public APIs must have comprehensive javadoc

## API Compatibility Checking

This module uses [Revapi](https://revapi.org/) as the primary tool for API compatibility checking and enforcement.

Additionally, [JApiCmp](https://github.com/siom79/japicmp) is used to generate supplementary compatibility reports for analysis purposes.

### Revapi

#### Purpose
Revapi automatically detects breaking changes in the public API by comparing the current code against a previous release, helping maintain backwards compatibility.

#### Usage

The `revapi.latestVersion` property defines which version to compare against. The current version is defined in `build-parent/pom.xml`.

```bash
# Check API compatibility (uses revapi.latestVersion from global settings)
mvn clean verify

# Or specify version explicitly (check build-parent/pom.xml for current version)
mvn clean verify -Drevapi.latestVersion=<version>

# Generate detailed HTML report
mvn clean package -Drevapi.latestVersion=<version>
# Report: target/site/revapi-report.html
```

#### Handling API Changes

If Revapi detects API changes and you need to make **intentional** breaking or non-breaking changes:

1. Review the reported changes carefully
2. Update `src/build/revapi-config.json` to approve the changes
3. Add each change to the `ignore` array with:
   - Description of the change
   - Justification explaining why it's necessary

**Example:**
```json
[
  {
    "extension": "revapi.differences",
    "configuration": {
      "ignore": true,
      "differences": [
        {
          "code": "java.method.addedToInterface",
          "new": "method void org.kie.api.KieSession::newMethod()",
          "justification": "kie-issues#xyz: Added new method for feature X"
        }
      ]
    }
  }
]
```

#### Configuration
- **Config file**: `src/build/revapi-config.json`
- **Excludes**: Internal packages (`org.kie.internal.*`, `org.kie.api.internal.*`)
- **Tracks**: Deprecations and API changes

#### Resources
- [Official Website](https://revapi.org/)
- [GitHub Repository](https://github.com/revapi/revapi)
- [Documentation](https://revapi.org/revapi-site/main/index.html)

### JApiCmp (Report Generation Only)

#### Purpose
JApiCmp generates detailed binary compatibility reports for analysis and documentation purposes. In this project, JApiCmp is configured to not break the build on compatibility issues (the default behavior), making it suitable for report generation and analysis only. This is unlike Revapi, which is configured to enforce compatibility and can fail the build.

#### Usage

JApiCmp runs automatically during the `package` phase. The comparison is configured via properties in `build-parent/pom.xml`:

```bash
# Run with default settings (compares against kie.latest.released.version)
mvn clean package

# Override comparison version
mvn clean package -Djapicmp.oldVersion.version=10.1.0

# For downstream products with different coordinates
mvn clean package \
  -Djapicmp.oldVersion.version=1.0.0
```

#### Report Output

JApiCmp generates reports in the `target` directory:
- Console output during build showing detected changes
- Detailed reports can be generated in HTML, XML, or other formats (see [Maven Plugin Goals](https://siom79.github.io/japicmp/MavenPlugin.html#goals))

#### Configuration

**Plugin Configuration** (in `kie-api/pom.xml`):
- **Goal**: `cmp` - Compares the current artifact against the old version
- **Phase**: `package` - Runs during the package phase
- **Excludes**: Internal packages (`org.kie.internal`, `org.kie.api.internal`)

**Properties** (defined in `build-parent/pom.xml`):
- `japicmp.oldVersion.version` - Version to compare against (default: `${kie.latest.released.version}`)

#### Resources
- [Official Website](https://siom79.github.io/japicmp/)
- [GitHub Repository](https://github.com/siom79/japicmp)
- [Maven Plugin Documentation](https://siom79.github.io/japicmp/MavenPlugin.html)