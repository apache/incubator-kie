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
package org.drools.mvel.integrationtests;

import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.util.stream.Stream;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.drools.compiler.kie.builder.impl.KieServicesImpl;
import org.drools.core.ClockType;
import org.drools.core.impl.InternalKieContainer;
import org.drools.core.management.DroolsManagementAgent;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestConstants;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.builder.model.KieSessionModel.KieSessionType;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.conf.MBeansOption;
import org.kie.api.io.Resource;
import org.kie.api.management.GenericKieSessionMonitoringMXBean;
import org.kie.api.management.KieContainerMonitorMXBean;
import org.kie.api.management.KieSessionMonitoringMXBean;
import org.kie.api.management.StatelessKieSessionMonitoringMXBean;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.runtime.conf.ForceEagerActivationOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class MBeansMonitoringTest {
    public static final Logger LOG = LoggerFactory.getLogger(MBeansMonitoringTest.class);
    
    public static final String KSESSION1 = "KSession1";
    public static final String KBASE1 = "KBase1";
    private static final String KBASE2 = "KBase2";
    private static final String KSESSION2 = "KSession2";
    private String mbeansprop;

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseStreamConfigurations(true).stream();
    }

    @BeforeEach
    public void setUp() throws Exception {
        (( KieServicesImpl ) KieServices.Factory.get()).nullKieClasspathContainer();
        ((KieServicesImpl) KieServices.Factory.get()).nullAllContainerIds();
        mbeansprop = System.getProperty( MBeansOption.PROPERTY_NAME );
        System.setProperty( MBeansOption.PROPERTY_NAME, "enabled" );    
    }

    @AfterEach
    public void tearDown() throws Exception {
        ((KieServicesImpl) KieServices.Factory.get()).nullKieClasspathContainer();
        ((KieServicesImpl) KieServices.Factory.get()).nullAllContainerIds();
        if (mbeansprop != null) {
            System.setProperty( MBeansOption.PROPERTY_NAME, mbeansprop );
        } else {
            System.setProperty( MBeansOption.PROPERTY_NAME, MBeansOption.DISABLED.toString() );
        }
    }
    
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKieClasspathMBeans(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
        KieServices ks = KieServices.Factory.get();

        KieContainer kc = ks.getKieClasspathContainer("myID");
        
        KieContainerMonitorMXBean c1Monitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID"),
                KieContainerMonitorMXBean.class);
        KieBase kb = kc.getKieBase("org.kie.monitoring.kbase1");
        KieSession statefulKieSession = kc.newKieSession("org.kie.monitoring.kbase1.ksession1");
        StatelessKieSession statelessKieSession = kc.newStatelessKieSession("org.kie.monitoring.kbase1.ksession2");
        
        KieSessionMonitoringMXBean statefulKieSessionMonitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID", "org.kie.monitoring.kbase1", KieSessionType.STATEFUL, "org.kie.monitoring.kbase1.ksession1"),
                KieSessionMonitoringMXBean.class);
        StatelessKieSessionMonitoringMXBean statelessKieSessionMonitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID", "org.kie.monitoring.kbase1", KieSessionType.STATEFUL, "org.kie.monitoring.kbase1.ksession1"),
                StatelessKieSessionMonitoringMXBean.class);

        assertThat(c1Monitor.getContainerId()).isEqualTo("myID");

        assertThat(c1Monitor.getConfiguredReleaseId().sameGAVof(KieContainerMonitorMXBean.CLASSPATH_KIECONTAINER_RELEASEID)).isTrue();
        assertThat(c1Monitor.getResolvedReleaseId().sameGAVof(KieContainerMonitorMXBean.CLASSPATH_KIECONTAINER_RELEASEID)).isTrue();
        assertThat(statefulKieSessionMonitor.getKieSessionName()).isEqualTo("org.kie.monitoring.kbase1.ksession1");
        assertThat(statefulKieSessionMonitor.getKieBaseId()).isEqualTo("org.kie.monitoring.kbase1");
        assertThat(statelessKieSessionMonitor.getKieSessionName()).isEqualTo("org.kie.monitoring.kbase1.ksession1");
        assertThat(statelessKieSessionMonitor.getKieBaseId()).isEqualTo("org.kie.monitoring.kbase1");
        
        
        KieContainer kc2 = ks.newKieClasspathContainer("myID2");
        KieContainerMonitorMXBean c2Monitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID2"),
                KieContainerMonitorMXBean.class);
        KieBase kb2 = kc2.getKieBase("org.kie.monitoring.kbase1");
        KieSession statefulKieSession2 = kc2.newKieSession("org.kie.monitoring.kbase1.ksession1");
        StatelessKieSession statelessKieSession2 = kc2.newStatelessKieSession("org.kie.monitoring.kbase1.ksession2");
        KieSessionMonitoringMXBean statefulKieSessionMonitor2 = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID2", "org.kie.monitoring.kbase1", KieSessionType.STATEFUL, "org.kie.monitoring.kbase1.ksession1"),
                KieSessionMonitoringMXBean.class);
        StatelessKieSessionMonitoringMXBean statelessKieSessionMonitor2 = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("myID2", "org.kie.monitoring.kbase1", KieSessionType.STATEFUL, "org.kie.monitoring.kbase1.ksession1"),
                StatelessKieSessionMonitoringMXBean.class);

        assertThat(c2Monitor.getContainerId()).isEqualTo("myID2");
        assertThat(c2Monitor.getConfiguredReleaseId().sameGAVof(KieContainerMonitorMXBean.CLASSPATH_KIECONTAINER_RELEASEID)).isTrue();
        assertThat(c2Monitor.getResolvedReleaseId().sameGAVof(KieContainerMonitorMXBean.CLASSPATH_KIECONTAINER_RELEASEID)).isTrue();
        assertThat(statefulKieSessionMonitor2.getKieSessionName()).isEqualTo("org.kie.monitoring.kbase1.ksession1");
        assertThat(statefulKieSessionMonitor2.getKieBaseId()).isEqualTo("org.kie.monitoring.kbase1");
        assertThat(statelessKieSessionMonitor2.getKieSessionName()).isEqualTo("org.kie.monitoring.kbase1.ksession1");
        assertThat(statelessKieSessionMonitor2.getKieBaseId()).isEqualTo("org.kie.monitoring.kbase1");
        
        kc.dispose();
        kc2.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testEventOffset(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String drl = "package org.drools.mvel.compiler.test\n" +
                "import org.drools.mvel.compiler.StockTick\n" +
                "declare StockTick\n" +
                "    @role(event)\n" +
                "    @expires(10s)\n" +
                "end\n" +
                "rule X\n" +
                "when\n" +
                "    StockTick()\n" +
                "then\n" +
                "end";

        KieServices ks = KieServices.Factory.get();

        KieModuleModel kproj = ks.newKieModuleModel();

        KieBaseModel kieBaseModel1 = kproj.newKieBaseModel( KBASE1 ).setDefault( true )
                .setEventProcessingMode( EventProcessingOption.STREAM );
        KieSessionModel ksession1 = kieBaseModel1.newKieSessionModel( KSESSION1 ).setDefault( true )
                .setType( KieSessionModel.KieSessionType.STATEFUL )
                .setClockType( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );

        ReleaseId releaseId1 = ks.newReleaseId( "org.kie.test", "mbeans", "1.0.0" );
        final Resource drlResource1 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(drl));
        drlResource1.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1.drl");
        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, kproj, drlResource1);

        KieContainer kc = ks.newKieContainer( releaseId1 );

        KieBase kiebase = kc.getKieBase( KBASE1 );
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();

        ObjectName kbOn = DroolsManagementAgent.createObjectNameFor((InternalKnowledgeBase) kiebase);
        mbserver.invoke( kbOn, "startInternalMBeans", new Object[0], new String[0] );

        Object expOffset = mbserver.getAttribute( new ObjectName( kbOn + ",group=EntryPoints,EntryPoint=DEFAULT,ObjectType=org.drools.mvel.compiler.StockTick" ), "ExpirationOffset" );
        assertThat(((Number) expOffset).longValue()).isEqualTo(10001);
        kc.dispose();
    }
    
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testContainerMBeans(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String drl = "package org.drools.mvel.compiler.test\n" +
                "import org.drools.mvel.compiler.StockTick\n" +
                "declare StockTick\n" +
                "    @role(event)\n" +
                "    @expires(10s)\n" +
                "end\n" +
                "rule X\n" +
                "when\n" +
                "    StockTick()\n" +
                "then\n" +
                "end";

        KieServices ks = KieServices.Factory.get();

        KieModuleModel kproj = ks.newKieModuleModel();

        KieBaseModel kieBaseModel1 = kproj.newKieBaseModel( KBASE1 ).setDefault( true )
                .setEventProcessingMode( EventProcessingOption.STREAM );
        KieSessionModel ksessionModel1 = kieBaseModel1.newKieSessionModel( KSESSION1 ).setDefault( true )
                .setType( KieSessionModel.KieSessionType.STATEFUL )
                .setClockType( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );

        ReleaseId releaseId1 = ks.newReleaseId( "org.kie.test", "mbeans", "1.0.0" );
        final Resource drlResource1 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(drl));
        drlResource1.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1.drl");
        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, kproj, drlResource1);
        
        KieContainer kc = ks.newKieContainer( releaseId1 );
        KieBase kiebase = kc.getKieBase( KBASE1 );
        kc.newKieSession(KSESSION1);
        kiebase.newKieSession();
        
        String kc1ID = ((InternalKieContainer) kc).getContainerId();
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
        LOG.debug("{}", mbserver.queryNames(new ObjectName("org.kie:kcontainerId="+ObjectName.quote(kc1ID)+",*"), null) );
        
        ReleaseId verRelease = ks.newReleaseId("org.kie.test", "mbeans", "RELEASE" );
        KieContainer kc2 = ks.newKieContainer( "Matteo", verRelease );
        kc2.newKieSession(KSESSION1);
        
        
        KieContainerMonitorMXBean c1Monitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy(kc1ID),
                KieContainerMonitorMXBean.class);
        assertThat(c1Monitor.getConfiguredReleaseIdStr()).isEqualTo(releaseId1.toExternalForm());
        assertThat(c1Monitor.getResolvedReleaseIdStr()).isEqualTo(releaseId1.toExternalForm());

        assertThat(c1Monitor.getConfiguredReleaseId().sameGAVof(releaseId1)).isTrue();
        assertThat(c1Monitor.getResolvedReleaseId().sameGAVof(releaseId1)).isTrue();
        assertThat(c1Monitor.getConfiguredReleaseId().getVersion()).isEqualTo(releaseId1.getVersion());
        assertThat(c1Monitor.getResolvedReleaseId().getVersion()).isEqualTo(releaseId1.getVersion());
        
        KieContainerMonitorMXBean c2Monitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy("Matteo"),
                KieContainerMonitorMXBean.class);
        assertThat(c2Monitor.getConfiguredReleaseIdStr()).isEqualTo(verRelease.toExternalForm());
        assertThat(c2Monitor.getResolvedReleaseIdStr()).isEqualTo(releaseId1.toExternalForm());

        assertThat(c2Monitor.getConfiguredReleaseId().sameGAVof(verRelease)).isTrue();
        assertThat(c2Monitor.getResolvedReleaseId().sameGAVof(releaseId1)).isTrue();
        assertThat(c2Monitor.getConfiguredReleaseId().getVersion()).isEqualTo(verRelease.getVersion());
        assertThat(c2Monitor.getResolvedReleaseId().getVersion()).isEqualTo(releaseId1.getVersion());

        // MBean are supported only via KieContainer public API.
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote(kc1ID) + ",*"), null).size()).isEqualTo(3);
        kc.dispose();
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote(kc1ID) + ",*"), null).size()).isEqualTo(0);
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote("Matteo") + ",*"), null).size()).isEqualTo(3);

        kc.dispose();
        kc2.dispose();
    }
    
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testAggregatedAndDispose(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String drl = "package org.drools.mvel.integrationtests\n" +
    
                "rule ND\n" +
                "when\n" +
                "    String() \n" +
                "    not ( Double() ) \n" +
                "then\n" +
                "    // do nothing. \n" +
                "end\n"+
                
                "rule ND2\n" +
                "salience 1\n" +
                "when\n" +
                "    $s : String() \n" +
                "    not ( Double() ) \n" +
                "then\n" +
                "    try { Thread.sleep(10); } catch (Exception e) { }\n" + 
                "    insert( new Double(0) );\n" +
                "    retract( $s );\n" +
                "end\n"
                
                ;

        KieServices ks = KieServices.Factory.get();

        KieModuleModel kproj = ks.newKieModuleModel();

        KieBaseModel kieBaseModel1 = kproj.newKieBaseModel( KBASE1 ).setDefault( true )
                .setEventProcessingMode( EventProcessingOption.STREAM );
        KieSessionModel ksessionModel1 = kieBaseModel1.newKieSessionModel( KSESSION1 ).setDefault( true )
                .setType( KieSessionModel.KieSessionType.STATEFUL )
                .setClockType( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );
        
        KieBaseModel kieBaseModel2 = kproj.newKieBaseModel( KBASE2 ).setDefault( false );
        KieSessionModel ksessionModel2 = kieBaseModel2.newKieSessionModel( KSESSION2 ).setDefault( true )
                .setType( KieSessionModel.KieSessionType.STATELESS );

        ReleaseId releaseId1 = ks.newReleaseId( "org.kie.test", "mbeans", "1.0.0" );
        final Resource drlResource1 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(drl));
        drlResource1.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1.drl");
        KieModule km = KieUtil.buildAndInstallKieModuleIntoRepo(kieBaseTestConfiguration, releaseId1, kproj, drlResource1);
        
        String containerId = "myContainerId";
        KieContainer kc = ks.newKieContainer( containerId, releaseId1 );
        KieBase kiebase = kc.getKieBase( KBASE1 );
        KieSessionConfiguration sessionConf = ks.newKieSessionConfiguration();
        sessionConf.setOption(ForceEagerActivationOption.YES);
        KieSession ksession = kc.newKieSession(KSESSION1, sessionConf);
        
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
        
        KieSessionMonitoringMXBean aggrMonitor = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy(containerId, KBASE1, KieSessionType.STATEFUL, KSESSION1),
                KieSessionMonitoringMXBean.class);
        
        long tft = 0; 
        
        print(aggrMonitor);
        checkAgendaTotals(aggrMonitor, 0, 0, 0);
        checkTotalFactCount(aggrMonitor, 0);
        tft = checkTotalFiringTimeGEQ(aggrMonitor, tft);
        checkTotalSessions(aggrMonitor, 1);
        assertThat(aggrMonitor.getStatsForRule("ND")).isNull();
        assertThat(aggrMonitor.getStatsForRule("ND2")).isNull();
        assertThat(aggrMonitor.getStatsByRule().containsKey("ND")).isFalse();
        assertThat(aggrMonitor.getStatsByRule().containsKey("ND2")).isFalse();
        
        ksession.insert("Ciao");
        ksession.fireAllRules();
        print(aggrMonitor);
        checkAgendaTotals(aggrMonitor, 2, 1, 1);
        checkTotalFactCount(aggrMonitor, 1);
        tft = checkTotalFiringTimeGEQ(aggrMonitor, tft + 1);
        checkTotalSessions(aggrMonitor, 1);
        checkStatsForRule(aggrMonitor,"ND" ,1,1,0);
        checkStatsForRule(aggrMonitor,"ND2",1,0,1);
        checkStatsByRule(aggrMonitor,"ND" ,1,1,0);
        checkStatsByRule(aggrMonitor,"ND2",1,0,1);
        
        ksession.fireAllRules();
        print(aggrMonitor);
        checkAgendaTotals(aggrMonitor, 2, 1, 1);
        checkTotalFactCount(aggrMonitor, 1);
        tft = checkTotalFiringTimeGEQ(aggrMonitor, tft);
        checkTotalSessions(aggrMonitor, 1);
        checkStatsForRule(aggrMonitor,"ND" ,1,1,0);
        checkStatsForRule(aggrMonitor,"ND2",1,0,1);
        checkStatsByRule(aggrMonitor,"ND" ,1,1,0);
        checkStatsByRule(aggrMonitor,"ND2",1,0,1);
        
        LOG.debug("---");
        
        KieSession ksession2 = kc.newKieSession(KSESSION1, sessionConf);
        ksession2.insert("Ciao");
        ksession2.fireAllRules();
        
        print(aggrMonitor);
        checkAgendaTotals(aggrMonitor, 4, 2, 2);
        checkTotalFactCount(aggrMonitor, 2);
        tft = checkTotalFiringTimeGEQ(aggrMonitor, tft + 1);
        checkTotalSessions(aggrMonitor, 2);
        checkStatsForRule(aggrMonitor,"ND" ,2,2,0);
        checkStatsForRule(aggrMonitor,"ND2",2,0,2);
        checkStatsByRule(aggrMonitor,"ND" ,2,2,0);
        checkStatsByRule(aggrMonitor,"ND2",2,0,2);
        
        ksession.dispose();
        checkTotalSessions(aggrMonitor, 1);
        checkTotalFactCount(aggrMonitor, 1);
        ksession2.dispose();
        checkTotalSessions(aggrMonitor, 0);
        checkTotalFactCount(aggrMonitor, 0);
        
        LOG.debug("--- NOW for the STATELESS ---");
        
        tft = 0;
        
        StatelessKieSession stateless = kc.newStatelessKieSession(KSESSION2, sessionConf);
        
        StatelessKieSessionMonitoringMXBean aggrMonitor2 = JMX.newMXBeanProxy(
                mbserver,
                DroolsManagementAgent.createObjectNameBy(containerId, KBASE2, KieSessionType.STATELESS, KSESSION2),
                StatelessKieSessionMonitoringMXBean.class);
        
        print(aggrMonitor2);
        checkAgendaTotals(aggrMonitor2, 0, 0, 0);
        checkRuleRuntimeTotals(aggrMonitor2, 0, 0);
        tft = checkTotalFiringTimeGEQ(aggrMonitor2, tft);
        checkTotalSessions(aggrMonitor2, 0);
        assertThat(aggrMonitor2.getStatsForRule("ND")).isNull();
        assertThat(aggrMonitor2.getStatsForRule("ND2")).isNull();
        assertThat(aggrMonitor2.getStatsByRule().containsKey("ND")).isFalse();
        assertThat(aggrMonitor2.getStatsByRule().containsKey("ND2")).isFalse();

        stateless.execute("Ciao");
        print(aggrMonitor2);
        checkAgendaTotals(aggrMonitor2, 2, 1, 1);
        checkRuleRuntimeTotals(aggrMonitor2, 2, 1);
        tft = checkTotalFiringTimeGEQ(aggrMonitor2, tft + 1);
        checkTotalSessions(aggrMonitor2, 1);
        checkStatsForRule(aggrMonitor2,"ND" ,1,1,0);
        checkStatsForRule(aggrMonitor2,"ND2",1,0,1);
        checkStatsByRule(aggrMonitor2,"ND" ,1,1,0);
        checkStatsByRule(aggrMonitor2,"ND2",1,0,1);
        
        stateless.execute("Ciao");
        print(aggrMonitor2);
        checkAgendaTotals(aggrMonitor2, 4, 2, 2);
        checkRuleRuntimeTotals(aggrMonitor2, 4, 2);
        tft = checkTotalFiringTimeGEQ(aggrMonitor2, tft + 1);
        checkTotalSessions(aggrMonitor2, 2);
        checkStatsForRule(aggrMonitor2,"ND" ,2,2,0);
        checkStatsForRule(aggrMonitor2,"ND2",2,0,2);
        checkStatsByRule(aggrMonitor2,"ND" ,2,2,0);
        checkStatsByRule(aggrMonitor2,"ND2",2,0,2);
        
        StatelessKieSession stateless2 = kc.newStatelessKieSession(KSESSION2, sessionConf);
        LOG.debug(stateless + " " + stateless2);
        checkTotalSessions(aggrMonitor2, 2);
        
        stateless2.execute("Ciao");
        print(aggrMonitor2);
        checkAgendaTotals(aggrMonitor2, 6, 3, 3);
        checkRuleRuntimeTotals(aggrMonitor2, 6, 3);
        tft = checkTotalFiringTimeGEQ(aggrMonitor2, tft + 1);
        checkTotalSessions(aggrMonitor2, 3);
        checkStatsForRule(aggrMonitor2,"ND" ,3,3,0);
        checkStatsForRule(aggrMonitor2,"ND2",3,0,3);
        checkStatsByRule(aggrMonitor2,"ND" ,3,3,0);
        checkStatsByRule(aggrMonitor2,"ND2",3,0,3);
        
        KieContainer kc2 = ks.newKieContainer( "kc2", releaseId1 );
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote(containerId) + ",*"), null).size()).isEqualTo(5);
        kc.dispose();
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote(containerId) + ",*"), null).size()).isEqualTo(0);
        assertThat(mbserver.queryNames(new ObjectName("org.kie:kcontainerId=" + ObjectName.quote("kc2") + ",*"), null).size()).isEqualTo(1);

        kc2.dispose();
    }
    
    private void checkStatsByRule(GenericKieSessionMonitoringMXBean mb, String ruleName, long mCreated, long mCancelled, long mFired) {
        assertThat(mb.getStatsByRule().get(ruleName).getMatchesCreated()).isEqualTo(mCreated);
        assertThat(mb.getStatsByRule().get(ruleName).getMatchesCancelled()).isEqualTo(mCancelled);
        assertThat(mb.getStatsByRule().get(ruleName).getMatchesFired()).isEqualTo(mFired);
    }

    private void checkStatsForRule(GenericKieSessionMonitoringMXBean mb, String ruleName, long mCreated, long mCancelled, long mFired) {
        assertThat(mb.getStatsForRule(ruleName).getMatchesCreated()).isEqualTo(mCreated);
        assertThat(mb.getStatsForRule(ruleName).getMatchesCancelled()).isEqualTo(mCancelled);
        assertThat(mb.getStatsForRule(ruleName).getMatchesFired()).isEqualTo(mFired);
    }

    private void print(GenericKieSessionMonitoringMXBean mb) {
        LOG.debug("total match created  : {}",mb.getTotalMatchesCreated());
        LOG.debug("total match cancelled: {}",mb.getTotalMatchesCancelled());
        LOG.debug("total match fired    : {}",mb.getTotalMatchesFired());
        if (mb instanceof StatelessKieSessionMonitoringMXBean) {
            StatelessKieSessionMonitoringMXBean c = (StatelessKieSessionMonitoringMXBean) mb;
            LOG.debug("inserted and deleted : +{} -{}",c.getTotalObjectsInserted(),c.getTotalObjectsDeleted());
        } else if (mb instanceof KieSessionMonitoringMXBean) {
            KieSessionMonitoringMXBean c = (KieSessionMonitoringMXBean) mb;
            LOG.debug("total tact count     : {}",c.getTotalFactCount());
        }
        LOG.debug("{} ms .", mb.getTotalFiringTime());
    }
    
    private long checkTotalFiringTimeGEQ(GenericKieSessionMonitoringMXBean mb, long amount) {
        assertThat(mb.getTotalFiringTime() >= amount).isTrue();
        return mb.getTotalFiringTime();
    }
    
    private void checkTotalSessions(GenericKieSessionMonitoringMXBean mb, int totalSessions) {
        assertThat(mb.getTotalSessions()).isEqualTo(totalSessions);
    }
    
    private void checkTotalFactCount(KieSessionMonitoringMXBean mb, int factCount) {
        assertThat(mb.getTotalFactCount()).isEqualTo(factCount);
    }
    
    private void checkRuleRuntimeTotals(StatelessKieSessionMonitoringMXBean mb, int inserted, int deleted) {
        assertThat(mb.getTotalObjectsInserted()).isEqualTo(inserted);
        assertThat(mb.getTotalObjectsDeleted()).isEqualTo(deleted);
    }
    
    private void checkAgendaTotals(GenericKieSessionMonitoringMXBean mb, long mCreated, long mCancelled, long mFired) {
        assertThat(mb.getTotalMatchesCreated()).isEqualTo(mCreated);
        assertThat(mb.getTotalMatchesCancelled()).isEqualTo(mCancelled);
        assertThat(mb.getTotalMatchesFired()).isEqualTo(mFired);
    }
    
}
