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
//SOURCES DepGraph.java

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CiComputeBuildScopes {

    private static final Path DEP_GRAPH_EXTRACTOR_POM = Paths.get("script/ci/dep-graph-extractor/pom.xml");
    private static final Path DEP_GRAPH_EXTRACTOR_SRC = Paths.get("script/ci/dep-graph-extractor/src");
    private static final Path DEP_GRAPH_EXTRACTOR_JAR = Paths.get("script/ci/dep-graph-extractor/target/dep-graph-extractor-1.0.0.jar");

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: jbang CiComputeBuildScopes.java <changed-files-in> <upstream-out> <affected-out> <changed-out>");
            System.err.println();
            System.err.println("env (each also readable as a system property of the same name):");
            System.err.println("  DEP_GRAPH_EXTRACTOR__JAR               path to dep-graph-extractor jar (default: build from script/ci/dep-graph-extractor)");
            System.err.println("  DEP_GRAPH_EXTRACTOR__OUTPUT_FILE       path where the dependency graph TSV is written (default: temp file)");
            System.err.println("  DEP_GRAPH_EXTRACTOR__EXTRA_MAVEN_ARGS  whitespace-separated args forwarded to the 'mvn validate' dep-graph-extractor run (e.g. \"-Pfoo -Dbar=baz\")");
            System.err.println("  DEP_GRAPH_EXTRACTOR__REUSE_IF_FRESH    'true' to reuse the graph at OUTPUT_FILE while no pom.xml has changed (default: false)");
            System.err.println("  MVN                                    mvn binary (default: mvn)");
            System.exit(2);
        }

        Path fileList = Paths.get(args[0]);
        Path upstreamOut = Paths.get(args[1]);
        Path affectedOut = Paths.get(args[2]);
        Path changedOut = Paths.get(args[3]);

        Path cwd = Paths.get("").toAbsolutePath();
        if (!Files.isRegularFile(cwd.resolve("pom.xml"))) {
            System.err.println("no pom.xml in " + cwd);
            System.exit(2);
        }

        Path extractorJarPath = ensureDepGraphExtractorJar(cwd);

        String extraMavenArgsEnv = Optional.ofNullable(cfg("DEP_GRAPH_EXTRACTOR__EXTRA_MAVEN_ARGS")).orElse("").strip();
        List<String> extraMavenArgs = extraMavenArgsEnv.isEmpty()
                ? List.of()
                : Arrays.asList(extraMavenArgsEnv.split("\\s+"));

        // 1. map changed files -> nearest pom.xml directory (walk up recursively)
        List<Path> changedFiles = Files.readAllLines(fileList).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Paths::get)
                .map(p -> p.isAbsolute() ? p : cwd.resolve(p))
                .toList();

        // Walk up to the nearest enclosing pom.xml, but skip pom.xml files that
        // belong to nested Maven projects living under a src/ folder (e.g.
        // src/it/* invoker fixtures, src/test/resources/* test projects).
        // Those aren't part of the reactor — when they change we want the
        // enclosing reactor module to build instead.
        Set<Path> changedModuleDirs = new HashSet<>();
        for (Path f : changedFiles) {
            Path dir = Files.isDirectory(f) ? f : f.getParent();
            while (dir != null && dir.startsWith(cwd)) {
                if (Files.isRegularFile(dir.resolve("pom.xml")) && !isUnderSrcDir(cwd, dir)) {
                    changedModuleDirs.add(dir.toAbsolutePath().normalize());
                    break;
                }
                dir = dir.getParent();
            }
        }

        // 2. run dep-graph-extractor, writing dependency graph to file.
        // Persist the graph to DEP_GRAPH_EXTRACTOR__OUTPUT_FILE when set so downstream
        // tools (CiSummary) can reuse it without re-invoking Maven.
        String graphFileEnv = cfg("DEP_GRAPH_EXTRACTOR__OUTPUT_FILE");
        Path graphFile = (graphFileEnv != null && !graphFileEnv.isBlank())
                ? Paths.get(graphFileEnv).toAbsolutePath()
                : Files.createTempFile("dep-graph-", ".tsv");

        // Reading the reactor costs a full pass over every pom.xml, which is a steep
        // price to pay on every invocation of a local dev loop. When
        // DEP_GRAPH_EXTRACTOR__REUSE_IF_FRESH is set, keep a stamp of the reactor's
        // poms next to the graph and re-extract only once one of them changes.
        // Off by default, so CI always extracts from scratch.
        Path stampFile = graphFile.resolveSibling(graphFile.getFileName() + ".stamp");
        String stamp = reactorPomStamp(cwd, extraMavenArgs, extractorJarPath);
        if (reuseIfFresh() && graphIsFresh(graphFile, stampFile, stamp)) {
            System.err.println("[CiComputeBuildScopes] Reusing cached dependency graph (no pom.xml changed): " + relative(cwd, graphFile));
        } else {
            int rc = runMavenWithDepGraphExtractor(cwd, extractorJarPath, graphFile, extraMavenArgs);
            if (!Files.isRegularFile(graphFile) || Files.size(graphFile) == 0) {
                System.err.println("dep-graph-extractor failed (mvn rc=" + rc + ")");
                System.exit(1);
            }
            Files.writeString(stampFile, stamp);
        }

        // 3. parse graph (shared with script/dev/Dev.java, so both agree on its shape)
        DepGraph graph = DepGraph.parse(graphFile);
        Map<String, Path> gaToDir = graph.gaToDir;

        // 4. resolve changed dirs -> GA
        Map<Path, String> dirToGa = gaToDir.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Set<String> changed = new HashSet<>();
        for (Path d : changedModuleDirs) {
            String ga = dirToGa.get(d);
            if (ga == null) {
                System.err.println("note: ignoring " + relative(cwd, d) + " — it has a pom.xml but is not a module of this reactor");
                continue;
            }
            changed.add(ga);
        }

        // 5. affected = changed + transitive downstream
        Set<String> affected = DepGraph.traverse(changed, graph.downstreamOf);

        // 6. upstream = transitive upstream of affected, minus affected
        Set<String> upstreamAll = DepGraph.traverse(affected, graph.upstreamOf);
        upstreamAll.removeAll(affected);

        writeLines(upstreamOut, upstreamAll);
        writeLines(affectedOut, affected);
        writeLines(changedOut, changed);

        Map<String, Set<Path>> categories = parseModuleCategories(cwd.resolve("pom.xml"), cwd);

        for (String cat : categories.keySet()) {
            Set<String> catAffected = affected.stream()
                    .filter(ga -> categorizeGa(ga, gaToDir, categories).equals(cat))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            writeLines(partitionedPath(affectedOut, cat), catAffected);
        }

        int total = gaToDir.size();
        int ignored = total - affected.size() - upstreamAll.size();
        StringBuilder sb = new StringBuilder();
        sb.append("total=").append(total)
          .append(" changed=").append(changed.size())
          .append(" affected=").append(affected.size())
          .append(" upstream=").append(upstreamAll.size())
          .append(" ignored=").append(ignored);
        for (String cat : categories.keySet()) {
            long catCount = affected.stream()
                    .filter(ga -> categorizeGa(ga, gaToDir, categories).equals(cat))
                    .count();
            sb.append(" affected-").append(cat).append("=").append(catCount);
        }
        System.out.println(sb);
    }

    /**
     * Reads a setting from a system property first, then from the environment. The
     * property form lets an in-process caller (script/dev/Dev.java pulls this
     * class in via jbang //SOURCES) configure a run without spawning a subprocess.
     */
    private static String cfg(String name) {
        return Optional.ofNullable(System.getProperty(name)).orElseGet(() -> System.getenv(name));
    }

    private static boolean reuseIfFresh() {
        return "true".equalsIgnoreCase(Optional.ofNullable(cfg("DEP_GRAPH_EXTRACTOR__REUSE_IF_FRESH")).orElse("").strip());
    }

    private static boolean graphIsFresh(Path graphFile, Path stampFile, String stamp) throws IOException {
        return Files.isRegularFile(graphFile)
                && Files.size(graphFile) > 0
                && Files.isRegularFile(stampFile)
                && stamp.equals(Files.readString(stampFile).strip());
    }

    /**
     * Fingerprints every pom.xml under {@code cwd} (path + size + mtime). Any added,
     * removed or edited pom changes the digest, and so does a change to the Maven args
     * used for the extraction, since profiles can change the shape of the reactor, or
     * to the extractor itself, since that changes what the graph file contains.
     */
    private static String reactorPomStamp(Path cwd, List<String> extraMavenArgs, Path extractorJar) throws IOException {
        List<String> entries = new ArrayList<>();
        Files.walkFileTree(cwd, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (dir.equals(cwd)) return java.nio.file.FileVisitResult.CONTINUE;
                String name = dir.getFileName().toString();
                // target/ holds build output, node_modules/ vendored deps, and dot-dirs
                // hold tool state (including .kie-dev, where this stamp lives).
                boolean prune = name.equals("target") || name.equals("node_modules") || name.startsWith(".");
                return prune ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("pom.xml")) {
                    entries.add(cwd.relativize(file) + ":" + attrs.size() + ":" + attrs.lastModifiedTime().toMillis());
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(entries);
        entries.add("args:" + String.join(" ", extraMavenArgs));
        entries.add("extractor:" + Files.size(extractorJar) + ":" + Files.getLastModifiedTime(extractorJar).toMillis());
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", entries).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Paths read better relative to the repository than as absolute ones. */
    private static String relative(Path cwd, Path path) {
        return path.startsWith(cwd) ? cwd.relativize(path).toString() : path.toString();
    }

    private static boolean isUnderSrcDir(Path cwd, Path dir) {
        Path rel = cwd.relativize(dir.toAbsolutePath().normalize());
        for (Path part : rel) {
            if ("src".equals(part.toString())) return true;
        }
        return false;
    }

    private static void writeLines(Path out, Collection<String> lines) throws IOException {
        List<String> sorted = new ArrayList<>(lines);
        Collections.sort(sorted);
        Files.write(out, sorted);
    }

    private static final List<String> EXPECTED_CATEGORIES = List.of("optaplanner", "kogito-runtimes", "kogito-apps");

    static Map<String, Set<Path>> parseModuleCategories(Path rootPom, Path cwd) throws IOException {
        Map<String, Set<Path>> categories = new LinkedHashMap<>();
        categories.put("drools", new LinkedHashSet<>());
        for (String cat : EXPECTED_CATEGORIES) {
            categories.put(cat, new LinkedHashSet<>());
        }

        String currentCategory = "drools";
        boolean inModules = false;
        Set<String> seenBegins = new HashSet<>();
        Set<String> seenEnds = new HashSet<>();

        Pattern beginPattern = Pattern.compile("<!--\\s*BEGIN\\s+(\\S+)\\s+modules\\s+\\(auto\\)\\s*-->");
        Pattern endPattern = Pattern.compile("<!--\\s*END\\s+(\\S+)\\s+modules\\s+\\(auto\\)\\s*-->");
        Pattern modulePattern = Pattern.compile("<module>(.+)</module>");

        for (String line : Files.readAllLines(rootPom)) {
            String trimmed = line.trim();

            if (trimmed.equals("<modules>")) {
                inModules = true;
                continue;
            }
            if (trimmed.equals("</modules>")) {
                break;
            }
            if (!inModules) continue;

            Matcher beginMatcher = beginPattern.matcher(trimmed);
            if (beginMatcher.matches()) {
                currentCategory = beginMatcher.group(1);
                if (!categories.containsKey(currentCategory)) {
                    System.err.println("ERROR: unknown module category '" + currentCategory
                            + "' in pom.xml marker. Expected one of: " + EXPECTED_CATEGORIES);
                    System.exit(1);
                }
                seenBegins.add(currentCategory);
                continue;
            }

            Matcher endMatcher = endPattern.matcher(trimmed);
            if (endMatcher.matches()) {
                seenEnds.add(endMatcher.group(1));
                currentCategory = "drools";
                continue;
            }

            Matcher moduleMatcher = modulePattern.matcher(trimmed);
            if (moduleMatcher.matches()) {
                String modulePath = moduleMatcher.group(1);
                categories.get(currentCategory).add(cwd.resolve(modulePath).toAbsolutePath().normalize());
            }
        }

        for (String cat : EXPECTED_CATEGORIES) {
            if (!seenBegins.contains(cat)) {
                System.err.println("ERROR: missing '<!-- BEGIN " + cat
                        + " modules (auto) -->' marker in pom.xml");
                System.exit(1);
            }
            if (!seenEnds.contains(cat)) {
                System.err.println("ERROR: missing '<!-- END " + cat
                        + " modules (auto) -->' marker in pom.xml");
                System.exit(1);
            }
        }

        return categories;
    }

    private static String categorizeGa(String ga, Map<String, Path> gaToDir,
                                        Map<String, Set<Path>> categories) {
        Path dir = gaToDir.get(ga);
        if (dir == null) return "drools";
        dir = dir.toAbsolutePath().normalize();
        for (var entry : categories.entrySet()) {
            for (Path catPath : entry.getValue()) {
                if (dir.equals(catPath) || dir.startsWith(catPath + "/")) {
                    return entry.getKey();
                }
            }
        }
        return "drools";
    }

    private static Path partitionedPath(Path basePath, String category) {
        String name = basePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String newName = (dot >= 0)
                ? name.substring(0, dot) + "-" + category + name.substring(dot)
                : name + "-" + category;
        return basePath.resolveSibling(newName);
    }

    private static int runMavenWithDepGraphExtractor(Path cwd, Path extractorJar, Path graphOut,
                                                      List<String> extraArgs) throws IOException, InterruptedException {
        var cmd = new ArrayList<String>();
        cmd.add(mvnBinary());
        cmd.add("-Dmaven.ext.class.path=" + extractorJar.toAbsolutePath());
        cmd.add("-q");
        cmd.add("-DdepGraphExtractor.out=" + graphOut.toAbsolutePath());
        cmd.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=error");
        cmd.addAll(extraArgs);
        cmd.add("validate");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(cwd.toFile())
                .redirectErrorStream(true);
        pb.environment().put("MAVEN_OPTS",
                Optional.ofNullable(System.getenv("MAVEN_OPTS")).orElse(""));

        System.err.println("[CiComputeBuildScopes] Running: " + String.join(" ", cmd));
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.err.println("  " + line);
            }
        }
        return p.waitFor();
    }

    private static Path ensureDepGraphExtractorJar(Path cwd) throws IOException, InterruptedException {
        String override = cfg("DEP_GRAPH_EXTRACTOR__JAR");
        if (override != null && !override.isBlank()) {
            Path p = Paths.get(override).toAbsolutePath();
            if (!Files.isRegularFile(p)) {
                System.err.println("DEP_GRAPH_EXTRACTOR__JAR points to non-existent file: " + p);
                System.exit(2);
            }
            return p;
        }

        Path pom = cwd.resolve(DEP_GRAPH_EXTRACTOR_POM);
        Path jar = cwd.resolve(DEP_GRAPH_EXTRACTOR_JAR);
        Path src = cwd.resolve(DEP_GRAPH_EXTRACTOR_SRC);
        if (!Files.isRegularFile(pom)) {
            System.err.println("dep-graph-extractor pom not found: " + pom);
            System.exit(2);
        }

        if (!depGraphExtractorJarIsFresh(jar, pom, src)) {
            System.err.println("building dep-graph-extractor…");
            int rc = buildDepGraphExtractor(cwd, pom);
            if (rc != 0 || !Files.isRegularFile(jar)) {
                System.err.println("failed to build dep-graph-extractor (mvn rc=" + rc + ")");
                System.exit(1);
            }
        }
        return jar.toAbsolutePath();
    }

    private static boolean depGraphExtractorJarIsFresh(Path jar, Path pom, Path src) throws IOException {
        if (!Files.isRegularFile(jar)) return false;
        long jarMtime = Files.getLastModifiedTime(jar).toMillis();
        if (Files.getLastModifiedTime(pom).toMillis() > jarMtime) return false;
        if (!Files.isDirectory(src)) return true;
        try (Stream<Path> s = Files.walk(src)) {
            return s.filter(Files::isRegularFile).allMatch(p -> {
                try { return Files.getLastModifiedTime(p).toMillis() <= jarMtime; }
                catch (IOException e) { return false; }
            });
        }
    }

    private static int buildDepGraphExtractor(Path cwd, Path pom) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                mvnBinary(),
                "--batch-mode",
                "--no-transfer-progress",
                "-f", pom.toAbsolutePath().toString(),
                "package"
        );
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(cwd.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.err.println("  " + line);
            }
        }
        return p.waitFor();
    }

    private static String mvnBinary() {
        // Windows ships `mvn.cmd`, not `mvn.exe` — ProcessBuilder doesn't go through
        // cmd.exe, so the bare name "mvn" fails to resolve.
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Optional.ofNullable(cfg("MVN"))
                .orElse(windows ? "mvn.cmd" : "mvn");
    }
}