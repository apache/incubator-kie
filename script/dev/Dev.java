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
//SOURCES ../ci/CiComputeBuildScopes.java

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Builds only the Maven modules your changes affect, using the same scope computation CI
 * runs on pull requests ({@link CiComputeBuildScopes} and {@code DepGraph}, pulled in
 * above via jbang {@code //SOURCES} rather than copied, so the two cannot disagree).
 *
 * <p>Driven by four settings in {@code .kie-dev/config}: <b>since</b> (the point in
 * history to measure from), <b>uncommitted</b> (whether the working tree counts),
 * <b>breadth</b> (how far to fan out) and <b>upstream</b> (whether to rebuild
 * dependencies first). The first run writes that file with sensible defaults.
 *
 * <p>See {@code docs/DEV.md}.
 */
public class Dev {

    // ------------------------------------------------------------------
    // settings and state
    // ------------------------------------------------------------------

    static final Path STATE_DIR = Paths.get(".kie-dev");
    static final Path CONFIG_FILE = STATE_DIR.resolve("config.properties");
    private static final Path GRAPH_FILE = STATE_DIR.resolve("dep-graph.tsv");
    private static final Path CHANGED_FILES_FILE = STATE_DIR.resolve("changed-files.txt");
    private static final Path PL_UPSTREAM_FILE = STATE_DIR.resolve("pl-upstream.txt");
    private static final Path PL_AFFECTED_FILE = STATE_DIR.resolve("pl-affected.txt");
    private static final Path PL_CHANGED_FILE = STATE_DIR.resolve("pl-changed.txt");
    private static final Path MODULES_FILE = STATE_DIR.resolve("modules-to-build.txt");
    private static final Path UPSTREAM_FILE = STATE_DIR.resolve("upstream-modules.txt");
    private static final Path LAST_COMMAND_FILE = STATE_DIR.resolve("last-maven-command.txt");

    static final String KEY_SINCE = "since";
    static final String KEY_UNCOMMITTED = "uncommitted";
    static final String KEY_BREADTH = "breadth";
    static final String KEY_UPSTREAM = "upstream";
    /**
     * The point in history a build measures from: any git ref. Used as
     * {@code merge-base(<ref>, HEAD)}, the same three-dot comparison CI makes, so that
     * work that landed on the base branch after you started is not counted as yours.
     *
     * <p>The default, {@code HEAD}, falls out of that with no special case: its merge
     * base with HEAD is HEAD, so nothing committed is included and you are left with the
     * working tree alone.
     */
    private static final String SINCE_DEFAULT = "HEAD";

    /** How far a build fans out from what changed. */
    static final String BREADTH_CHANGED = "changed";
    private static final String BREADTH_AFFECTED = "affected";
    private static final List<String> BREADTH_VALUES = List.of(BREADTH_CHANGED, BREADTH_AFFECTED);

    /** Whether to rebuild dependencies before the main build. */
    private static final String UPSTREAM_AUTO = "auto";
    private static final String UPSTREAM_ALWAYS = "always";
    private static final String UPSTREAM_NEVER = "never";
    private static final List<String> UPSTREAM_VALUES = List.of(UPSTREAM_AUTO, UPSTREAM_ALWAYS, UPSTREAM_NEVER);

    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");

    /**
     * The everyday case: what you have edited but not committed, and everything
     * downstream of it, which is what CI would check.
     *
     * <p>A {@link LinkedHashMap} rather than {@code Map.of} so that the config file and
     * the line printed on first run come out in a sensible order.
     */
    private static final Map<String, String> DEFAULTS = defaults();

    private static Map<String, String> defaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(KEY_SINCE, SINCE_DEFAULT);
        defaults.put(KEY_UNCOMMITTED, "true");
        defaults.put(KEY_BREADTH, BREADTH_AFFECTED);
        defaults.put(KEY_UPSTREAM, UPSTREAM_AUTO);
        return Collections.unmodifiableMap(defaults);
    }

    /**
     * Flags that make a build fast rather than thorough: parallel, quiet about downloads,
     * reporting every broken module rather than stopping at the first, and skipping tests
     * and the reporting plugins.
     *
     * <p>These mirror the upstream step in .github/workflows/ci.yaml. Keep the two in
     * step: if CI's upstream step changes, this should change with it.
     */
    private static final List<String> FAST_FLAGS = List.of(
            "-T", "1C", "--no-transfer-progress", "-fae",
            "-DskipTests", "-DskipITs",
            "-Denforcer.skip=true", "-Dcheckstyle.skip=true",
            "-Dformatter.skip=true", "-Darchunit.skip=true");

    /** What a bare {@code make dev} runs. Anything else goes through {@code make dev mvn --}. */
    private static final List<String> DEFAULT_MVN_ARGS =
            java.util.stream.Stream.concat(FAST_FLAGS.stream(), java.util.stream.Stream.of("install")).toList();

    /** The Maven this run will use, resolved once by {@link #requireTools}. */
    private static String maven;

    /** Why the upstream pass is doing what it is doing, shown alongside the module count. */
    private static String upstreamSummary;

    // ------------------------------------------------------------------
    // everything the user sees
    // ------------------------------------------------------------------

    /**
     * Kept together so that the tool's vocabulary can be read and reviewed on its own.
     * Colour belongs here too: how something reads and how it looks are one decision.
     */
    private static final class Text {

        static final String USAGE = String.join(System.lineSeparator(),
                "Build only the modules your changes affect.",
                "",
                "  " + bold("make dev") + "                    build them, fast (no tests)",
                "  " + bold("make dev scope") + "              show what would be built, build nothing",
                "  " + bold("make dev config") + "             show the current settings",
                "  " + bold("make dev mvn -- <args>") + "      build them with your own Maven arguments",
                "",
                "Examples:",
                "  make dev since=origin/main breadth=changed",
                "  make dev mvn -- install                 build and run tests",
                "",
                "See docs/DEV.md.");

        static final String MVN_NEEDS_ARGUMENTS = red(
                "`make dev mvn` needs Maven arguments after `--`. For the usual build, run `make dev`.");

        static String unknownCommand(String command) { return red("Unknown command: " + command); }

        static String notARepositoryRoot(Path cwd) {
            return "This must run from the repository root (no pom.xml/.git in " + cwd + ").";
        }

        // --- the summary above a build ---------------------------------

        static String field(String label, String value) {
            return bold(String.format("%-15s", label + ":")) + value;
        }

        static String since(String sinceRef) {
            return ref(sinceRef) + ("HEAD".equals(sinceRef) ? dim(" — your uncommitted work only") : "");
        }

        static String localChanges(boolean included, int count) {
            return field("Local changes", included
                    ? count + (count == 1 ? " uncommitted file included" : " uncommitted files included")
                    : dim("ignored — only committed work counts"));
        }

        static String baseIsStale(String base, int behindBy) {
            String remote = "origin/" + base;
            return "  " + ref(base) + yellow(" is " + behindBy + " commit(s) behind ") + ref(remote)
                    + yellow(" — that widens the build. Pull it, or use ") + ref(remote)
                    + yellow(" as the base.");
        }

        static final String DETERMINING_MODULES = "Determining which modules to build.";
        static final String NOTHING_CHANGED = yellow("Nothing has changed since that point — nothing to build.");
        static final String NO_MODULE_MATCHED = yellow("No reactor module matched the changed files — nothing to build.");
        static final String DRY_RUN_HINT = "Run " + bold("make dev") + " to build these.";
        static final String MODULES_TO_BUILD = "Modules to build";
        static final String UPSTREAM_TO_REBUILD = "Upstream modules to rebuild first (tests skipped)";

        static String moduleListLine(String title, int count, Path listFile) {
            return bold(title + ": ") + count + dim("  (" + listFile + ")");
        }

        static String detail(String message) { return dim("    " + message); }

        static String commandEcho(String command) { return bold("$ ") + command; }

        static String moduleCount(int count) {
            return "<" + count + (count == 1 ? " module>" : " modules>");
        }

        // --- upstream modules -------------------------------------------

        static final String LOCAL_REPOSITORY_UNKNOWN =
                yellow("Could not determine the local Maven repository — rebuilding all upstream modules.");

        static String allUpstreamUpToDate(int count) {
            return "all " + count + " upstream modules are installed and up to date — nothing to rebuild";
        }

        static String upstreamReasons(long missing, long stale, int downstream) {
            List<String> reasons = new ArrayList<>();
            if (missing > 0) reasons.add(missing + " not installed");
            if (stale > 0) reasons.add(stale + " older than their sources");
            if (downstream > 0) reasons.add(downstream + " downstream of those");
            return String.join(", ", reasons);
        }

        static final String UPSTREAM_OVERRIDE_HINT =
                "skip with `upstream=never`, force all with `upstream=always`";

        static String upstreamPassFailed(int exitCode) {
            return red("Upstream pass failed (mvn exit " + exitCode + ").");
        }

        static final String COULD_NOT_RESOLVE_HINT =
                yellow("If Maven could not resolve a module of this repository, try "
                        + bold("make dev upstream=always") + " to rebuild all upstream modules.");

        // --- settings ----------------------------------------------------

        static String wroteConfig(Path file) {
            return "First run — wrote " + bold(file.toString());
        }

        static String settings(Map<String, String> config) {
            return config.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "  " + b)
                    .orElse("");
        }

        static final String EDIT_CONFIG_HINT =
                "edit it, or override for one run: " + bold("make dev since=origin/main");

        static String recordedBase(String branch, String base) {
            return dim("Recorded ") + ref(base) + dim(" as the base of ") + ref(branch)
                    + dim(" in " + CONFIG_FILE);
        }

        static String invalidValue(String key, String value, List<String> valid) {
            return red("Invalid " + key + ": '" + value + "'. Valid values: " + valid);
        }

        static String noMergeBase(String base) {
            return red("Cannot find a merge base between ") + ref(base) + red(" and ") + ref("HEAD")
                    + red(". Fetch it, or edit " + CONFIG_FILE + ".");
        }

        // --- the tools we need -------------------------------------------

        static String usingMaven(String binary) { return dim("Using Maven at " + binary); }

        static String missingTools(List<String> commands, boolean devboxAvailable) {
            return red("Not found on your PATH: " + String.join(", ", commands))
                    + System.lineSeparator()
                    + (devboxAvailable ? "Install them with " + bold("devbox install") + ", or "
                                       : "Install them, or ")
                    + "point at one explicitly: " + bold("MVN=/path/to/mvn make dev");
        }
    }

    // ------------------------------------------------------------------
    // commands
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Path root = requireRepoRoot();
        String command = args.length > 0 ? args[0] : "";
        List<String> mvnArgs = args.length > 1 ? List.of(args).subList(1, args.length) : List.of();
        Map<String, String> config = loadConfig(root);

        switch (command) {
            case "config" -> say(Text.settings(config.isEmpty() ? DEFAULTS : config)
                    + System.lineSeparator() + Text.EDIT_CONFIG_HINT);
            case "scope" -> run(root, config, mvnArgs, false);
            // Bare `make dev` is the everyday build.
            case "" -> run(root, config, DEFAULT_MVN_ARGS, true);
            // `mvn` is the escape hatch, and it insists on being told what to run — a bare
            // `make dev mvn` would otherwise be a second, silent spelling of `make dev`.
            case "mvn" -> {
                if (mvnArgs.isEmpty()) {
                    sayErr(Text.MVN_NEEDS_ARGUMENTS);
                    System.err.println(Text.USAGE);
                    System.exit(2);
                }
                run(root, config, mvnArgs, true);
            }
            default -> {
                sayErr(Text.unknownCommand(command));
                System.err.println(Text.USAGE);
                System.exit(2);
            }
        }
    }

    private static void run(Path root, Map<String, String> config, List<String> mvnArgs, boolean build)
            throws Exception {
        requireTools(root);
        firstRun(root, config);

        String since = Optional.ofNullable(System.getenv("KIE_DEV_SINCE"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> config.getOrDefault(KEY_SINCE, SINCE_DEFAULT))
                .strip();
        boolean includeUncommitted = "true".equals(setting(config, KEY_UNCOMMITTED, "KIE_DEV_UNCOMMITTED", BOOLEAN_VALUES));
        String breadth = setting(config, KEY_BREADTH, "KIE_DEV_BREADTH", BREADTH_VALUES);

        List<String> changedFiles = collectChangedFiles(root, since, includeUncommitted);

        blank();
        say(Text.field("Since", Text.since(since)));
        warnIfRefIsStale(root, since);
        say(Text.localChanges(includeUncommitted, includeUncommitted ? dirtyFiles(root).size() : 0));
        say(Text.field("Breadth", breadth));
        say(Text.field("Files", changedFiles.size() + " changed in total"));

        if (changedFiles.isEmpty()) {
            blank();
            say(Text.NOTHING_CHANGED);
            return;
        }

        Files.createDirectories(root.resolve(STATE_DIR));
        Files.write(root.resolve(CHANGED_FILES_FILE), changedFiles);
        Files.deleteIfExists(root.resolve(LAST_COMMAND_FILE));

        computeScopes(root);

        List<String> toBuild = readLines(root.resolve(breadth.equals(BREADTH_CHANGED) ? PL_CHANGED_FILE : PL_AFFECTED_FILE));
        List<String> toBuildUpstream = resolveUpstreamPass(root, config, toBuild);

        blank();
        // `scope` exists to show you the list, so it prints it; a build prints the count
        // and gets out of the way of Maven.
        printModules(root, Text.MODULES_TO_BUILD, toBuild, MODULES_FILE, !build);
        if (!toBuildUpstream.isEmpty()) {
            printModules(root, Text.UPSTREAM_TO_REBUILD, toBuildUpstream, UPSTREAM_FILE, !build);
            say(Text.detail(upstreamSummary));
            say(Text.detail(Text.UPSTREAM_OVERRIDE_HINT));
        } else if (upstreamSummary != null) {
            say(Text.detail(upstreamSummary));
        }

        if (toBuild.isEmpty()) {
            blank();
            say(Text.NO_MODULE_MATCHED);
            return;
        }
        if (!build) {
            blank();
            say(Text.DRY_RUN_HINT);
            return;
        }

        if (!toBuildUpstream.isEmpty()) {
            // The point of this pass is to populate the local repository, not to verify
            // anything, so it always uses the fast flags whatever was asked for.
            List<String> upstreamCmd = new ArrayList<>(FAST_FLAGS);
            upstreamCmd.addAll(List.of("-pl", String.join(",", toBuildUpstream), "install"));
            int rc = runMaven(root, upstreamCmd);
            if (rc != 0) {
                sayErr(Text.upstreamPassFailed(rc));
                System.exit(rc);
            }
        }

        List<String> cmd = new ArrayList<>(List.of("-pl", String.join(",", toBuild)));
        cmd.addAll(mvnArgs.isEmpty() ? DEFAULT_MVN_ARGS : mvnArgs);
        int rc = runMaven(root, cmd);
        if (rc != 0 && toBuildUpstream.isEmpty()) {
            // The freshness check thought everything was fine, so if Maven still could not
            // resolve a reactor module, point at the way to force it.
            blank();
            sayErr(Text.COULD_NOT_RESOLVE_HINT);
        }
        System.exit(rc);
    }

    // ------------------------------------------------------------------
    // which upstream modules must be rebuilt first
    // ------------------------------------------------------------------

    /**
     * A partial build can only resolve what is already installed, and only gives correct
     * results if what is installed matches the current sources. Both go wrong routinely —
     * a fresh clone has nothing installed, and after switching branches what is installed
     * no longer matches — so the untrustworthy ones are rebuilt first.
     */
    private static List<String> resolveUpstreamPass(Path root, Map<String, String> config, List<String> toBuild)
            throws Exception {
        String mode = setting(config, KEY_UPSTREAM, "KIE_DEV_UPSTREAM", UPSTREAM_VALUES);
        if (mode.equals(UPSTREAM_NEVER)) {
            return List.of();
        }

        DepGraph graph = DepGraph.parse(root.resolve(GRAPH_FILE));
        List<String> upstream = graph.dependenciesOf(toBuild);
        if (upstream.isEmpty()) {
            return List.of();
        }
        if (mode.equals(UPSTREAM_ALWAYS)) {
            return upstream;
        }
        if (graph.localRepo == null) {
            say(Text.LOCAL_REPOSITORY_UNKNOWN);
            return upstream;
        }

        Map<String, String> reasons = new LinkedHashMap<>();
        for (String ga : upstream) {
            String reason = outOfDateReason(graph, ga);
            if (reason != null) {
                reasons.put(ga, reason);
            }
        }
        Set<String> needed = graph.rebuildClosure(new LinkedHashSet<>(upstream), reasons.keySet());
        if (needed.isEmpty()) {
            upstreamSummary = Text.allUpstreamUpToDate(upstream.size());
            return List.of();
        }

        long missing = reasons.values().stream().filter(r -> r.equals("not installed")).count();
        upstreamSummary = Text.upstreamReasons(missing, reasons.size() - missing, needed.size() - reasons.size());
        // Keep the caller's ordering; Maven works out the actual build order itself.
        return upstream.stream().filter(needed::contains).toList();
    }

    /**
     * Why {@code ga}'s installed artifact cannot be trusted, or null if it can. An artifact
     * older than a file in its own module was built from different sources — which is what
     * happens after switching branches, pulling, or editing a dependency by hand.
     */
    static String outOfDateReason(DepGraph graph, String ga) throws IOException {
        Path artifact = graph.installedArtifact(ga);
        if (artifact == null || !Files.isRegularFile(artifact)) {
            return "not installed";
        }
        Path dir = graph.gaToDir.get(ga);
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        return hasSourceNewerThan(graph, dir, Files.getLastModifiedTime(artifact).toMillis())
                ? "older than its sources"
                : null;
    }

    /**
     * Walks a module's own files, stopping at the first one newer than the artifact.
     * Nested module directories are skipped — they are checked as modules in their own
     * right, and an aggregator would otherwise look stale whenever any child changed.
     */
    static boolean hasSourceNewerThan(DepGraph graph, Path moduleDir, long mtime) throws IOException {
        boolean[] newer = {false};
        Files.walkFileTree(moduleDir, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (dir.equals(moduleDir)) return java.nio.file.FileVisitResult.CONTINUE;
                String name = dir.getFileName().toString();
                boolean prune = name.equals("target") || name.equals("node_modules")
                        || name.startsWith(".") || graph.moduleDirs.contains(dir);
                return prune ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (attrs.lastModifiedTime().toMillis() > mtime) {
                    newer[0] = true;
                    return java.nio.file.FileVisitResult.TERMINATE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return newer[0];
    }

    // ------------------------------------------------------------------
    // running Maven
    // ------------------------------------------------------------------

    /** Delegates to the CI scope computation, reusing the cached dependency graph. */
    private static void computeScopes(Path root) throws Exception {
        blank();
        say(Text.DETERMINING_MODULES);
        System.setProperty("DEP_GRAPH_EXTRACTOR__OUTPUT_FILE", root.resolve(GRAPH_FILE).toString());
        System.setProperty("DEP_GRAPH_EXTRACTOR__REUSE_IF_FRESH", "true");
        CiComputeBuildScopes.main(new String[] {
                CHANGED_FILES_FILE.toString(),
                PL_UPSTREAM_FILE.toString(),
                PL_AFFECTED_FILE.toString(),
                PL_CHANGED_FILE.toString()
        });
    }

    private static int runMaven(Path root, List<String> args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(maven);
        cmd.addAll(args);

        // The -pl list runs to thousands of characters, which buries the part of the
        // command you actually read. Show it abbreviated and keep the real one on disk.
        Path log = root.resolve(LAST_COMMAND_FILE);
        Files.createDirectories(log.getParent());
        Files.writeString(log, String.join(" ", cmd) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        blank();
        say(Text.commandEcho(String.join(" ", abbreviate(cmd))));
        say(Text.detail("(" + LAST_COMMAND_FILE + ")"));
        blank();
        return new ProcessBuilder(cmd).directory(root.toFile()).inheritIO().start().waitFor();
    }

    /** The binary by name rather than absolute path, and {@code -pl} as a count. */
    static List<String> abbreviate(List<String> cmd) {
        List<String> shown = new ArrayList<>(cmd);
        if (!shown.isEmpty()) {
            shown.set(0, Paths.get(shown.get(0)).getFileName().toString());
        }
        int flag = shown.indexOf("-pl");
        if (flag >= 0 && flag + 1 < shown.size()) {
            shown.set(flag + 1, Text.moduleCount(shown.get(flag + 1).split(",").length));
        }
        return shown;
    }

    /**
     * Fails early and legibly when something this run needs is not installed, rather than
     * letting a missing `mvn` surface as a ProcessBuilder stack trace.
     *
     * <p>Maven is whatever {@code MVN} points at, else your own on {@code PATH}, else the
     * copy devbox installed for this repository — preferring yours, since that is the one
     * your settings and mirrors are set up for.
     */
    private static void requireTools(Path root) {
        List<String> missing = new ArrayList<>();
        if (!isOnPath("git")) {
            missing.add("git");
        }
        maven = resolveMaven(root);
        if (maven == null) {
            missing.add(mvnName());
        } else {
            // CiComputeBuildScopes runs Maven too, and reads this as a system property.
            System.setProperty("MVN", maven);
            if (!maven.equals(mvnName())) {
                say(Text.usingMaven(maven));
            }
        }
        if (!missing.isEmpty()) {
            sayErr(Text.missingTools(missing, Files.isRegularFile(root.resolve("devbox.json"))));
            System.exit(127);
        }
    }

    static String resolveMaven(Path root) {
        String explicit = System.getenv("MVN");
        if (explicit != null && !explicit.isBlank()) {
            return isOnPath(explicit) ? explicit : null;
        }
        return isOnPath(mvnName()) ? mvnName() : devboxMaven(root);
    }

    /** Maven as installed by devbox for this repository, or null if it is not there. */
    static String devboxMaven(Path root) {
        Path candidate = root.resolve(".devbox/nix/profile/default/bin").resolve(mvnName());
        return Files.isExecutable(candidate) ? candidate.toString() : null;
    }

    private static String mvnName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "mvn.cmd" : "mvn";
    }

    /** Whether a command can be run: a path to an executable, or a bare name on {@code PATH}. */
    static boolean isOnPath(String command) {
        if (command.contains("/") || command.contains(java.io.File.separator)) {
            return Files.isExecutable(Path.of(command));
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank() && Files.isExecutable(Path.of(entry).resolve(command))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // git — every command this tool runs, all of them read-only
    // ------------------------------------------------------------------

    /**
     * The files a build should consider: everything committed since a starting point, plus
     * — unless asked otherwise — whatever is not committed yet.
     */
    static List<String> collectChangedFiles(Path root, String since, boolean includeUncommitted) throws Exception {
        String from = requireMergeBase(root, since);
        Set<String> files = new LinkedHashSet<>(git(root, "diff", "--name-only", from, "HEAD"));
        if (includeUncommitted) {
            files.addAll(dirtyFiles(root));
        }
        return new ArrayList<>(files);
    }

    /** Tracked modifications (staged and unstaged, including deletions) plus untracked files. */
    static List<String> dirtyFiles(Path root) throws Exception {
        List<String> files = new ArrayList<>(git(root, "diff", "--name-only", "HEAD"));
        files.addAll(git(root, "ls-files", "--others", "--exclude-standard"));
        return files;
    }

    static String currentBranch(Path root) throws Exception {
        String branch = first(git(root, "rev-parse", "--abbrev-ref", "HEAD"));
        return branch == null ? "HEAD" : branch;
    }

    private static String requireMergeBase(Path root, String ref) throws Exception {
        String mergeBase = first(git(root, "merge-base", ref, "HEAD"));
        if (mergeBase == null) {
            sayErr(Text.noMergeBase(ref));
            System.exit(1);
        }
        return mergeBase;
    }

    /** Says so when the build is measured from a local branch that has gone stale. */
    private static void warnIfRefIsStale(Path root, String base) throws Exception {
        if (base.contains("/") || base.equals("HEAD")) {
            return;
        }
        String remote = "origin/" + base;
        if (git(root, "rev-parse", "--verify", "--quiet", remote).isEmpty()) {
            return;
        }
        int behind = commitsBetween(root, base, remote);
        if (behind > 0) {
            say(Text.baseIsStale(base, behind));
        }
    }

    /** How many commits {@code to} is ahead of {@code from}. */
    static int commitsBetween(Path root, String from, String to) throws Exception {
        String count = first(git(root, "rev-list", "--count", from + ".." + to));
        return count == null ? 0 : Integer.parseInt(count);
    }

    /**
     * Runs git in {@code root} and returns its non-blank output lines. A non-zero exit is
     * reported as no output rather than as a failure: for probes like "does this ref
     * exist", "no" is an answer, not an error.
     */
    private static List<String> git(Path root, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(root.toFile()).start();
        List<String> lines = new ArrayList<>();
        try (var r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) lines.add(line.strip());
            }
        }
        p.getErrorStream().readAllBytes();
        return p.waitFor() == 0 ? lines : List.of();
    }

    private static String first(List<String> lines) {
        return lines.isEmpty() ? null : lines.get(0);
    }

    // ------------------------------------------------------------------
    // the config file
    // ------------------------------------------------------------------

    /**
     * The saved settings, or an empty map when nothing has been saved yet.
     *
     * <p>A {@code .properties} file, read by {@link Properties}: it is the format Java
     * already knows, so there is no parser here to get wrong, and editors highlight it.
     */
    static Map<String, String> loadConfig(Path root) throws IOException {
        Map<String, String> config = new LinkedHashMap<>();
        Path file = root.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            return config;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        // Copied out in our own order: Properties is a Hashtable, and the file should read
        // the way the settings are documented rather than the way they hash.
        for (String key : DEFAULTS.keySet()) {
            String value = properties.getProperty(key);
            if (value != null && !value.isBlank()) {
                config.put(key, value.strip());
            }
        }
        return config;
    }

    /**
     * Written by hand rather than with {@link Properties#store}, which stamps the file
     * with the current date and orders keys by hash — neither of which belongs in a file
     * we ask people to read and edit.
     */
    static void saveConfig(Path root, Map<String, String> config) throws IOException {
        Files.createDirectories(root.resolve(STATE_DIR));
        List<String> lines = new ArrayList<>(List.of(
                "# Local build preferences for `make dev`. Not tracked by git.",
                "# See docs/DEV.md.",
                ""));
        config.forEach((key, value) -> lines.add(key + "=" + value));
        Files.write(root.resolve(CONFIG_FILE), lines);
    }

    /** Writes the defaults on first use, so that there is always a file to edit. */
    private static void firstRun(Path root, Map<String, String> config) throws IOException {
        if (!config.isEmpty()) {
            return;
        }
        config.putAll(new LinkedHashMap<>(DEFAULTS));
        saveConfig(root, config);
        blank();
        say(Text.wroteConfig(CONFIG_FILE));
        say(Text.detail(Text.settings(config)));
        say(Text.detail(Text.EDIT_CONFIG_HINT));
    }

    /** Command line wins over the saved config, which wins over the default. */
    private static String setting(Map<String, String> config, String key, String envVar, List<String> values) {
        String value = Optional.ofNullable(System.getenv(envVar))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> config.getOrDefault(key, DEFAULTS.get(key)))
                .strip();
        if (!values.contains(value)) {
            sayErr(Text.invalidValue(key, value, values));
            System.exit(2);
        }
        return value;
    }

    // ------------------------------------------------------------------
    // output
    // ------------------------------------------------------------------

    /** Marks a line as ours, so it is not mistaken for part of the Maven build below it. */
    private static void say(String message) {
        for (String line : message.split("\\R", -1)) {
            System.out.println(dim("[KIE dev]") + " " + line);
        }
    }

    private static void sayErr(String message) {
        for (String line : message.split("\\R", -1)) {
            System.err.println(dim("[KIE dev]") + " " + line);
        }
    }

    private static void blank() {
        System.out.println();
    }

    /**
     * Names the size of the build; {@code scope} additionally lists it. Hundreds of module
     * coordinates are not something anyone reads on the way to a build.
     */
    private static void printModules(Path root, String title, List<String> modules, Path listFile,
                                     boolean listThem) throws IOException {
        Files.createDirectories(root.resolve(listFile).getParent());
        Files.write(root.resolve(listFile), modules);
        say(Text.moduleListLine(title, modules.size(), listFile));
        if (listThem) {
            modules.forEach(module -> say(Text.detail(module)));
        }
    }

    private static List<String> readLines(Path file) throws IOException {
        return Files.isRegularFile(file)
                ? Files.readAllLines(file).stream().filter(line -> !line.isBlank()).toList()
                : List.of();
    }

    private static Path requireRepoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (!Files.isRegularFile(cwd.resolve("pom.xml")) || !Files.exists(cwd.resolve(".git"))) {
            System.err.println(Text.notARepositoryRoot(cwd));
            System.exit(2);
        }
        return cwd;
    }

    /** Colour is for a person at a terminal; without one it is noise. */
    private static boolean colors() {
        return System.console() != null && System.getenv("NO_COLOR") == null;
    }

    private static String paint(String code, String s) {
        return colors() ? "\u001b[" + code + "m" + s + "\u001b[0m" : s;
    }

    /**
     * Branch names, refs and anything else that came out of git. Given its own colour so
     * that the moving parts of a message stand out from the words around them — use this
     * for every git artifact printed, rather than inlining the name.
     */
    private static String ref(String s) { return paint("36", s); }

    private static String bold(String s) { return paint("1", s); }
    private static String dim(String s) { return paint("2", s); }
    private static String red(String s) { return paint("31", s); }
    private static String yellow(String s) { return paint("33", s); }
}
