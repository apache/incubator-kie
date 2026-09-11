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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.junit.platform.console.ConsoleLauncher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Snapshot tests for {@code script/ci/CiSummary.java}.
 *
 * For each scenario directory under {@code script/ci/tests/scenarios-summary/}:
 *   env.properties         — one KEY=VALUE per line; passed as environment to CiSummary
 *   graph.tsv (optional)   — dep-graph TSV; any literal {ROOT} is replaced with the
 *                            absolute path to the scenario's root/ directory before use
 *   root/                  — used as CI_REPO_ROOT; hosts surefire/failsafe XML reports
 *                            under <module>/target/{surefire,failsafe}-reports/
 *   expected-summary.md    — golden output (stdout of CiSummary with [CI] log lines stripped)
 *
 * Env:
 *   CI_UPDATE_GOLDEN=1    optional — rewrite the golden files instead of asserting
 *
 * Run:
 *   jbang script/ci/tests/CiSummaryTest.java
 */
public class CiSummaryTest {

    static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    static final Path SCENARIOS_DIR = REPO_ROOT.resolve("script/ci/tests/scenarios-summary");
    static final Path SCRIPT = REPO_ROOT.resolve("script/ci/CiSummary.java");

    public static void main(String[] args) throws Exception {
        if ("1".equals(System.getenv("CI_UPDATE_GOLDEN"))) {
            updateGoldens();
            return;
        }

        System.setOut(withoutSummaryTable(System.out));
        ConsoleLauncher.main(new String[]{
            "execute",
            "--select-class=" + CiSummaryTest.class.getName(),
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
        Path expected = scenario.resolve("expected-summary.md");
        Assumptions.assumeTrue(Files.isRegularFile(expected),
                "missing expected-summary.md (run with CI_UPDATE_GOLDEN=1 to generate it)");

        String actual = runAndCapture(scenario);
        Assertions.assertEquals(
                Files.readString(expected, StandardCharsets.UTF_8),
                actual,
                "summary mismatch for scenario " + name);
    }

    @Test
    void parallelReproductionIncludesImageProducerPhase() throws Exception {
        Path scenario = Files.createTempDirectory("ci-summary-parallel-");
        Files.createDirectories(scenario.resolve("root"));
        Files.writeString(scenario.resolve("env.properties"), """
                MAVEN_PL_UPSTREAM=g:u,g:a,g:image
                MAVEN_PL_AFFECTED=g:a
                MAVEN_PL_CHANGED=g:a
                MAVEN_PL_IMAGE_PRODUCERS=g:image
                CI_PARALLEL_BUILD=true
                """, StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("graph.tsv"), """
                P\tg:u\t{ROOT}/u
                P\tg:a\t{ROOT}/a
                P\tg:image\t{ROOT}/image
                D\tg:a\tg:u
                D\tg:a\tg:image
                """, StandardCharsets.UTF_8);

        String actual = runAndCapture(scenario);

        Assertions.assertAll(
                () -> Assertions.assertTrue(actual.contains("**1 affected**")),
                () -> Assertions.assertTrue(actual.contains("**2 upstream**")),
                () -> Assertions.assertTrue(actual.contains("Step 1 — Build upstream dependencies")),
                () -> Assertions.assertTrue(actual.contains("-Dquarkus.build.skip=true -Ddisable.quarkus.plugin=true -Dskip.quarkus.image.assembly=true")),
                () -> Assertions.assertTrue(actual.contains("-pl \"g:u,g:a,g:image\" install")),
                () -> Assertions.assertTrue(actual.contains("Step 2 — Build required image-producing modules")),
                () -> Assertions.assertTrue(actual.contains("-pl \"g:image\" install")),
                () -> Assertions.assertTrue(actual.contains("Step 3 — Build changed and affected modules")),
                () -> Assertions.assertTrue(actual.contains("-Dfull -Dreproducible -pl \"g:a\" install")));
    }

    private static void updateGoldens() throws Exception {
        List<Path> scenarios;
        try (Stream<Path> s = Files.list(SCENARIOS_DIR)) {
            scenarios = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path scenario : scenarios) {
            String name = scenario.getFileName().toString();
            try {
                String actual = runAndCapture(scenario);
                Files.writeString(scenario.resolve("expected-summary.md"), actual, StandardCharsets.UTF_8);
                System.err.println("[" + name + "] UPDATED expected-summary.md");
            } catch (Exception e) {
                System.err.println("[" + name + "] FAIL: " + e.getMessage());
            }
        }
    }

    /**
     * Runs CiSummary against the scenario and returns stdout with {@code [CI] ...}
     * log lines stripped, so the golden contains only the rendered summary.
     */
    private static String runAndCapture(Path scenario) throws IOException, InterruptedException {
        Path root = scenario.resolve("root");
        if (!Files.isDirectory(root)) {
            throw new IOException("scenario missing root/ directory: " + scenario);
        }

        Map<String, String> extraEnv = new LinkedHashMap<>();
        extraEnv.put("CI_REPO_ROOT", root.toAbsolutePath().toString());
        // Unset so CiSummary writes to stdout rather than a summary file.
        extraEnv.put("GITHUB_STEP_SUMMARY", null);
        // Fixture XMLs use TS- prefix to avoid matching action-surefire-report's TEST-*.xml glob.
        extraEnv.put("CI_SUREFIRE_FILE_PREFIXES", "TS-");

        Path envProps = scenario.resolve("env.properties");
        if (Files.isRegularFile(envProps)) {
            for (String line : Files.readAllLines(envProps, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                extraEnv.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }

        Path graphTsv = scenario.resolve("graph.tsv");
        if (Files.isRegularFile(graphTsv)) {
            // Expand {ROOT} → absolute path to scenario root so dep-graph entries line up.
            String tsv = Files.readString(graphTsv, StandardCharsets.UTF_8)
                              .replace("{ROOT}", root.toAbsolutePath().toString());
            Path expanded = Files.createTempFile("ci-summary-graph-", ".tsv");
            Files.writeString(expanded, tsv, StandardCharsets.UTF_8);
            extraEnv.put("DEP_GRAPH_EXTRACTOR__OUTPUT_FILE", expanded.toAbsolutePath().toString());
        }

        ProcessBuilder pb = new ProcessBuilder("jbang", SCRIPT.toString())
                .directory(REPO_ROOT.toFile());
        Map<String, String> env = pb.environment();
        for (var e : extraEnv.entrySet()) {
            if (e.getValue() == null) env.remove(e.getKey());
            else env.put(e.getKey(), e.getValue());
        }

        Process p = pb.start();
        // Drain stderr so jbang resolver output doesn't block, and surface it for debugging.
        Thread err = new Thread(() -> {
            try (var r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) System.err.println("    [stderr] " + line);
            } catch (IOException ignored) {}
        });
        err.setDaemon(true);
        err.start();

        StringBuilder out = new StringBuilder();
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("[CI] ")) continue;
                out.append(line).append('\n');
            }
        }
        int rc = p.waitFor();
        err.join(2000);
        if (rc != 0) throw new IOException("CiSummary exited with rc=" + rc);
        return out.toString();
    }

    private static PrintStream withoutSummaryTable(PrintStream delegate) {
        return new PrintStream(delegate, true) {
            @Override
            public void println(String x) {
                if (x != null && (x.matches("\\[\\s*\\d+.*]") || x.startsWith("Test run finished after "))) return;
                super.println(x);
            }
        };
    }
}
