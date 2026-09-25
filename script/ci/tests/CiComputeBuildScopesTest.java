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
//SOURCES ../CiComputeBuildScopes.java

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.junit.platform.console.ConsoleLauncher;

import java.io.*;
import java.nio.file.*;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.*;

/**
 * Snapshot tests for {@code script/ci/CiComputeBuildScopes.java}.
 *
 * For each scenario directory under {@code script/ci/tests/scenarios-compute-build-scopes/}:
 *   changed-files.txt                — input, one real repo-relative file path per line
 *   expected-upstream.txt            — golden list of groupId:artifactId, sorted
 *   expected-affected.txt            — golden list of groupId:artifactId, sorted
 *   expected-changed.txt             — golden list of groupId:artifactId, sorted (directly changed)
 *   expected-image-producers.txt      — upstream modules configured as image producers
 *   expected-affected-partitionN.txt — per-partition affected goldens (when partition files exist)
 *   expected-upstream-partitionN.txt — per-partition upstream goldens
 *   expected-image-producers-partitionN.txt — per-partition image-producer goldens
 *   expected-affected-default.txt    — implicit default partition affected golden
 *   expected-upstream-default.txt    — implicit default partition upstream golden
 *
 * Generated per-partition changed files are checked against the intersection
 * of the global changed list and each partition's affected list.
 *
 * The test runs CiComputeBuildScopes with the scenario's changed-files.txt
 * (and CI_PARTITIONS_DIR pointing to the real partition files) and diffs the
 * produced lists against the committed goldens. Any divergence — a listed
 * file moved, a module renamed/added/removed, or a reactor dependency edge
 * changed — breaks the test. The fix is to regenerate the goldens in a
 * follow-up PR.
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
    static final Path PARTITIONS_DIR = REPO_ROOT.resolve(".github/supporting-files/ci/partitions");
    static final List<String> PARTITIONS;

    static {
        List<String> parts = List.of();
        if (Files.isDirectory(PARTITIONS_DIR)) {
            try (Stream<Path> s = Files.list(PARTITIONS_DIR)) {
                parts = s.map(p -> p.getFileName().toString())
                         .filter(n -> n.startsWith("partition") && n.endsWith(".txt"))
                         .sorted()
                         .map(n -> n.replaceFirst("\\.txt$", ""))
                         .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        parts = new java.util.ArrayList<>(parts);
        parts.add("default");
        PARTITIONS = List.copyOf(parts);
    }

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

    @Test
    void partitionUpstreamKeepsLocalAffectedDependencyNeededByForeignAffectedModule() {
        String consumer = "test:partition1-consumer";
        String foreignDependency = "test:partition2-dependency";
        String localDependency = "test:partition1-dependency";

        DepGraph graph = new DepGraph();
        graph.upstreamOf.put(consumer, Set.of(foreignDependency));
        graph.upstreamOf.put(foreignDependency, Set.of(localDependency));
        graph.upstreamOf.put(localDependency, Set.of());

        CiComputeBuildScopes.Partition partition1 =
                new CiComputeBuildScopes.Partition("partition1", Set.of());
        partition1.assigned.addAll(Set.of(consumer, localDependency));
        CiComputeBuildScopes.Partition partition2 =
                new CiComputeBuildScopes.Partition("partition2", Set.of());
        partition2.assigned.add(foreignDependency);

        CiComputeBuildScopes.computePerPartitionUpstream(
                List.of(partition1, partition2), graph);

        // partition1 must install the foreign module before testing its consumer. The
        // foreign module, in turn, cannot be built unless the local affected dependency
        // is included in this upstream pass as well.
        assertThat(partition1.upstream)
                .containsExactlyInAnyOrder(consumer, foreignDependency, localDependency);
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
        Path actualImageProducers = tmp.resolve("image-producers.txt");

        int rc = runScript(changedFiles, actualUpstream, actualAffected, actualChanged, actualImageProducers);
        Assertions.assertEquals(0, rc, "CiComputeBuildScopes exited with rc=" + rc);

        assertMatchesGolden(scenario, "upstream", actualUpstream);
        assertMatchesGolden(scenario, "affected", actualAffected);
        assertMatchesGolden(scenario, "changed",  actualChanged);
        assertMatchesGolden(scenario, "image-producers", actualImageProducers);

        for (String part : PARTITIONS) {
            Path partAffected = tmp.resolve("affected-" + part + ".txt");
            if (Files.isRegularFile(scenario.resolve("expected-affected-" + part + ".txt"))) {
                assertMatchesGolden(scenario, "affected-" + part, partAffected);
            }
            Path partUpstream = tmp.resolve("upstream-" + part + ".txt");
            if (Files.isRegularFile(scenario.resolve("expected-upstream-" + part + ".txt"))) {
                assertMatchesGolden(scenario, "upstream-" + part, partUpstream);
            }
            Path partImageProducers = tmp.resolve("image-producers-" + part + ".txt");
            if (Files.isRegularFile(scenario.resolve("expected-image-producers-" + part + ".txt"))) {
                assertMatchesGolden(scenario, "image-producers-" + part, partImageProducers);
            }
            Path partChanged = tmp.resolve("changed-" + part + ".txt");
            Assertions.assertTrue(Files.isRegularFile(partChanged),
                    "partition changed list missing: " + partChanged);
            Set<String> expectedPartChanged = new HashSet<>(Files.readAllLines(actualChanged));
            expectedPartChanged.retainAll(Files.readAllLines(partAffected));
            assertThat(Files.readAllLines(partChanged))
                    .as("changed-" + part)
                    .containsExactlyInAnyOrderElementsOf(expectedPartChanged);
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
            Path actualImageProducers = tmp.resolve("image-producers.txt");
            int rc = runScript(changedFiles, actualUpstream, actualAffected, actualChanged, actualImageProducers);
            if (rc != 0) {
                System.err.println("[" + name + "] FAIL: CiComputeBuildScopes exited with rc=" + rc);
                continue;
            }
            Files.copy(actualUpstream, scenario.resolve("expected-upstream.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualAffected, scenario.resolve("expected-affected.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualChanged,  scenario.resolve("expected-changed.txt"),  StandardCopyOption.REPLACE_EXISTING);
            Files.copy(actualImageProducers, scenario.resolve("expected-image-producers.txt"), StandardCopyOption.REPLACE_EXISTING);
            for (String part : PARTITIONS) {
                Path partAffected = tmp.resolve("affected-" + part + ".txt");
                if (Files.isRegularFile(partAffected)) {
                    Files.copy(partAffected, scenario.resolve("expected-affected-" + part + ".txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                Path partUpstream = tmp.resolve("upstream-" + part + ".txt");
                if (Files.isRegularFile(partUpstream)) {
                    Files.copy(partUpstream, scenario.resolve("expected-upstream-" + part + ".txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                Path partImageProducers = tmp.resolve("image-producers-" + part + ".txt");
                if (Files.isRegularFile(partImageProducers)) {
                    Files.copy(partImageProducers, scenario.resolve("expected-image-producers-" + part + ".txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.err.println("[" + name + "] UPDATED goldens");
        }
    }

    private static int runScript(Path input, Path upstreamOut, Path affectedOut, Path changedOut,
                                 Path imageProducersOut)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "jbang", SCRIPT.toString(),
                input.toAbsolutePath().toString(),
                upstreamOut.toAbsolutePath().toString(),
                affectedOut.toAbsolutePath().toString(),
                changedOut.toAbsolutePath().toString(),
                imageProducersOut.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(REPO_ROOT.toFile())
                .redirectErrorStream(true);
        if (Files.isDirectory(PARTITIONS_DIR)) {
            pb.environment().put("CI_PARTITIONS_DIR", PARTITIONS_DIR.toString());
        }
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) System.err.println("    " + line);
        }
        return p.waitFor();
    }
}
