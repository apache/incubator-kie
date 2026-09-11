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
package org.drools.quarkus.deployment;


import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;
import org.drools.quarkus.util.deployment.AbstractDroolsAssetsProcessor;
import org.drools.quarkus.util.deployment.GlobalsBuildItem;
import org.drools.quarkus.util.deployment.KmoduleKieBaseModelsBuiltItem;
import org.drools.quarkus.util.deployment.PatternsTypesBuildItem;

public class DroolsAssetsProcessor extends AbstractDroolsAssetsProcessor{

	
	public DroolsAssetsProcessor() {
		super();
	}
	
    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
    private static final String FEATURE = "drools";

    @BuildStep
    public void generateSources( 
    		ArchiveRootBuildItem root,
    	    LiveReloadBuildItem liveReload,
    	    CurateOutcomeBuildItem curateOutcomeBuildItem,
    	    CombinedIndexBuildItem combinedIndexBuildItem,
    	    Capabilities capabilities,
    	    BuildProducer<GeneratedBeanBuildItem> generatedBeans,
                                 BuildProducer<NativeImageResourceBuildItem> resource,
                                 BuildProducer<AdditionalStaticResourceBuildItem> staticResProducer,
                                 BuildProducer<GeneratedResourceBuildItem> genResBI,
                                 BuildProducer<PatternsTypesBuildItem> otnClasesBI,
                                 BuildProducer<KmoduleKieBaseModelsBuiltItem> kbaseModelsBI,
                                 BuildProducer<GlobalsBuildItem> globalsBI,
                                 BuildProducer<GeneratedJaxRsResourceBuildItem> jaxrsProducer) {
    	super.generateSources(root, liveReload, curateOutcomeBuildItem, combinedIndexBuildItem, capabilities, generatedBeans, resource, staticResProducer, genResBI, otnClasesBI, kbaseModelsBI, globalsBI, jaxrsProducer);
    }
}
