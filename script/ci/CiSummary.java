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
//COMPILE_OPTIONS -encoding UTF-8
//DEPS com.samskivert:jmustache:1.16

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Parses Maven Surefire / Failsafe XML reports and writes a GitHub Actions
 * job summary to $GITHUB_STEP_SUMMARY.
 *
 * Environment variables:
 *   CI_REPO_ROOT                Path to the repository root  (default: current directory)
 *   GITHUB_STEP_SUMMARY         Path to the GitHub step summary file (set automatically by GitHub Actions)
 *   MAVEN_PL_UPSTREAM           Comma-separated upstream groupId:artifactId list (from CiComputeBuildScopes)
 *   MAVEN_PL_AFFECTED           Comma-separated affected groupId:artifactId list (changed + transitive downstream)
 *   MAVEN_PL_CHANGED            Comma-separated directly-changed groupId:artifactId list (subset of affected)
 *   MAVEN_PL_IMAGE_PRODUCERS    Comma-separated image-producing groupId:artifactId list (parallel build only)
 *   CI_PARALLEL_BUILD           true when reproducing the parallel build's three-phase strategy
 *   DEP_GRAPH_EXTRACTOR__OUTPUT_FILE TSV dump produced by the dep-graph-extractor Maven extension via CiComputeBuildScopes.
 *                                    Lines: `P<TAB>ga<TAB>abs-basedir` (one per reactor project)
 *                                           `D<TAB>dependent-ga<TAB>dependency-ga` (one per direct edge)
 *   CI_SUREFIRE_FILE_PREFIXES        Comma-separated list of filename prefixes accepted as test report XMLs.
 *                                    Default: `TEST-,IT-`
 */
class CiSummary {

    record Suite(String module, int tests, int failures, int errors, int skipped, List<Fail> fails) {}
    record Fail(String classname, String test, String type, String message) {}

    /** Reactor GA → relative module path. Populated from the graph file. */
    static final Map<String, String> gaToPath = new LinkedHashMap<>();
    /** All reactor module paths, in reactor-iteration order. */
    static final Set<String> allPaths = new LinkedHashSet<>();
    /** module path → paths of its direct upstream (dependency) modules. */
    static final Map<String, Set<String>> upstreamsOf = new LinkedHashMap<>();
    /** Paths of reactor modules that are BOMs (imported via <dependencyManagement><scope>import</scope>). */
    static final Set<String> bomPaths = new LinkedHashSet<>();

    /** Max nodes in the Mermaid graph; beyond this, show collapsible lists instead. */
    static final int MERMAID_NODE_LIMIT = 1000;

    /** When false, the Mermaid graph is wrapped in a collapsed <details> block. */
    static boolean mermaidExpanded = false;
    static String matrixOs   = "";
    static String matrixJava = "";

    // ── The entire summary as a single Mustache template ─────────────────────
    static final String TEMPLATE = """
            {{#hasScope}}
            ## Build Scope

            {{{breakdownLine}}}

            {{{mermaidBlock}}}
            {{#lists}}
            <details>
            <summary>{{title}} ({{count}})</summary>

            {{#items}}
            - `{{name}}`{{#bom}} — _BOM_{{/bom}}
            {{/items}}

            </details>

            {{/lists}}
            {{/hasScope}}
            ## Test Results

            {{#noTests}}
            > ℹ️ No test reports found.
            {{/noTests}}
            {{^noTests}}
            {{#allPassed}}
            > 🎉 **All tests passed.**
            {{/allPassed}}
            {{^allPassed}}
            > ⚠️ **Some tests failed.**
            {{/allPassed}}

            | ✅ Passed | ❌ Failed | 💥 Errors | ⏭️ Skipped | TOTAL |
            |---:|---:|---:|---:|---:|
            | **{{passed}}** | **{{failed}}** | **{{errored}}** | **{{testsSkipped}}** | **{{total}}** |
            {{#hasFailedModules}}

            ### Failed Tests

            {{#failedModules}}
            <details>
            <summary><b>{{module}}</b> — {{count}} {{failureWord}}</summary>

            {{#fails}}
            **`{{classname}}#{{test}}`**{{#hasType}} — `{{type}}`{{/hasType}}
            {{#hasMessage}}
            > {{message}}
            {{/hasMessage}}

            {{/fails}}
            </details>

            {{/failedModules}}
            {{/hasFailedModules}}
            {{/noTests}}

            ## Reproduce This Build Locally

            {{#hasMatrix}}
            > Built on **{{matrixOs}}** with **Java {{matrixJava}}**

            {{/hasMatrix}}
            {{#isPr}}
            {{#hasUpstreamGa}}
            **{{upstreamHeading}}**

            ```bash
            {{upstreamCmd}}
            ```

            {{/hasUpstreamGa}}
            {{#hasImageProducersGa}}
            **{{imageProducersHeading}}**

            ```bash
            {{imageProducersCmd}}
            ```

            {{/hasImageProducersGa}}
            **{{affectedHeading}}**

            ```bash
            {{affectedCmd}}
            ```

            {{/isPr}}
            {{^isPr}}
            **Full build:**

            ```bash
            {{fullCmd}}
            ```

            {{/isPr}}
            """;

    public static void main(String[] args) throws Exception {
        var root        = Path.of(env("CI_REPO_ROOT", ".")).toAbsolutePath().normalize();
        var summaryPath = env("GITHUB_STEP_SUMMARY", null);
        var upstreamGa  = env("MAVEN_PL_UPSTREAM",   "");
        var affectedGa  = env("MAVEN_PL_AFFECTED",   "");
        var changedGa   = env("MAVEN_PL_CHANGED",    "");
        var imageProducersGa = env("MAVEN_PL_IMAGE_PRODUCERS", "");
        var parallelBuild = "true".equalsIgnoreCase(env("CI_PARALLEL_BUILD", "false"));
        var graphPath   = env("DEP_GRAPH_EXTRACTOR__OUTPUT_FILE", null);
        mermaidExpanded = "true".equalsIgnoreCase(env("MERMAID_EXPANDED", "false"));
        matrixOs        = env("MATRIX_OS",   "");
        matrixJava      = env("MATRIX_JAVA", "");
        var surefixPrefixesCsv = env("CI_SUREFIRE_FILE_PREFIXES", "TEST-,IT-");
        var surefirePrefixes = List.of(surefixPrefixesCsv.split(","));

        if (graphPath != null) {
            try {
                loadGraphFile(Path.of(graphPath), root);
            } catch (Exception e) {
                log("Warning: failed to load graph file %s: %s — build-scope section unavailable",
                    graphPath, e.getMessage());
            }
        }

        var upstream = resolveGas(upstreamGa);
        var affected = resolveGas(affectedGa);
        var changed  = resolveGas(changedGa);

        // A partition's phase-1 list intentionally contains its affected modules so
        // cross-partition upstream dependencies can resolve them. Keep the raw GA list
        // for the reproduction command, but show each module in only one scope category.
        upstream.removeAll(affected);

        var xmlFiles = new ArrayList<Path>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> {
                var name = p.getFileName().toString();
                var dir  = p.getParent() == null ? "" : p.getParent().getFileName().toString();
                return name.endsWith(".xml")
                    && surefirePrefixes.stream().anyMatch(name::startsWith)
                    && (dir.equals("surefire-reports") || dir.equals("failsafe-reports"));
            }).forEach(xmlFiles::add);
        }

        var suites = new ArrayList<Suite>();
        for (var f : xmlFiles) {
            var s = parseSuite(root, f);
            if (s != null) suites.add(s);
        }

        int total   = suites.stream().mapToInt(Suite::tests).sum();
        int failed  = suites.stream().mapToInt(Suite::failures).sum();
        int errored = suites.stream().mapToInt(Suite::errors).sum();
        int skipped = suites.stream().mapToInt(Suite::skipped).sum();
        int passed  = total - failed - errored - skipped;

        var ctx = buildContext(upstream, affected, changed, upstreamGa, affectedGa,
                               imageProducersGa, parallelBuild,
                               suites, total, failed, errored, skipped, passed);

        Template tmpl = Mustache.compiler().escapeHTML(false).compile(TEMPLATE);
        var summary = tmpl.execute(ctx);

        if (summaryPath != null) {
            Files.writeString(Path.of(summaryPath), summary,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            System.out.print(summary);
        }

        log("Summarized %d tests across %d suite(s): %d passed, %d failed, %d errors, %d skipped",
            total, suites.size(), passed, failed, errored, skipped);
    }

    // ── Context builder ──────────────────────────────────────────────────────

    static Map<String, Object> buildContext(Set<String> upstream, Set<String> affected, Set<String> changed,
                                             String upstreamGa, String affectedGa, String imageProducersGa,
                                             boolean parallelBuild,
                                             List<Suite> suites,
                                             int total, int failed, int errored, int skipped, int passed) {
        var ctx = new HashMap<String, Object>();

        // ── Scope section ──
        boolean hasScope = !affected.isEmpty();
        ctx.put("hasScope", hasScope);
        if (hasScope) {
            var skippedPaths = new TreeSet<String>();
            for (var path : allPaths) {
                if (!upstream.contains(path) && !affected.contains(path)) skippedPaths.add(path);
            }
            var all = new LinkedHashSet<String>();
            all.addAll(upstream);
            all.addAll(affected);

            ctx.put("breakdownLine", breakdownLine(upstream, affected, changed, skippedPaths));
            ctx.put("mermaidBlock", all.size() <= MERMAID_NODE_LIMIT
                ? renderMermaid(upstream, affected, changed, all)
                : "");
            ctx.put("lists", listsData(upstream, affected, changed, skippedPaths));
        }

        // ── Test results ──
        ctx.put("noTests",      total == 0);
        ctx.put("allPassed",    failed + errored == 0);
        ctx.put("passed",       passed);
        ctx.put("failed",       failed);
        ctx.put("errored",      errored);
        ctx.put("testsSkipped", skipped);
        ctx.put("total",        total);

        var byModule = suites.stream()
            .filter(s -> !s.fails().isEmpty())
            .collect(Collectors.groupingBy(Suite::module, TreeMap::new, Collectors.toList()));
        ctx.put("hasFailedModules", !byModule.isEmpty());
        ctx.put("failedModules", failedModulesData(byModule));

        // ── Reproduce ──
        boolean hasMatrix = !matrixOs.isBlank() || !matrixJava.isBlank();
        ctx.put("hasMatrix", hasMatrix);
        ctx.put("matrixOs",  matrixOs.isBlank() ? "unknown OS" : matrixOs);
        ctx.put("matrixJava", matrixJava.isBlank() ? "?" : matrixJava);

        boolean isPr = !affectedGa.isBlank();
        boolean hasUpstreamGa = !upstreamGa.isBlank();
        boolean hasImageProducersGa = !imageProducersGa.isBlank();
        ctx.put("isPr", isPr);
        ctx.put("hasUpstreamGa", hasUpstreamGa);
        ctx.put("hasImageProducersGa", hasImageProducersGa);

        int step = 1;
        if (hasUpstreamGa) {
            ctx.put("upstreamHeading", "Step " + step++ + " — Build upstream dependencies (tests skipped):");
        }
        if (hasImageProducersGa) {
            ctx.put("imageProducersHeading", "Step " + step++ + " — Build required image-producing modules (tests skipped):");
        }
        ctx.put("affectedHeading", step == 1
                ? "Build changed and affected modules (with tests):"
                : "Step " + step + " — Build changed and affected modules (with tests):");

        String parallelUpstreamFlags = parallelBuild
                ? " -Dquarkus.build.skip=true -Ddisable.quarkus.plugin=true -Dskip.quarkus.image.assembly=true"
                : "";
        ctx.put("upstreamCmd",
            "mvn -T 1C --batch-mode --no-transfer-progress -fae"
            + " -DskipTests -DskipITs" + parallelUpstreamFlags
            + " -Denforcer.skip=true -Dcheckstyle.skip=true"
            + " -Dformatter.skip=true -Darchunit.skip=true"
            + " -Dsurefire.redirectTestOutputToFile=true"
            + " -pl \"" + upstreamGa + "\" install");
        ctx.put("imageProducersCmd",
            "mvn -T 1C --batch-mode --no-transfer-progress -fae"
            + " -DskipTests -DskipITs -Denforcer.skip=true -Dcheckstyle.skip=true"
            + " -Dformatter.skip=true -Darchunit.skip=true"
            + " -Dsurefire.redirectTestOutputToFile=true"
            + " -pl \"" + imageProducersGa + "\" install");
        ctx.put("affectedCmd",
            "mvn --batch-mode --no-transfer-progress -fae"
            + " -Dsurefire.redirectTestOutputToFile=true"
            + " -Dfull -Dreproducible"
            + " -pl \"" + affectedGa + "\" install");
        ctx.put("fullCmd",
            "mvn --batch-mode --no-transfer-progress -fae"
            + " -Dsurefire.redirectTestOutputToFile=true"
            + " -Dfull -Dreproducible install");

        return ctx;
    }

    static String breakdownLine(Set<String> upstream, Set<String> affected,
                                 Set<String> changed, Set<String> skipped) {
        var sb = new StringBuilder();
        if (!changed.isEmpty()) {
            sb.append("**").append(changed.size()).append(" changed** (")
              .append(bomBreakdown(changed)).append("directly modified)")
              .append(" · ");
        }
        sb.append("**").append(affected.size()).append(" affected** (")
          .append(bomBreakdown(affected)).append("changed + downstream, built + tested)");
        if (!upstream.isEmpty()) {
            sb.append(" · **").append(upstream.size()).append(" upstream** (")
              .append(bomBreakdown(upstream)).append("built, tests skipped)");
        }
        if (!skipped.isEmpty()) {
            sb.append(" · **").append(skipped.size()).append(" skipped** (")
              .append(bomBreakdown(skipped)).append("not built)");
        }
        return sb.toString();
    }

    /**
     * Returns "{X} modules + {N} BOMs; " when {@code paths} contains any BOMs, else "".
     * BOMs are pom-only manifests, so the distinct count makes CI logs easier to reconcile.
     */
    static String bomBreakdown(Set<String> paths) {
        long bomCount = paths.stream().filter(bomPaths::contains).count();
        if (bomCount == 0) return "";
        long modCount = paths.size() - bomCount;
        return modCount + (modCount == 1 ? " module + " : " modules + ")
             + bomCount + (bomCount == 1 ? " BOM; " : " BOMs; ");
    }

    static List<Map<String, Object>> listsData(Set<String> upstream, Set<String> affected,
                                                 Set<String> changed, Set<String> skipped) {
        var lists = new ArrayList<Map<String, Object>>();
        addListIfNotEmpty(lists, "Changed modules",  changed);
        addListIfNotEmpty(lists, "Upstream modules", upstream);
        addListIfNotEmpty(lists, "Affected modules", affected);
        addListIfNotEmpty(lists, "Skipped modules",  skipped);
        return lists;
    }

    static void addListIfNotEmpty(List<Map<String, Object>> lists, String title, Collection<String> items) {
        if (items.isEmpty()) return;
        var itemsData = new ArrayList<Map<String, Object>>();
        for (var m : items) {
            itemsData.add(Map.of(
                "name", m.isEmpty() ? "." : m,
                "bom",  bomPaths.contains(m)
            ));
        }
        lists.add(Map.of(
            "title", title,
            "count", items.size(),
            "items", itemsData
        ));
    }

    static List<Map<String, Object>> failedModulesData(Map<String, List<Suite>> byModule) {
        var modules = new ArrayList<Map<String, Object>>();
        for (var entry : byModule.entrySet()) {
            var allFails = entry.getValue().stream().flatMap(s -> s.fails().stream()).toList();
            var failsData = new ArrayList<Map<String, Object>>();
            for (var f : allFails) {
                var msg = f.message().trim();
                boolean hasMessage = !msg.isEmpty();
                if (hasMessage) {
                    if (msg.length() > 300) msg = msg.substring(0, 300) + "…";
                    msg = msg.replace("\n", " ");
                }
                failsData.add(Map.of(
                    "classname",  f.classname(),
                    "test",       f.test(),
                    "type",       f.type(),
                    "hasType",    !f.type().isBlank(),
                    "hasMessage", hasMessage,
                    "message",    msg
                ));
            }
            modules.add(Map.of(
                "module",      entry.getKey().isEmpty() ? "(root)" : entry.getKey(),
                "count",       allFails.size(),
                "failureWord", allFails.size() == 1 ? "failure" : "failures",
                "fails",       failsData
            ));
        }
        return modules;
    }

    // ── Mermaid rendering ────────────────────────────────────────────────────

    static String renderMermaid(Set<String> upstream, Set<String> affected,
                                 Set<String> changed, Set<String> all) {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (var p : all) adj.put(p, new LinkedHashSet<>());
        for (var p : all) {
            for (var up : upstreamsOf.getOrDefault(p, Set.of())) {
                if (!up.equals(p) && all.contains(up)) {
                    adj.get(up).add(p);
                }
            }
        }

        // Transitive reduction: without it, parent-pom / BOM edges bury the actual structure.
        var reduced = transitiveReduction(all, adj);
        var aliases = buildAliasMap(all);

        var sb = new StringBuilder();
        if (!mermaidExpanded) sb.append("<details>\n<summary>Dependency graph</summary>\n\n");
        sb.append("```mermaid\n");
        sb.append("flowchart TD\n");

        // BOMs: subroutine [[label]] — distinct from module shapes so graph reads in monochrome.
        // Upstream: stadium ([label]), Changed: hexagon {{label}}, Affected-not-changed: rectangle.
        for (var path : upstream) appendMermaidNode(sb, path, "([", "])", aliases);
        for (var path : changed)  appendMermaidNode(sb, path, "{{", "}}", aliases);
        for (var path : affected) {
            if (!changed.contains(path)) appendMermaidNode(sb, path, "[", "]", aliases);
        }

        for (var u : all) {
            for (var v : reduced.getOrDefault(u, Set.of())) {
                sb.append("    ").append(aliases.get(u)).append(" --> ").append(aliases.get(v)).append("\n");
            }
        }

        sb.append("    classDef upstream fill:#4a90d9,color:#fff,stroke:#2c6fad\n");
        sb.append("    classDef affected fill:#f5a623,color:#fff,stroke:#c0820a\n");
        sb.append("    classDef changed  fill:#d0021b,color:#fff,stroke:#8a0112,stroke-width:2px\n");
        sb.append("    classDef bom      fill:#9b59b6,color:#fff,stroke:#6c3483\n");

        // BOM nodes get their own class; exclude from per-category classes so purple wins.
        var upstreamIds     = joinIds(upstream, aliases, p -> !bomPaths.contains(p));
        var affectedOnlyIds = joinIds(affected, aliases, p -> !changed.contains(p) && !bomPaths.contains(p));
        var changedIds      = joinIds(changed,  aliases, p -> !bomPaths.contains(p));
        var bomIds          = joinIds(all,      aliases, bomPaths::contains);

        if (!upstreamIds.isEmpty())     sb.append("    class ").append(upstreamIds).append(" upstream\n");
        if (!affectedOnlyIds.isEmpty()) sb.append("    class ").append(affectedOnlyIds).append(" affected\n");
        if (!changedIds.isEmpty())      sb.append("    class ").append(changedIds).append(" changed\n");
        if (!bomIds.isEmpty())          sb.append("    class ").append(bomIds).append(" bom\n");

        sb.append("```\n");
        if (!mermaidExpanded) sb.append("\n</details>\n");
        sb.append("\n");
        return sb.toString();
    }

    static String joinIds(Set<String> paths, Map<String, String> aliases,
                           java.util.function.Predicate<String> filter) {
        return paths.stream().filter(filter).map(aliases::get).collect(Collectors.joining(","));
    }

    /** Emits a Mermaid node; BOMs use subroutine shape, modules use the given shape. */
    static void appendMermaidNode(StringBuilder sb, String path,
                                   String open, String close, Map<String, String> aliases) {
        var id    = aliases.get(path);
        var label = shortName(path);
        sb.append("    ").append(id);
        if (bomPaths.contains(path)) {
            sb.append("[[\"").append(label).append("\"]]");
        } else {
            sb.append(open).append("\"").append(label).append("\"").append(close);
        }
        sb.append("\n");
    }

    /**
     * Transitive reduction of the DAG. Safe on non-DAGs but also strips edges on a cycle;
     * the reactor graph is always a DAG, so that's fine.
     */
    static Map<String, Set<String>> transitiveReduction(Set<String> nodes,
                                                         Map<String, Set<String>> adj) {
        // Transitive closure via fixpoint. n ≤ MERMAID_NODE_LIMIT, so quadratic is fine.
        Map<String, Set<String>> reach = new HashMap<>();
        for (var n : nodes) reach.put(n, new HashSet<>());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var u : nodes) {
                var r = reach.get(u);
                for (var w : adj.getOrDefault(u, Set.of())) {
                    if (r.add(w)) changed = true;
                    for (var x : reach.getOrDefault(w, Set.of())) {
                        if (r.add(x)) changed = true;
                    }
                }
            }
        }
        // Keep u→v only when no other neighbour w of u already reaches v.
        Map<String, Set<String>> reduced = new LinkedHashMap<>();
        for (var u : nodes) {
            var neighbours = adj.getOrDefault(u, Set.of());
            var keep = new LinkedHashSet<String>();
            for (var v : neighbours) {
                boolean redundant = false;
                for (var w : neighbours) {
                    if (w.equals(v)) continue;
                    if (reach.getOrDefault(w, Set.of()).contains(v)) {
                        redundant = true;
                        break;
                    }
                }
                if (!redundant) keep.add(v);
            }
            reduced.put(u, keep);
        }
        return reduced;
    }

    // ── Graph file loader ────────────────────────────────────────────────────

    /**
     * Parses the dependency graph TSV produced by the dep-graph-extractor Maven extension and
     * populates {@link #gaToPath}, {@link #allPaths} and {@link #upstreamsOf}.
     */
    static void loadGraphFile(Path file, Path root) throws IOException {
        var pendingEdges = new ArrayList<String[]>();
        var pendingBoms  = new ArrayList<String>();
        for (var line : Files.readAllLines(file)) {
            var parts = line.split("\t", -1);
            if (parts.length < 2) continue;
            switch (parts[0]) {
                case "P" -> {
                    if (parts.length < 3) break;
                    var ga  = parts[1];
                    var abs = Path.of(parts[2]).toAbsolutePath().normalize();
                    var rel = root.relativize(abs).toString().replace('\\', '/');
                    gaToPath.put(ga, rel);
                    allPaths.add(rel);
                    upstreamsOf.computeIfAbsent(rel, k -> new LinkedHashSet<>());
                }
                case "D" -> {
                    if (parts.length < 3) break;
                    pendingEdges.add(new String[]{parts[1], parts[2]});
                }
                case "B" -> pendingBoms.add(parts[1]);
                default  -> { /* ignore unknown record types */ }
            }
        }
        // Resolve edges after all P lines have been seen, so forward references work.
        for (var e : pendingEdges) {
            var dependent  = gaToPath.get(e[0]);
            var dependency = gaToPath.get(e[1]);
            if (dependent != null && dependency != null && !dependent.equals(dependency)) {
                upstreamsOf.computeIfAbsent(dependent, k -> new LinkedHashSet<>()).add(dependency);
            }
        }
        for (var bomGa : pendingBoms) {
            var path = gaToPath.get(bomGa);
            if (path != null) bomPaths.add(path);
        }
    }

    // ── Surefire XML parsing ─────────────────────────────────────────────────

    static Suite parseSuite(Path root, Path xml) {
        try {
            var ts = parseXml(xml).getDocumentElement();

            int tests    = intAttr(ts, "tests");
            int failures = intAttr(ts, "failures");
            int errors   = intAttr(ts, "errors");
            int skipped  = intAttr(ts, "skipped");

            // Derive module: root/<module>/target/[surefire|failsafe]-reports/TEST-X.xml
            var rel = root.relativize(xml).toString().replace('\\', '/');
            var sep = rel.indexOf("/target/");
            var module = sep < 0 ? "" : rel.substring(0, sep);

            var fails = new ArrayList<Fail>();
            var tcs   = ts.getElementsByTagName("testcase");
            for (int i = 0; i < tcs.getLength(); i++) {
                var tc  = (Element) tcs.item(i);
                var cls = tc.getAttribute("classname");
                var tst = tc.getAttribute("name");
                for (var tag : List.of("failure", "error")) {
                    var nl = tc.getElementsByTagName(tag);
                    if (nl.getLength() > 0) {
                        var el = (Element) nl.item(0);
                        fails.add(new Fail(cls, tst, el.getAttribute("type"), el.getAttribute("message")));
                    }
                }
            }

            return new Suite(module, tests, failures, errors, skipped, fails);
        } catch (Exception e) {
            System.err.printf("[CI] Skipping %s: %s%n", xml, e.getMessage());
            return null;
        }
    }

    // ── XML helpers ──────────────────────────────────────────────────────────

    static Document parseXml(Path file) throws Exception {
        var fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(false);
        // Prevent XXE and avoid slow network lookups for DTDs
        fac.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fac.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fac.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return fac.newDocumentBuilder().parse(file.toFile());
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /**
     * Parses comma-separated {@code groupId:artifactId} tokens and maps each to
     * its module path via {@link #gaToPath}. Unknown GAs are logged and dropped.
     * Requires {@link #loadGraphFile} to have been called first.
     */
    static Set<String> resolveGas(String gas) {
        if (gas == null || gas.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(gas.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(ga -> {
                var path = gaToPath.get(ga);
                if (path == null) log("Warning: no reactor module for %s", ga);
                return path;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Assigns a compact alphabetic alias to each node (a, b, …, z, aa, ab, …, zz, aaa, …).
     * Aliases are assigned in iteration order of {@code nodes}.
     */
    static Map<String, String> buildAliasMap(Set<String> nodes) {
        var aliases = new LinkedHashMap<String, String>();
        int i = 0;
        for (var node : nodes) aliases.put(node, toAlias(i++));
        return aliases;
    }

    /** Maps a non-negative integer to a lowercase alphabetic string: 0→a, 25→z, 26→aa, … */
    static String toAlias(int n) {
        var sb = new StringBuilder();
        do {
            sb.insert(0, (char) ('a' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    /** Returns the last path segment (directory name), which is typically the artifactId. */
    static String shortName(String path) {
        if (path.isEmpty()) return "root";
        var idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    static int intAttr(Element el, String name) {
        try { return Integer.parseInt(el.getAttribute(name)); } catch (Exception e) { return 0; }
    }

    static String env(String name, String def) {
        var v = System.getenv(name);
        return v != null && !v.isBlank() ? v : def;
    }

    static void log(String fmt, Object... args) {
        System.out.printf("[CI] " + fmt + "%n", args);
    }
}
