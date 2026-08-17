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

package depgraphextractor;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Named("dep-graph-extractor")
@Singleton
public class DepGraphExtractorParticipant extends AbstractMavenLifecycleParticipant {

    private static final String OUT_PROP = "depGraphExtractor.out";

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        String outPath = session.getUserProperties().getProperty(OUT_PROP,
                session.getSystemProperties().getProperty(OUT_PROP, "dep-graph.tsv"));
        Path out = Paths.get(outPath).toAbsolutePath();

        ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        List<MavenProject> projects = session.getAllProjects();

        Set<String> reactorGas = new HashSet<>();
        for (MavenProject p : projects) reactorGas.add(ga(p));

        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            // format:
            // P<TAB>groupId:artifactId<TAB>/abs/basedir
            // D<TAB>groupId:artifactId<TAB>upstream-groupId:artifactId<TAB>scope
            // (one "D" line per direct upstream edge; downstream is the inverse)
            // V<TAB>groupId:artifactId<TAB>version<TAB>packaging
            // L<TAB>/abs/path/to/local/maven/repository
            // Consumers ignore record types they do not know, so new types are additive.
            for (MavenProject p : projects) {
                w.write("P\t");
                w.write(ga(p));
                w.write("\t");
                w.write(p.getBasedir().getAbsolutePath());
                w.newLine();
            }
            // Version and packaging let local tooling (script/dev/Dev.java) locate a
            // module's installed artifact in the local repository, to tell whether it is
            // present and up to date.
            for (MavenProject p : projects) {
                w.write("V\t");
                w.write(ga(p));
                w.write("\t");
                w.write(p.getVersion());
                w.write("\t");
                w.write(p.getPackaging());
                w.newLine();
            }
            if (session.getRequest().getLocalRepository() != null) {
                w.write("L\t");
                w.write(session.getRequest().getLocalRepository().getBasedir());
                w.newLine();
            }
            // The edge set is exactly ProjectDependencyGraph's, unchanged. The 4th field
            // says *why* the edge exists, which lets consumers tell a module that ships
            // against an upstream from one that only compiles its tests against it.
            for (MavenProject p : projects) {
                for (MavenProject up : graph.getUpstreamProjects(p, false)) {
                    w.write("D\t");
                    w.write(ga(p));
                    w.write("\t");
                    w.write(ga(up));
                    w.write("\t");
                    w.write(edgeScope(p, ga(up)));
                    w.newLine();
                }
            }
            // BOM-import edges: <dependencyManagement><scope>import</scope><type>pom</type>
            // entries are invisible to ProjectDependencyGraph, so a change to a reactor BOM
            // would not otherwise propagate to importing modules. Use the original model to
            // capture direct imports only — transitive imports ride the parent chain.
            // Also collect the distinct set of BOM GAs, emitted below as `B` records so
            // downstream tooling (CiSummary) can style them separately from built modules.
            Set<String> bomGas = new LinkedHashSet<>();
            for (MavenProject p : projects) {
                Model orig = p.getOriginalModel();
                if (orig == null) continue;
                DependencyManagement dm = orig.getDependencyManagement();
                if (dm == null) continue;
                for (Dependency d : dm.getDependencies()) {
                    if (!"import".equals(d.getScope())) continue;
                    if (!"pom".equals(d.getType())) continue;
                    String bomGa = d.getGroupId() + ":" + d.getArtifactId();
                    if (!reactorGas.contains(bomGa)) continue;
                    if (bomGa.equals(ga(p))) continue;
                    w.write("D\t");
                    w.write(ga(p));
                    w.write("\t");
                    w.write(bomGa);
                    w.write("\timport");
                    w.newLine();
                    bomGas.add(bomGa);
                }
            }
            // B<TAB>ga — marks each in-reactor BOM. 2-field record; P/D are 3-field.
            for (String bomGa : bomGas) {
                w.write("B\t");
                w.write(bomGa);
                w.newLine();
            }
        } catch (IOException e) {
            throw new MavenExecutionException("failed to write dep-graph dump", e);
        }

        // Graph dumped — that's all we wanted. Skip the actual build to save time.
        System.exit(0);
    }

    private static String ga(MavenProject p) {
        return p.getGroupId() + ":" + p.getArtifactId();
    }

    /**
     * Why {@code p} depends on {@code upstreamGa}, as one of:
     * a Maven dependency scope ({@code compile}, {@code test}, {@code provided},
     * {@code runtime}, {@code system}), {@code parent} when the edge is only the parent
     * chain, or {@code plugin} when it comes from a plugin or extension. A declared
     * dependency wins over the parent chain, because that is the stronger statement.
     *
     * <p>Only the reason is new; the edge itself is still whatever
     * {@link ProjectDependencyGraph} reported, so build scoping is unaffected.
     */
    private static String edgeScope(MavenProject p, String upstreamGa) {
        for (Dependency d : p.getDependencies()) {
            if (upstreamGa.equals(d.getGroupId() + ":" + d.getArtifactId())) {
                String scope = d.getScope();
                return (scope == null || scope.isEmpty()) ? "compile" : scope;
            }
        }
        MavenProject parent = p.getParent();
        if (parent != null && upstreamGa.equals(ga(parent))) {
            return "parent";
        }
        return "plugin";
    }
}
