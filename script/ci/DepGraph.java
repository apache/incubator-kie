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

//JAVA 21

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reactor dependency graph, as written by the {@code dep-graph-extractor} Maven
 * extension and read by everything that needs to know how the modules relate.
 *
 * <p>Shared by {@code script/ci/CiComputeBuildScopes.java} and
 * {@code script/dev/Dev.java} — pull it in with a jbang {@code //SOURCES} line rather
 * than copying it, so that CI and local builds cannot disagree about the graph.
 *
 * <p>The file is a TSV of one-letter record types:
 * <pre>
 *   P  groupId:artifactId  /abs/basedir
 *   D  groupId:artifactId  upstream-groupId:artifactId  scope
 *   V  groupId:artifactId  version                      packaging
 *   L  /abs/path/to/local/maven/repository
 *   B  groupId:artifactId                               (an in-reactor BOM)
 * </pre>
 * Unknown record types are ignored, so the extractor can add more without breaking
 * anything that reads it.
 */
public final class DepGraph {

    /** Module directory of each module. */
    public final Map<String, Path> gaToDir = new LinkedHashMap<>();
    /** Version of each module, when the extractor recorded it. */
    public final Map<String, String> gaToVersion = new LinkedHashMap<>();
    /** Maven packaging of each module, when the extractor recorded it. */
    public final Map<String, String> gaToPackaging = new LinkedHashMap<>();
    /** What each module depends on, directly. */
    public final Map<String, Set<String>> upstreamOf = new LinkedHashMap<>();
    /** What depends on each module, directly. */
    public final Map<String, Set<String>> downstreamOf = new LinkedHashMap<>();
    /** Every module directory, for telling a module's own files from a nested module's. */
    public final Set<Path> moduleDirs = new HashSet<>();
    /** The modules that are BOMs. */
    public final Set<String> boms = new LinkedHashSet<>();
    /**
     * Why each edge exists, keyed by {@code "dependent|dependency"} — a Maven scope, or
     * {@code parent} / {@code plugin} / {@code import}. Empty when the graph was written
     * by an extractor that predates the 4th {@code D} field.
     *
     * <p>Build scoping deliberately ignores this: a test-scope edge still means the
     * downstream module must be rebuilt. It is here for tooling that needs to tell what
     * ships from what is only tested, such as separating test-only modules.
     */
    public final Map<String, String> edgeScopes = new LinkedHashMap<>();
    /** The local Maven repository this graph was read with, or null if not recorded. */
    public Path localRepo;

    public static DepGraph parse(Path file) throws IOException {
        DepGraph graph = new DepGraph();
        if (!Files.isRegularFile(file)) {
            return graph;
        }
        for (String line : Files.readAllLines(file)) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 2) continue;
            switch (parts[0]) {
                case "P" -> {
                    if (parts.length < 3) break;
                    Path dir = Paths.get(parts[2]).toAbsolutePath().normalize();
                    graph.gaToDir.put(parts[1], dir);
                    graph.moduleDirs.add(dir);
                    graph.upstreamOf.computeIfAbsent(parts[1], k -> new LinkedHashSet<>());
                    graph.downstreamOf.computeIfAbsent(parts[1], k -> new LinkedHashSet<>());
                }
                case "D" -> {
                    if (parts.length < 3) break;
                    graph.addEdge(parts[1], parts[2]);
                    if (parts.length >= 4 && !parts[3].isEmpty()) {
                        graph.edgeScopes.put(parts[1] + "|" + parts[2], parts[3]);
                    }
                }
                case "V" -> {
                    if (parts.length < 4) break;
                    graph.gaToVersion.put(parts[1], parts[2]);
                    graph.gaToPackaging.put(parts[1], parts[3]);
                }
                case "L" -> graph.localRepo = Paths.get(parts[1]).toAbsolutePath().normalize();
                case "B" -> graph.boms.add(parts[1]);
                default -> { /* ignore unknown record types */ }
            }
        }
        graph.pairQuarkusExtensions();
        return graph;
    }

    /** Records that {@code dependent} depends on {@code dependency}. */
    private void addEdge(String dependent, String dependency) {
        upstreamOf.computeIfAbsent(dependent, k -> new LinkedHashSet<>()).add(dependency);
        downstreamOf.computeIfAbsent(dependency, k -> new LinkedHashSet<>()).add(dependent);
    }


    /** Scope of {@code dependent -> dependency}, or null if the graph recorded none. */
    public String scopeOf(String dependent, String dependency) {
        return edgeScopes.get(dependent + "|" + dependency);
    }

    /** True when the only reason {@code dependent} needs {@code dependency} is its tests. */
    public boolean isTestOnlyEdge(String dependent, String dependency) {
        return "test".equals(scopeOf(dependent, dependency));
    }

    /**
     * The extension-descriptor goal on a Quarkus runtime module resolves its
     * {@code -deployment} counterpart at build time, which is invisible to the reactor
     * graph. A synthetic edge keeps the pair in the same build set.
     *
     * <p>This matches any {@code <ga>} / {@code <ga>-deployment} pair, not only Quarkus
     * extensions — which is safe: the worst case is that an unrelated pair is built
     * together, which is conservative rather than wrong.
     */
    private void pairQuarkusExtensions() {
        for (String ga : List.copyOf(gaToDir.keySet())) {
            String deploymentGa = ga + "-deployment";
            if (gaToDir.containsKey(deploymentGa)) {
                addEdge(ga, deploymentGa);
            }
        }
    }

    /** Where a module's primary artifact would live in the local repository, or null if unknown. */
    public Path installedArtifact(String ga) {
        String version = gaToVersion.get(ga);
        if (localRepo == null || version == null) {
            return null;
        }
        int separator = ga.indexOf(':');
        String groupId = ga.substring(0, separator);
        String artifactId = ga.substring(separator + 1);
        // Aggregators and BOMs install only a pom; everything else installs its
        // packaging's main artifact, whose extension matches the packaging for every
        // type used in this repository (maven-plugin excepted — also a jar).
        String extension = "pom".equals(gaToPackaging.getOrDefault(ga, "jar")) ? "pom" : "jar";
        Path dir = localRepo;
        for (String segment : groupId.split("\\.")) {
            dir = dir.resolve(segment);
        }
        return dir.resolve(artifactId).resolve(version).resolve(artifactId + "-" + version + "." + extension);
    }

    /**
     * Everything {@code toBuild} depends on, transitively, that it does not build itself.
     *
     * <p>This is not the same as the `upstream` set CI computes: that one is relative to
     * the affected modules, and a narrower build can need modules that sit inside
     * `affected` — everything is downstream of the root pom, including modules that the
     * root pom's own children depend on.
     */
    public List<String> dependenciesOf(List<String> toBuild) {
        Set<String> build = new LinkedHashSet<>(toBuild);
        return traverse(build, upstreamOf).stream()
                .filter(ga -> !build.contains(ga))
                .sorted()
                .toList();
    }

    /**
     * Grows {@code seeds} to include everything downstream of them, bounded by
     * {@code candidates}: rebuilding a module invalidates its consumers, but only those
     * consumers that are themselves dependencies — anything else is in the main pass.
     */
    public Set<String> rebuildClosure(Set<String> candidates, Set<String> seeds) {
        Set<String> needed = new LinkedHashSet<>(seeds);
        Deque<String> pending = new ArrayDeque<>(needed);
        while (!pending.isEmpty()) {
            for (String down : downstreamOf.getOrDefault(pending.pop(), Set.of())) {
                if (candidates.contains(down) && needed.add(down)) {
                    pending.push(down);
                }
            }
        }
        return needed;
    }

    /** Every node reachable from {@code seeds} along {@code edges}, seeds included. */
    public static Set<String> traverse(Set<String> seeds, Map<String, Set<String>> edges) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(seeds);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (visited.add(current)) {
                stack.addAll(edges.getOrDefault(current, Set.of()));
            }
        }
        return visited;
    }
}
