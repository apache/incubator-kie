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
package org.kie.kogito.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataObserver;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.impl.InternalRuleUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.kogito.Application;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.data.Address;
import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.codegen.data.StockTick;
import org.kie.kogito.codegen.data.ValueDrop;
import org.kie.kogito.codegen.rules.RuleCodegenError;
import org.kie.kogito.codegen.rules.multiunit.MultiUnit;
import org.kie.kogito.codegen.rules.singleton.Datum;
import org.kie.kogito.codegen.rules.singleton.Singleton;
import org.kie.kogito.codegen.unit.AdultUnit;
import org.kie.kogito.codegen.unit.PersonsUnit;
import org.kie.kogito.codegen.unit.StockUnit;
import org.kie.kogito.rules.RuleUnits;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class RuleUnitCompilerIT extends AbstractCodegenIT {

    @Test
    public void testRuleUnit() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnit.drl");

        AdultUnit adults = new AdultUnit();

        adults.getPersons().add(new Person("Mario", 45));
        adults.getPersons().add(new Person("Marilena", 47));

        Person sofia = new Person("Sofia", 7);
        DataHandle dhSofia = adults.getPersons().add(sofia);

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        assertThat((instance.getClock() instanceof SessionPseudoClock)).isTrue();

        assertThat(instance.fire()).isEqualTo(2);
        assertThat(adults.getResults().getResults().containsAll(asList("Mario", "Marilena"))).isTrue();

        sofia.setAge(22);
        adults.getPersons().update(dhSofia, sofia);
        assertThat(instance.fire()).isEqualTo(1);
        assertThat(adults.getResults().getResults().containsAll(asList("Mario", "Marilena", "Sofia"))).isTrue();
    }

    @Test
    public void testRuleUnitModify() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitModify.drl");

        AdultUnit adults = new AdultUnit();

        Person sofia = new Person("Sofia", 7);
        DataHandle dhSofia = adults.getPersons().add(sofia);

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        assertThat(instance.fire()).isEqualTo(2);

        assertThat(adults.getResults().getResults().containsAll(asList("Sofia"))).isTrue();
    }

    @Test
    public void testRuleUnitDelete() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitDelete.drl");

        AdultUnit adults = new AdultUnit();

        adults.getPersons().add(new Person("Mario", 45));
        adults.getPersons().add(new Person("Marilena", 47));
        adults.getPersons().add(new Person("Sofia", 7));

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        instance.fire();

        List<String> results = adults.getResults().getResults();
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.containsAll(asList("Mario", "Marilena"))).isTrue();
    }

    @Test
    public void testRuleUnitQuery() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitQuery.drl");

        AdultUnit adults = new AdultUnit();

        adults.getPersons().add(new Person("Mario", 45));
        adults.getPersons().add(new Person("Marilena", 47));
        adults.getPersons().add(new Person("Sofia", 7));

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        Class<?> queryClass = application.getClass()
                .getClassLoader().loadClass("org.kie.kogito.codegen.unit.AdultUnitQueryFindAdults");

        List<String> results = (List<String>) queryClass.getMethod("execute", RuleUnitInstance.class).invoke(null, instance);

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.containsAll(asList("Mario", "Marilena"))).isTrue();
    }

    @Test
    public void testRuleUnitQueryOnPrimitive() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitQuery.drl");

        AdultUnit adults = new AdultUnit();

        adults.getPersons().add(new Person("Mario", 45));
        adults.getPersons().add(new Person("Marilena", 47));
        adults.getPersons().add(new Person("Sofia", 7));

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        List<Object> results = instance.executeQuery("FindAdultsAge").toList("$age");

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.containsAll(asList(45, 47))).isTrue();
    }

    @Test
    public void testRuleUnitQueryWithNoRules() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitQueryNoRules.drl");

        AdultUnit adults = new AdultUnit();

        adults.getPersons().add(new Person("Mario", 45).setAdult(true));
        adults.getPersons().add(new Person("Marilena", 47).setAdult(true));
        adults.getPersons().add(new Person("Sofia", 7).setAdult(true));

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        List<Object> results = instance.executeQuery("FindAdultsAge").toList("$sum");

        assertThat(results.size()).isEqualTo(1);
        assertThat(results).containsExactlyInAnyOrder(99);
    }

    @Test
    public void testRuleUnitExecutor() throws Exception {
        Application application = createApplication(
                "org/kie/kogito/codegen/unit/RuleUnit.drl",
                "org/kie/kogito/codegen/unit/PersonsUnit.drl");

        DataStore<Person> persons = DataSource.createStore();
        persons.add(new Person("Mario", 45));
        persons.add(new Person("Marilena", 17));
        persons.add(new Person("Sofia", 7));

        RuleUnit<AdultUnit> adultUnit = application.get(RuleUnits.class).create(AdultUnit.class);

        AdultUnit adultData18 = new AdultUnit(persons, 18);
        RuleUnitInstance<AdultUnit> adultUnitInstance18 = ((InternalRuleUnit) adultUnit).createInstance(adultData18, "adult18");

        AdultUnit adultData21 = new AdultUnit(persons, 21);
        RuleUnitInstance<AdultUnit> adultUnitInstance21 = ((InternalRuleUnit) adultUnit).createInstance(adultData21, "adult21");

        RuleUnit<PersonsUnit> personsUnit = application.get(RuleUnits.class).create(PersonsUnit.class);
        personsUnit.createInstance(new PersonsUnit(persons)).fire();

        assertThat(adultData18.getResults().getResults().size()).isEqualTo(2);
        assertThat(adultData18.getResults().getResults().containsAll(asList("Mario", "Marilena"))).isTrue();
        assertThat(adultData21.getResults().getResults().size()).isEqualTo(1);
        assertThat(adultData21.getResults().getResults().containsAll(asList("Mario"))).isTrue();
    }

    @Test
    public void generateSinglePackageSingleUnit() throws Exception {
        Application application = createApplication(
                "org/kie/kogito/codegen/rules/multiunit/MultiUnit.drl",
                "org/kie/kogito/codegen/rules/multiunit/MultiUnit2.drl");

        ArrayList<String> strings = new ArrayList<>();

        RuleUnit<MultiUnit> mu = application.get(RuleUnits.class).create(MultiUnit.class);
        MultiUnit data = new MultiUnit();
        RuleUnitInstance<MultiUnit> instance = mu.createInstance(data);
        data.getValues().subscribe(DataObserver.of(v -> {
            if (v != null)
                strings.add((String) v);
        }));
        data.getValues().add("start");
        instance.fire();

        assertThat(strings).isEqualTo(asList("start", "middle", "done"));

    }

    @Test
    public void singletonStore() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/rules/singleton/Singleton.drl");

        ArrayList<String> data = new ArrayList<>();
        AtomicReference<Datum> lastSeen = new AtomicReference<>();

        RuleUnit<Singleton> mu = application.get(RuleUnits.class).create(Singleton.class);
        Singleton unitData = new Singleton();
        RuleUnitInstance<Singleton> instance = mu.createInstance(unitData);
        unitData.getOutput().subscribe(
                DataObserver.ofUpdatable(v -> data.add(v == null ? null : v.getValue())));
        unitData.getOutput().subscribe(
                DataObserver.of(lastSeen::set));

        unitData.getInput().set(new Datum("start"));
        instance.fire();
        assertThat(data).isEqualTo(asList("continue", "updated", null, "done"));

        lastSeen.get().setValue("updated");
        unitData.getOutput().update();
        instance.fire();

        assertThat(data).isEqualTo(asList("continue", "updated", null, "done", "updated", null, "done"));

    }

    @Test
    public void test2PatternsOopath() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/TwoPatternsQuery.drl");

        AdultUnit adults = new AdultUnit();

        Person mario = new Person("Mario", 42);
        mario.addAddress(new Address("Milano"));
        Person mark = new Person("Mark", 40);
        mark.addAddress(new Address("London"));
        Person edson = new Person("Edson", 37);
        edson.addAddress(new Address("Toronto"));

        adults.getPersons().add(mario);
        adults.getPersons().add(mark);
        adults.getPersons().add(edson);

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        List<Object> results = instance.executeQuery("FindPeopleInMilano").toList("$p");

        assertThat(results.size()).isEqualTo(1);
        assertThat(((Person) results.get(0)).getName()).isEqualTo("Mario");
    }

    @Test
    public void testRuleUnitWithNoBindQueryShouldntCompile() throws Exception {
        try {
            Application application = createApplication("org/kie/kogito/codegen/unit/RuleUnitNoBindQuery.drl");
            fail("A query without binding shouldn't compile");
        } catch (RuleCodegenError e) {
            // ignore
        }
    }

    @Test
    public void testCep() throws Exception {
        Application application = createApplication("org/kie/kogito/codegen/unit/Stock.drl");

        StockUnit stockUnit = new StockUnit();
        RuleUnit<StockUnit> unit = application.get(RuleUnits.class).create(StockUnit.class);
        RuleUnitInstance<StockUnit> instance = unit.createInstance(stockUnit);

        stockUnit.getStockTicks().append(new StockTick("IBM", 2000, 100));
        stockUnit.getStockTicks().append(new StockTick("IBM", 1700, 170));
        stockUnit.getStockTicks().append(new StockTick("IBM", 1500, 240));

        ValueDrop valueDrop = (ValueDrop) instance.executeQuery("highestValueDrop", "IBM").iterator().next().get("$s");
        assertThat(valueDrop.getDropAmount()).isEqualTo(300);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void checckPropertyReactvity(boolean usePropertyReactivity) throws Exception {
        // KOGITO-5101
        KogitoBuildContext context = newContext();
        if (!usePropertyReactivity) {
            context.setApplicationProperty(PropertySpecificOption.PROPERTY_NAME, PropertySpecificOption.DISABLED.toString());
        }

        Application application = createApplication(context, "org/kie/kogito/codegen/unit/RuleUnitNoPropReact.drl");

        AdultUnit adults = new AdultUnit();

        Person mario = new Person("Mario", 45);
        adults.getPersons().add(mario);

        RuleUnit<AdultUnit> unit = application.get(RuleUnits.class).create(AdultUnit.class);
        RuleUnitInstance<AdultUnit> instance = unit.createInstance(adults);

        instance.fire();

        assertThat(mario.getAge()).isEqualTo(usePropertyReactivity ? 46 : 50);
    }

    private Application createApplication(String... drls) throws Exception {
        KogitoBuildContext context = newContext();
        return createApplication(context, drls);
    }

    private Application createApplication(KogitoBuildContext context, String... drls) throws Exception {
        Map<TYPE, List<String>> resourcesTypeMap = new HashMap<>();
        resourcesTypeMap.put(TYPE.RULES, Arrays.asList(drls));
        Application application = generateCode(resourcesTypeMap, context);
        return application;
    }
}
