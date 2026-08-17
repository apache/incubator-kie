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

import org.junit.jupiter.api.Test;
import org.junit.platform.console.ConsoleLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the {@code dev} target in the repository Makefile — {@code make dev mvn},
 * {@code make dev scope} and {@code make dev config}.
 *
 * These run {@code make --dry-run}, so they assert on the commands Make *would* run
 * without running them — no Maven, no builds, no network.
 *
 * Most of what is tested here is Make's command-line parsing, which is subtle enough to
 * be worth pinning down: the subcommand and every Maven argument reach Make as goals of
 * its own, `--` is what stops it from swallowing `-DskipTests` as one of its options, and
 * the catch-all rule that absorbs the leftovers must stay scoped to `dev`, so that a
 * mistyped target still fails loudly.
 *
 * Run:
 *   jbang script/dev/tests/MakefileTest.java
 */
public class MakefileTest {

    static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    static final String SCRIPT = "script/dev/Dev.java";

    public static void main(String[] args) {
        ConsoleLauncher.main(new String[]{
            "execute",
            "--select-class=" + MakefileTest.class.getName(),
            "--exclude-engine=junit-vintage",
            "--fail-if-no-tests"
        });
    }

    @Test
    void helpShowsEverySubcommand() throws Exception {
        Result help = make("help");

        assertThat(help.rc).isZero();
        assertThat(strippedOf(help.output))
                .contains("make dev ")
                .contains("make dev scope")
                .contains("make dev config")
                .contains("make dev mvn")
                .contains("docs/DEV.md");
    }

    @Test
    void eachSubcommandReachesTheScriptAsItsFirstArgument() throws Exception {
        assertThat(make("-n", "dev", "mvn").output).contains(SCRIPT + " mvn");
        assertThat(make("-n", "dev", "scope").output).contains(SCRIPT + " scope");
        assertThat(make("-n", "dev", "config").output).contains(SCRIPT + " config");
    }

    @Test
    void mavenFlagsSurviveMakesOwnOptionParsing() throws Exception {
        Result r = make("-n", "dev", "mvn", "--", "install", "-DskipTests", "-Pfull", "-T1C");

        assertThat(r.rc).isZero();
        assertThat(r.output).contains("install -DskipTests -Pfull -T1C");
    }

    @Test
    void overridesArePassedAsEnvironmentVariables() throws Exception {
        Result r = make("-n", "dev", "since=HEAD", "breadth=changed");

        assertThat(r.rc).isZero();
        assertThat(r.output).contains("KIE_DEV_SINCE=HEAD").contains("KIE_DEV_BREADTH=changed");
    }

    @Test
    void leftoverMavenArgumentsDoNotRunAnythingThemselves() throws Exception {
        Result r = make("-n", "dev", "mvn", "--", "clean", "install");

        // Every printed command is either the script invocation or the catch-all no-op.
        List<String> commands = r.output.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.equals(":"))
                .toList();
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0)).contains(SCRIPT);
    }

    /**
     * The catch-all that absorbs subcommands and Maven arguments is deliberately scoped
     * to `dev`. Outside it, a mistyped target must still be an error.
     */
    @Test
    void aMistypedTargetStillFails() throws Exception {
        Result r = make("-n", "dev-buld");

        assertThat(r.rc).isNotZero();
        assertThat(r.output).contains("dev-buld");
    }

    @Test
    void theMakefileHasNoTargetsThatCouldCollideWithMavenGoals() throws Exception {
        // Subcommands and Maven goals both reach Make as goals, so a target named
        // `clean`, `install` or `mvn` would silently run instead of being forwarded.
        List<String> targets = new ArrayList<>();
        for (String line : Files.readAllLines(REPO_ROOT.resolve("Makefile"))) {
            if (line.startsWith(".PHONY:")) {
                targets.add(line.substring(".PHONY:".length()).strip());
            }
        }

        assertThat(targets).isNotEmpty();
        assertThat(targets)
                .doesNotContain("clean", "install", "test", "verify", "package", "deploy", "validate")
                .doesNotContain("mvn", "scope", "config");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private record Result(int rc, String output) {}

    private static Result make(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getenv().getOrDefault("MAKE", "make"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd)
                .directory(REPO_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        return new Result(p.waitFor(), output);
    }

    /** Drops the ANSI colour codes `make help` emits, so assertions can match plain text. */
    private static String strippedOf(String output) {
        return output.replaceAll("\\[[0-9;]*m", "");
    }
}
