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

# Developing on macOS

## Setting up GraalVM for native image builds

Building native images requires GraalVM. The instructions below use
[SDKMAN!](https://sdkman.io) to manage JDK installations — not mandatory, but
recommended, as it makes switching JDKs during development easy.

Check the [Quarkus documentation](https://quarkus.io/guides/building-native-image#configuring-graalvm)
for the currently supported GraalVM version, and use it in place of
`<graalvm-version>` below.

1. Install SDKMAN!:

   ```bash
   curl -s "https://get.sdkman.io" | bash
   ```

2. Install [Homebrew](https://brew.sh):

   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

3. Install GraalVM with Homebrew:

   ```bash
   brew install --cask graalvm/tap/<graalvm-version>
   ```

4. Register the GraalVM installation with SDKMAN!:

   ```bash
   sdk install java <graalvm-version>-grl /Library/Java/JavaVirtualMachines/<graalvm-version>/Contents/Home
   ```

5. In the module you want to build, switch to GraalVM and run the native
   build:

   ```bash
   sdk use java <graalvm-version>-grl
   mvn clean package -Pnative
   ```
