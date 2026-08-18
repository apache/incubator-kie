/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.junit.platform:junit-platform-console-standalone:1.11.4
//DEPS org.assertj:assertj-core:3.26.3

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.junit.platform.console.ConsoleLauncher;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.*;

/**
 * Snapshot tests for {@code script/ci/CiComputeBuildScopes.java}.
 *
 * For each scenario directory under {@code script/ci/tests/scenarios-compute-build-scopes/}:
 *   changed-files.txt      — input, one real repo-relative file path per line
 *   expected-upstream.txt  — golden list of groupId:artifactId, sorted
 *   expected-affected.txt  — golden list of groupId:artifactId, sorted
 *   expected-changed.txt   — golden list of groupId:artifactId, sorted (directly changed)
 *
 * The test runs CiComputeBuildScopes with the scenario's changed-files.txt
 * and diffs the produced upstream/affected/changed lists against the committed
 * goldens. Any divergence — a listed file moved, a module renamed/added/
 * removed, or a reactor dependency edge changed — breaks the test. The
 * fix is to regenerate the goldens in a follow-up PR.
 *
 * Env:
 *   CI_UPDATE_GOLDEN=1    optional — rewrite the golden files instead of asserting
 *
 * Run:
 *   jbang script/ci/tests/CiComputeBuildScopesTest.java
 */
public class CiComputeBuildScopesTest {

    static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    static final Path SCENARIOS_DIR = REPO_ROOT.resolve("script/ci/tests/scenarios-compute-build-scopes");
    static final Path SCRIPT = REPO_ROOT.resolve("script/ci/CiComputeBuildScopes.java");
    static final List<String> CATEGORIES = List.of("drools", "optaplanner", "kogito-runtimes", "kogito-apps");

    public static void main(String[] args) throws Exception {
        if ("1".equals(System.getenv("CI_UPDATE_GOLDEN"))) {
            updateGoldens();
            return;
        }

        ConsoleLauncher.main(new String[]{
            "execute",
            "--select-class=" + CiComputeBuildScopesTest.class.getName(),
            "--exclude-engine=junit-vintage",
            "--fail-if-no-tests"
        });
    }

    static Stream<Arguments> scenarios() throws IOException {
        if (!Files.isDirectory(SCENARIOS_DIR)) return Stream.empty();
        try (Stream<Path> s = Files.list(SCENARIOS_DIR)) {
            return s.filter(Files::isDirectory)
                    .sorted()
                    .map(p -> Arguments.of(p.getFileName().toString(), p))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name, Path scenario) throws Exception {
        Path changedFiles = scenario.resolve("changed-files.txt");
        Assumptions.assumeTrue(Files.isRegularFile(changedFiles), "missing changed-files.txt");

        Path tmp = Files.createTempDirectory("cbs-test-" + name + "-");
        Path actualUpstream = tmp.resolve("upstream.txt");
        Path actualAffected = tmp.resolve("affected.txt");
        Path actualChanged  = tmp.resolve("changed.txt");

        int rc = runScript(changedFiles, actualUpstream, actualAffected, actualChanged);
        Assertions.assertEquals(0, rc, "CiComputeBuildScopes exited with rc=" + rc);

        assertMatchesGolden(scenario, "upstream", actualUpstream);
        assertMatchesGolden(scenario, "affected", actualAffected);
        assertMatchesGolden(scenario, "changed",  actualChanged);

        for (String cat : CATEGORIES) {
            Path catAffected = tmp.resolve("affected-" + cat + ".txt");
            if (Files.isRegularFile(scenario.resolve("expected-affected-" + cat + ".txt"))) {
                assertMatchesGolden(scenario, "affected-" + cat, catAffected);
            }
        }
    }

    private static void assertMatchesGolden(Path scenario, String label, Path actual) throws IOException {
        Path expected = scenario.resolve("expected-" + label + ".txt");
        Assertions.assertTrue(
                Files.isRegularFile(expected),
                label + " golden missing: " + expected + "\n(run with CI_UPDATE_GOLDEN=1 to generate it)");
        assertThat(Files.readAllLines(actual))
                .as(label)
                .containsExactlyInAnyOrderElementsOf(Files.readAllLines(expected));
    }

    private static void updateGoldens() throws Exception {
        List<Path> scenarios;
        try (Stream<Path> s = Files.list(SCENARIOS_DIR)) {
            scenarios = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path scenario : scenarios) {
            String name = scenario.getFileName().toString();
            Path changedFiles = scenario.resolve("changed-files.txt");
            if (!Files.isRegularFile(changedFiles)) {
                System.err.println("[" + name + "] SKIP: missing changed-files.txt");
                continue;
            }
            Path tmp = Files.createTempDirectory("cbs-update-" + name + "-");
            Path actualUpstream = tmp.resolve("upstream.txt");
            Path actualAffected = tmp.resolve("affected.txt");
            Path actualChanged  = tmp.resolve("changed.txt");
            int rc = runScript(changedFiles, actualUpstream, actualAffected, actualChanged);
            if (rc != 0) {
                System.err.println("[" + name + "] FAIL: CiComputeBuildScopes exited with rc=" + rc);
                continue;
            }
            Files.copy(actualUpstream, scenario.resolve("expected-upstream.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualAffected, scenario.resolve("expected-affected.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualChanged,  scenario.resolve("expected-changed.txt"),  StandardCopyOption.REPLACE_EXISTING);
            for (String cat : CATEGORIES) {
                Path catAffected = tmp.resolve("affected-" + cat + ".txt");
                if (Files.isRegularFile(catAffected)) {
                    Files.copy(catAffected, scenario.resolve("expected-affected-" + cat + ".txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.err.println("[" + name + "] UPDATED goldens");
        }
    }

    private static int runScript(Path input, Path upstreamOut, Path affectedOut, Path changedOut)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "jbang", SCRIPT.toString(),
                input.toAbsolutePath().toString(),
                upstreamOut.toAbsolutePath().toString(),
                affectedOut.toAbsolutePath().toString(),
                changedOut.toAbsolutePath().toString());
        Process p = new ProcessBuilder(cmd)
                .directory(REPO_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) System.err.println("    " + line);
        }
        return p.waitFor();
    }
}
