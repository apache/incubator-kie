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
//SOURCES ../Dev.java

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.platform.console.ConsoleLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@code script/dev/Dev.java}.
 *
 * Unlike {@code CiComputeBuildScopesTest}, these do not run against the real reactor:
 * every test builds its own throwaway git repository, dependency graph and local Maven
 * repository under a temp directory. That keeps the suite fast and hermetic — it needs
 * git, but never Maven — and lets it assert on cases the real repository cannot
 * conveniently produce, such as an artifact older than its sources.
 *
 * Run:
 *   jbang script/dev/tests/DevTest.java
 */
public class DevTest {

    public static void main(String[] args) {
        ConsoleLauncher.main(new String[]{
            "execute",
            "--select-class=" + DevTest.class.getName(),
            "--exclude-engine=junit-vintage",
            "--fail-if-no-tests"
        });
    }

    // ------------------------------------------------------------------
    // since: the point in history a build measures from
    // ------------------------------------------------------------------

    @Test
    void sinceHeadSeesModifiedStagedDeletedAndUntrackedFiles() throws Exception {
        Path repo = newRepo();
        write(repo, "committed.txt", "v1");
        write(repo, "staged.txt", "v1");
        write(repo, "deleted.txt", "v1");
        commitAll(repo, "initial");

        write(repo, "committed.txt", "v2");           // unstaged modification
        write(repo, "staged.txt", "v2");
        git(repo, "add", "staged.txt");               // staged modification
        Files.delete(repo.resolve("deleted.txt"));    // deletion
        write(repo, "untracked.txt", "new");          // never added

        assertThat(Dev.collectChangedFiles(repo, "HEAD", true))
                .containsExactlyInAnyOrder("committed.txt", "staged.txt", "deleted.txt", "untracked.txt");
    }

    @Test
    void sinceHeadIsEmptyWhenEverythingIsCommitted() throws Exception {
        Path repo = newRepo();
        write(repo, "a.txt", "v1");
        commitAll(repo, "initial");

        assertThat(Dev.collectChangedFiles(repo, "HEAD", true)).isEmpty();
    }

    /**
     * The three-dot property: a branch build must not pick up work that landed on the
     * base branch after the fork, or every rebase of main would widen the build.
     */
    @Test
    void branchSeesOnlyItsOwnCommitsNotLaterWorkOnTheBase() throws Exception {
        Path repo = newRepo();
        write(repo, "base.txt", "v1");
        commitAll(repo, "initial");

        git(repo, "checkout", "-b", "feature");
        write(repo, "on-feature.txt", "v1");
        commitAll(repo, "feature work");

        git(repo, "checkout", "main");
        write(repo, "on-main-after-fork.txt", "v1");
        commitAll(repo, "later main work");
        git(repo, "checkout", "feature");

        assertThat(Dev.collectChangedFiles(repo, "main", false))
                .containsExactly("on-feature.txt");
    }

    @Test
    void theUncommittedFlagAddsTheWorkingTreeOnTop() throws Exception {
        Path repo = newRepo();
        write(repo, "base.txt", "v1");
        commitAll(repo, "initial");

        git(repo, "checkout", "-b", "feature");
        write(repo, "committed-on-branch.txt", "v1");
        commitAll(repo, "feature work");
        write(repo, "uncommitted.txt", "v1");

        assertThat(Dev.collectChangedFiles(repo, "main", true))
                .containsExactlyInAnyOrder("committed-on-branch.txt", "uncommitted.txt");
    }

    @Test
    void branchIsEmptyWhenNothingHasBeenCommittedOnIt() throws Exception {
        Path repo = newRepo();
        write(repo, "base.txt", "v1");
        commitAll(repo, "initial");
        git(repo, "checkout", "-b", "feature");

        assertThat(Dev.collectChangedFiles(repo, "main", true)).isEmpty();
    }

    @Test
    void theUncommittedFlagIsWhatDecidesIfTheWorkingTreeCounts() throws Exception {
        Path repo = newRepo();
        write(repo, "base.txt", "v1");
        commitAll(repo, "initial");
        git(repo, "checkout", "-b", "feature");
        write(repo, "committed.txt", "v1");
        commitAll(repo, "feature work");
        write(repo, "uncommitted.txt", "v1");

        assertThat(Dev.collectChangedFiles(repo, "main", false))
                .containsExactly("committed.txt");
        assertThat(Dev.collectChangedFiles(repo, "main", true))
                .containsExactlyInAnyOrder("committed.txt", "uncommitted.txt");
    }

    @Test
    void commitsBetweenCountsInOneDirection() throws Exception {
        Path repo = newRepo();
        write(repo, "a.txt", "v1");
        commitAll(repo, "one");
        git(repo, "tag", "start");
        write(repo, "b.txt", "v1");
        commitAll(repo, "two");
        write(repo, "c.txt", "v1");
        commitAll(repo, "three");

        assertThat(Dev.commitsBetween(repo, "start", "HEAD")).isEqualTo(2);
        assertThat(Dev.commitsBetween(repo, "HEAD", "start")).isZero();
    }

    // ------------------------------------------------------------------
    // the tools we need, and what gets printed
    // ------------------------------------------------------------------

    @Test
    void commandsAreLookedUpOnThePathOrByPath() throws Exception {
        assertThat(Dev.isOnPath("git")).isTrue();
        assertThat(Dev.isOnPath("definitely-not-a-real-command-zzz")).isFalse();

        Path dir = Files.createTempDirectory("dev-tool-");
        Path executable = dir.resolve("some-tool");
        Files.writeString(executable, "#!/bin/sh\n");
        executable.toFile().setExecutable(true);
        assertThat(Dev.isOnPath(executable.toString())).isTrue();
        assertThat(Dev.isOnPath(dir.resolve("missing").toString())).isFalse();
    }

    /** The fallback that lets the tool work from a plain shell, without `devbox shell`. */
    @Test
    void mavenIsFoundWhereDevboxInstallsIt() throws Exception {
        Path root = Files.createTempDirectory("dev-devbox-");
        assertThat(Dev.devboxMaven(root)).isNull();

        Path bin = root.resolve(".devbox/nix/profile/default/bin");
        Files.createDirectories(bin);
        Path mvn = bin.resolve("mvn");
        Files.writeString(mvn, "#!/bin/sh\n");
        mvn.toFile().setExecutable(true);

        assertThat(Dev.devboxMaven(root)).isEqualTo(mvn.toString());
    }

    @Test
    void theEchoedCommandIsShortenedButTheRealOneIsNot() {
        assertThat(Dev.abbreviate(List.of("/long/path/to/bin/mvn", "-pl", "g:a,g:b,g:c", "-T", "1C", "install")))
                .containsExactly("mvn", "-pl", "<3 modules>", "-T", "1C", "install");
        // nothing to shorten, nothing changed; and a trailing -pl must not blow up
        assertThat(Dev.abbreviate(List.of("mvn", "clean"))).containsExactly("mvn", "clean");
        assertThat(Dev.abbreviate(List.of("mvn", "-pl"))).containsExactly("mvn", "-pl");
    }

    // ------------------------------------------------------------------
    // config file
    // ------------------------------------------------------------------

    @Test
    void configSurvivesARoundTrip() throws Exception {
        Path repo = Files.createTempDirectory("dev-config-");
        Map<String, String> saved = config();
        saved.put("since", "origin/main");
        saved.put("uncommitted", "false");
        saved.put("breadth", "changed");
        saved.put("upstream", "never");
        Dev.saveConfig(repo, saved);

        assertThat(Dev.loadConfig(repo)).isEqualTo(saved);
    }

    @Test
    void configIgnoresCommentsAndBlankLines() throws Exception {
        Path repo = Files.createTempDirectory("dev-config-");
        Files.createDirectories(repo.resolve(".kie-dev"));
        Files.writeString(repo.resolve(".kie-dev/config.properties"),
                "# a comment\n\n  since=origin/main  \n\n! another\nbreadth=changed\n");

        assertThat(Dev.loadConfig(repo))
                .containsExactlyInAnyOrderEntriesOf(Map.of("since", "origin/main", "breadth", "changed"));
    }

    @Test
    void missingConfigLoadsAsEmptyRatherThanFailing() throws Exception {
        Path repo = Files.createTempDirectory("dev-config-");

        assertThat(Dev.loadConfig(repo)).isEmpty();
    }

    // ------------------------------------------------------------------
    // dependency graph parsing
    // ------------------------------------------------------------------

    @Test
    void graphParsesAllRecordTypesAndIgnoresUnknownOnes() throws Exception {
        Path tsv = writeGraph(
                "P\tg:a\t/tmp/a",
                "P\tg:b\t/tmp/b",
                "D\tg:b\tg:a",
                "V\tg:a\t9-SNAPSHOT\tjar",
                "V\tg:b\t9-SNAPSHOT\tpom",
                "L\t/tmp/m2",
                "Z\tsomething\tfrom the future",
                "B\tg:a");

        DepGraph graph = DepGraph.parse(tsv);

        assertThat(graph.gaToDir).containsOnlyKeys("g:a", "g:b");
        assertThat(graph.gaToVersion).containsEntry("g:a", "9-SNAPSHOT");
        assertThat(graph.gaToPackaging).containsEntry("g:b", "pom");
        assertThat(graph.localRepo).isEqualTo(Path.of("/tmp/m2"));
        // D<TAB>dependent<TAB>dependency — b depends on a
        assertThat(graph.upstreamOf.get("g:b")).containsExactly("g:a");
        assertThat(graph.downstreamOf.get("g:a")).containsExactly("g:b");
    }

    @Test
    void graphOfAMissingFileIsEmptyRatherThanFailing() throws Exception {
        DepGraph graph = DepGraph.parse(Path.of("/does/not/exist.tsv"));

        assertThat(graph.gaToDir).isEmpty();
        assertThat(graph.localRepo).isNull();
    }

    @Test
    void installedArtifactPathFollowsMavenRepositoryLayout() throws Exception {
        Path tsv = writeGraph(
                "P\torg.kie:kie-api\t/tmp/kie-api",
                "P\torg.kie:kie-bom\t/tmp/kie-bom",
                "V\torg.kie:kie-api\t999-SNAPSHOT\tjar",
                "V\torg.kie:kie-bom\t999-SNAPSHOT\tpom",
                "L\t/home/u/.m2/repository");

        DepGraph graph = DepGraph.parse(tsv);

        assertThat(graph.installedArtifact("org.kie:kie-api"))
                .isEqualTo(Path.of("/home/u/.m2/repository/org/kie/kie-api/999-SNAPSHOT/kie-api-999-SNAPSHOT.jar"));
        // pom packaging installs only a pom
        assertThat(graph.installedArtifact("org.kie:kie-bom"))
                .isEqualTo(Path.of("/home/u/.m2/repository/org/kie/kie-bom/999-SNAPSHOT/kie-bom-999-SNAPSHOT.pom"));
    }

    // ------------------------------------------------------------------
    // freshness: is an installed artifact still trustworthy
    // ------------------------------------------------------------------

    @Test
    void anArtifactThatWasNeverInstalledNeedsRebuilding() throws Exception {
        Fixture f = new Fixture();
        f.module("g:a", "a");   // sources, but nothing installed

        assertThat(Dev.outOfDateReason(f.graph(), "g:a")).isEqualTo("not installed");
    }

    @Test
    void anArtifactNewerThanItsSourcesIsUpToDate() throws Exception {
        Fixture f = new Fixture();
        Path module = f.module("g:a", "a");
        f.install("g:a", 2_000_000);

        assertThat(Dev.outOfDateReason(f.graph(), "g:a")).isNull();
    }

    @Test
    void anArtifactOlderThanItsSourcesNeedsRebuilding() throws Exception {
        Fixture f = new Fixture();
        Path module = f.module("g:a", "a");
        f.install("g:a", 1_000_000);
        touch(module.resolve("src/Main.java"), 2_000_000);

        assertThat(Dev.outOfDateReason(f.graph(), "g:a")).isEqualTo("older than its sources");
    }

    /** Otherwise every aggregator would look stale whenever any of its children changed. */
    @Test
    void aParentIsNotStaleJustBecauseANestedModuleChanged() throws Exception {
        Fixture f = new Fixture();
        Path parent = f.module("g:parent", "parent");
        Path child = f.module("g:child", "parent/child");
        touch(parent.resolve("pom.xml"), 1_000_000);
        f.install("g:parent", 2_000_000);
        touch(child.resolve("src/Main.java"), 3_000_000);

        assertThat(Dev.outOfDateReason(f.graph(), "g:parent")).isNull();
    }

    @Test
    void buildOutputDoesNotCountAsASourceChange() throws Exception {
        Fixture f = new Fixture();
        Path module = f.module("g:a", "a");
        touch(module.resolve("src/Main.java"), 1_000_000);
        f.install("g:a", 2_000_000);
        touch(module.resolve("target/classes/Main.class"), 3_000_000);

        assertThat(Dev.outOfDateReason(f.graph(), "g:a")).isNull();
    }

    // ------------------------------------------------------------------
    // which dependencies the upstream pass builds
    // ------------------------------------------------------------------

    @Test
    void dependenciesOfExcludesTheModulesBeingBuilt() throws Exception {
        // c -> b -> a  (c depends on b, b depends on a)
        DepGraph graph = DepGraph.parse(writeGraph(
                "P\tg:a\t/tmp/a", "P\tg:b\t/tmp/b", "P\tg:c\t/tmp/c",
                "D\tg:b\tg:a", "D\tg:c\tg:b"));

        assertThat(graph.dependenciesOf(List.of("g:c"))).containsExactly("g:a", "g:b");
        assertThat(graph.dependenciesOf(List.of("g:b", "g:c"))).containsExactly("g:a");
        assertThat(graph.dependenciesOf(List.of("g:a"))).isEmpty();
    }

    @Test
    void rebuildingADependencyAlsoRebuildsItsConsumers() throws Exception {
        // c -> b -> a, and the build itself is some module d that depends on c
        DepGraph graph = DepGraph.parse(writeGraph(
                "P\tg:a\t/tmp/a", "P\tg:b\t/tmp/b", "P\tg:c\t/tmp/c",
                "D\tg:b\tg:a", "D\tg:c\tg:b"));
        Set<String> candidates = Set.of("g:a", "g:b", "g:c");

        // a is stale, so b and c — built from it — cannot be trusted either
        assertThat(graph.rebuildClosure(candidates, Set.of("g:a")))
                .containsExactlyInAnyOrder("g:a", "g:b", "g:c");
        // but a stale c invalidates nothing below it
        assertThat(graph.rebuildClosure(candidates, Set.of("g:c")))
                .containsExactly("g:c");
    }

    @Test
    void theClosureStopsAtModulesTheMainPassAlreadyBuilds() throws Exception {
        DepGraph graph = DepGraph.parse(writeGraph(
                "P\tg:a\t/tmp/a", "P\tg:b\t/tmp/b",
                "D\tg:b\tg:a"));

        // b is not a candidate — it is in the main pass, which rebuilds it anyway
        assertThat(graph.rebuildClosure(Set.of("g:a"), Set.of("g:a")))
                .containsExactly("g:a");
    }

    @Test
    void nothingStaleMeansNoUpstreamPass() throws Exception {
        DepGraph graph = DepGraph.parse(writeGraph("P\tg:a\t/tmp/a"));

        assertThat(graph.rebuildClosure(Set.of("g:a"), Set.of())).isEmpty();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** A throwaway reactor: module directories, a local repository, and a graph tying them together. */
    private static final class Fixture {
        /** Baseline modification time for every fixture file; tests move times relative to it. */
        static final long BASE_TIME = 1_000_000;

        private final Path root = Files.createTempDirectory("dev-fixture-");
        private final Path localRepo = root.resolve("m2");
        private final List<String> records = new java.util.ArrayList<>();

        Fixture() throws IOException {
            Files.createDirectories(localRepo);
            records.add("L\t" + localRepo);
        }

        /**
         * Creates a module directory with a pom and one source file, and registers it.
         * Every file starts at {@link #BASE_TIME} so that a test only has to set the times
         * it actually cares about — otherwise files left at the current time would swamp
         * the artifact times the tests choose.
         */
        Path module(String ga, String relativeDir) throws IOException {
            Path dir = root.resolve(relativeDir);
            Files.createDirectories(dir.resolve("src"));
            Files.writeString(dir.resolve("pom.xml"), "<project/>");
            Files.writeString(dir.resolve("src/Main.java"), "class Main {}");
            touch(dir.resolve("pom.xml"), BASE_TIME);
            touch(dir.resolve("src/Main.java"), BASE_TIME);
            records.add("P\t" + ga + "\t" + dir);
            records.add("V\t" + ga + "\t1-SNAPSHOT\tjar");
            return dir;
        }

        /** Installs the module's artifact into the fixture's local repository, at a given time. */
        void install(String ga, long mtimeMillis) throws IOException {
            String groupId = ga.substring(0, ga.indexOf(':'));
            String artifactId = ga.substring(ga.indexOf(':') + 1);
            Path dir = localRepo;
            for (String segment : groupId.split("\\.")) {
                dir = dir.resolve(segment);
            }
            Path artifact = dir.resolve(artifactId).resolve("1-SNAPSHOT")
                    .resolve(artifactId + "-1-SNAPSHOT.jar");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, "jar");
            Files.setLastModifiedTime(artifact, FileTime.fromMillis(mtimeMillis));
        }

        DepGraph graph() throws IOException {
            Path tsv = root.resolve("dep-graph.tsv");
            Files.write(tsv, records);
            return DepGraph.parse(tsv);
        }
    }

    /** An input stream of the given key bytes, as a terminal in raw mode would deliver them. */
    private static java.io.InputStream keys(int... bytes) {
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            buf[i] = (byte) bytes[i];
        }
        return new java.io.ByteArrayInputStream(buf);
    }

    private static Path writeGraph(String... records) throws IOException {
        Path tsv = Files.createTempFile("dev-graph-", ".tsv");
        Files.write(tsv, List.of(records));
        return tsv;
    }

    private static void touch(Path file, long mtimeMillis) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.writeString(file, "x");
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtimeMillis));
    }

    private static Map<String, String> config(String... keyValuePairs) {
        Map<String, String> config = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            config.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return config;
    }

    // ------------------------------------------------------------------
    // git helpers
    // ------------------------------------------------------------------

    private static Path newRepo() throws Exception {
        Path repo = Files.createTempDirectory("dev-repo-");
        git(repo, "init");
        // Not `init -b main`: that needs git 2.28+, and this works everywhere.
        git(repo, "symbolic-ref", "HEAD", "refs/heads/main");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "config", "user.name", "Dev Test");
        git(repo, "config", "commit.gpgsign", "false");
        return repo;
    }

    private static void write(Path repo, String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void commitAll(Path repo, String message) throws Exception {
        git(repo, "add", "-A");
        git(repo, "commit", "-m", message);
    }

    private static void git(Path repo, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed (rc=" + rc + "):\n" + output);
        }
    }
}
