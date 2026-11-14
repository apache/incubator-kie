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
package org.kie.kogito.explainability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualDomain;
import org.kie.kogito.explainability.api.CounterfactualDomainCategorical;
import org.kie.kogito.explainability.api.CounterfactualDomainRange;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainUnitValue;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.model.Feature;
import org.kie.kogito.explainability.model.Output;
import org.kie.kogito.explainability.model.Type;
import org.kie.kogito.explainability.model.Value;
import org.kie.kogito.explainability.model.domain.CategoricalFeatureDomain;
import org.kie.kogito.explainability.model.domain.EmptyFeatureDomain;
import org.kie.kogito.explainability.model.domain.FeatureDomain;
import org.kie.kogito.explainability.model.domain.NumericalFeatureDomain;
import org.kie.kogito.tracing.typedvalue.CollectionValue;
import org.kie.kogito.tracing.typedvalue.StructureValue;
import org.kie.kogito.tracing.typedvalue.TypedValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.vertx.core.json.JsonObject;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ConversionUtilsTest {

    private static NamedTypedValue getDoubleUnit(String name, double value) {
        return new NamedTypedValue(name, new UnitValue("number", DoubleNode.valueOf(value)));
    }

    private static CounterfactualSearchDomain getDoubleSearchDomain(String name, double lowerBound, double upperBound) {
        final CounterfactualDomainRange range = new CounterfactualDomainRange(DoubleNode.valueOf(lowerBound),
                DoubleNode.valueOf(upperBound));
        CounterfactualSearchDomainUnitValue searchDomain = new CounterfactualSearchDomainUnitValue("double",
                "double",
                Boolean.FALSE,
                range);
        return new CounterfactualSearchDomain(name, searchDomain);
    }

    @Test
    void toFeatureDomainsConstraintsSingleElement() {

        NamedTypedValue typedValue = getDoubleUnit("f-1", 20.0);
        CounterfactualSearchDomain domain = getDoubleSearchDomain("f-1", 18.0, 65.0);

        final List<Feature> features = ConversionUtils.toFeatureList(List.of(typedValue), List.of(domain));

        assertThat(features.size()).isEqualTo(1);
        final Feature feature = features.get(0);
        assertThat(feature.getType()).isEqualTo(Type.NUMBER);
        assertThat(feature.getName()).isEqualTo("f-1");
        assertThat(feature.getValue().asNumber()).isEqualTo(20.0);
        assertThat(feature.isConstrained()).isFalse();
        assertThat(feature.getDomain().isEmpty()).isFalse();
        assertThat(feature.getDomain().getLowerBound()).isEqualTo(18);
        assertThat(feature.getDomain().getUpperBound()).isEqualTo(65);
    }

    @Test
    void toFeatureDomainsConstraintsMultiElement() {

        final Random random = new Random();

        List<NamedTypedValue> values = IntStream.range(0, 10).mapToObj(i -> getDoubleUnit("f-" + i, random.nextDouble()))
                .collect(Collectors.toList());

        List<CounterfactualSearchDomain> domains = IntStream.range(0, 10).mapToObj(i -> getDoubleSearchDomain("f-" + i, -1, 1)).collect(
                Collectors.toList());

        final List<Feature> features = ConversionUtils.toFeatureList(values, domains);

        assertThat(features.size()).isEqualTo(10);
        assertThat(features.stream().allMatch(f -> f.getType() == Type.NUMBER)).isTrue();
        assertThat(features.stream().noneMatch(Feature::isConstrained)).isTrue();
        assertThat(features.stream().map(Feature::getDomain).noneMatch(FeatureDomain::isEmpty)).isTrue();
        assertThat(features.stream().map(Feature::getDomain).map(FeatureDomain::getLowerBound).allMatch(lb -> lb == -1.0)).isTrue();
        assertThat(features.stream().map(Feature::getDomain).map(FeatureDomain::getUpperBound).allMatch(ub -> ub == 1.0)).isTrue();
    }

    @Test
    void toFeatureTypedValue() {
        Feature name = ConversionUtils.toFeature("name", new UnitValue("number", new DoubleNode(10d)));
        assertThat(name).isNotNull();
        assertThat(name.getName()).isEqualTo("name");
        assertThat(name.getType()).isEqualTo(Type.NUMBER);
        assertThat(name.getValue().getUnderlyingObject()).isEqualTo(10d);
        assertThat(name.isConstrained()).isTrue();
        assertThat(name.getDomain().isEmpty()).isTrue();

        Feature name1 = ConversionUtils.toFeature("name1",
                new StructureValue("complex", singletonMap(
                        "key",
                        new UnitValue("string1", new TextNode("stringValue")))));
        assertThat(name1).isNotNull();
        assertThat(name1.isConstrained()).isTrue();
        assertThat(name1.getDomain().isEmpty()).isTrue();
        assertThat(name1.getName()).isEqualTo("name1");
        assertThat(name1.getType()).isEqualTo(Type.COMPOSITE);

        assertThat((name1.getValue().getUnderlyingObject() instanceof List)).isTrue();
        @SuppressWarnings("unchecked")
        List<Feature> features = (List<Feature>) name1.getValue().getUnderlyingObject();
        assertThat(features.size()).isEqualTo(1);
        assertThat(features.get(0).getType()).isEqualTo(Type.TEXT);
        assertThat(features.get(0).getValue().getUnderlyingObject()).isEqualTo("stringValue");

        List<TypedValue> values = List.of(new UnitValue("number", new DoubleNode(0d)),
                new UnitValue("number", new DoubleNode(1d)));
        Feature collectionFeature = ConversionUtils.toFeature("name", new CollectionValue("list", values));
        assertThat(collectionFeature).isNotNull();
        assertThat(collectionFeature.getName()).isEqualTo("name");
        assertThat(collectionFeature.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((collectionFeature.getValue().getUnderlyingObject() instanceof List)).isTrue();
        @SuppressWarnings("unchecked")
        List<Feature> objects = (List<Feature>) collectionFeature.getValue().getUnderlyingObject();
        assertThat(objects.size()).isEqualTo(2);
        for (Feature f : objects) {
            assertThat(f).isNotNull();
            assertThat(f.getName()).isNotNull();
            assertThat(f.getType()).isNotNull();
            assertThat(f.getType()).isEqualTo(Type.NUMBER);
            assertThat(f.getValue()).isNotNull();
        }
    }

    @Test
    void testNestedCollection() {
        Collection<TypedValue> depthTwoOne = new ArrayList<>(2);
        depthTwoOne.add(new StructureValue("complex", singletonMap(
                "key",
                new UnitValue("string1", new TextNode("value one")))));
        depthTwoOne.add(new StructureValue("complex", singletonMap(
                "key",
                new UnitValue("string1", new TextNode("value two")))));

        Collection<TypedValue> depthTwoTwo = new ArrayList<>(2);
        depthTwoTwo.add(new StructureValue("complex", singletonMap(
                "key",
                new UnitValue("string1", new TextNode("value three")))));
        depthTwoTwo.add(new StructureValue("complex", singletonMap(
                "key",
                new UnitValue("string1", new TextNode("value four")))));

        CollectionValue depthOneLeft = new CollectionValue("list", depthTwoOne);
        CollectionValue depthOneRight = new CollectionValue("list", depthTwoTwo);
        Collection<TypedValue> depthOne = new ArrayList<>(2);
        depthOne.add(depthOneLeft);
        depthOne.add(depthOneRight);
        CollectionValue value = new CollectionValue("list", depthOne);
        Feature collectionFeature = ConversionUtils.toFeature("name", value);
        assertThat(collectionFeature).isNotNull();
        assertThat(collectionFeature.getName()).isEqualTo("name");
        assertThat(collectionFeature.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((collectionFeature.getValue().getUnderlyingObject() instanceof List)).isTrue();
        @SuppressWarnings("unchecked")
        List<Feature> deepFeatures = (List<Feature>) collectionFeature.getValue().getUnderlyingObject();
        assertThat(deepFeatures.size()).isEqualTo(2);
        for (Feature f : deepFeatures) {
            assertThat(f).isNotNull();
            assertThat(f.getName()).isNotNull();
            assertThat(f.getType()).isNotNull();
            assertThat(f.getType()).isEqualTo(Type.COMPOSITE);
            assertThat(f.getValue()).isNotNull();
            List<Feature> nestedOneValues = (List<Feature>) f.getValue().getUnderlyingObject();
            for (Feature nestedOneValue : nestedOneValues) {
                assertThat(nestedOneValue).isNotNull();
                assertThat(nestedOneValue.getName()).isNotNull();
                assertThat(nestedOneValue.getType()).isNotNull();
                assertThat(nestedOneValue.getType()).isEqualTo(Type.COMPOSITE);
                assertThat(nestedOneValue.getValue()).isNotNull();
                List<Feature> nestedTwoValues = (List<Feature>) nestedOneValue.getValue().getUnderlyingObject();
                for (Feature nestedTwoValue : nestedTwoValues) {
                    assertThat(nestedTwoValue).isNotNull();
                    assertThat(nestedTwoValue.getName()).isNotNull();
                    assertThat(nestedTwoValue.getType()).isNotNull();
                    assertThat(nestedTwoValue.getType()).isEqualTo(Type.TEXT);
                    assertThat(nestedTwoValue.getValue()).isNotNull();
                    assertThat(nestedTwoValue.getValue().asString().contains("value")).isTrue();
                }
            }
        }
    }

    @Test
    void testToList() {
        assertThat(ConversionUtils.toList((JsonObject) null, (a, b) -> a)).isEqualTo(emptyList());
        assertThat(ConversionUtils.toList((Map<String, TypedValue>) null, (a, b) -> a)).isEqualTo(emptyList());
    }

    @Test
    void toOutputObject() {
        Output name = ConversionUtils.toOutput("name", 10d);
        assertThat(name).isNotNull();
        assertThat(name.getName()).isEqualTo("name");
        assertThat(name.getType()).isEqualTo(Type.NUMBER);
        assertThat(name.getValue().getUnderlyingObject()).isEqualTo(10d);

        JsonObject jsonObject = new JsonObject(singletonMap("key", 10d));
        Output output = ConversionUtils.toOutput("output", jsonObject);
        assertThat(output).isNotNull();
        assertThat(output.getName()).isEqualTo("output");
        assertThat(output.getType()).isEqualTo(Type.COMPOSITE);
        assertThat(name.getValue().getUnderlyingObject()).isEqualTo(10d);
    }

    @Test
    void toOutputTypedValue() {
        Output name = ConversionUtils.toOutput("name", new UnitValue("number", new DoubleNode(10d)));
        assertThat(name).isNotNull();
        assertThat(name.getName()).isEqualTo("name");
        assertThat(name.getType()).isEqualTo(Type.NUMBER);
        assertThat(name.getValue().getUnderlyingObject()).isEqualTo(10d);

        Output name1 = ConversionUtils.toOutput("name1",
                new StructureValue("complex", singletonMap(
                        "key",
                        new UnitValue("string1", new TextNode("stringValue")))));
        assertThat(name1).isNotNull();
        assertThat(name1.getName()).isEqualTo("name1");
        assertThat(name1.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((name1.getValue().getUnderlyingObject() instanceof List)).isTrue();
        @SuppressWarnings("unchecked")
        List<Output> outputs = (List<Output>) name1.getValue().getUnderlyingObject();
        assertThat(outputs.size()).isEqualTo(1);
        assertThat(outputs.get(0).getType()).isEqualTo(Type.TEXT);
        assertThat(outputs.get(0).getValue().getUnderlyingObject()).isEqualTo("stringValue");

        List<TypedValue> values = List.of(new UnitValue("number", new DoubleNode(0d)),
                new UnitValue("number", new DoubleNode(1d)));
        assertThat(ConversionUtils.toOutput("name", new CollectionValue("list", values))).isNotNull();
    }

    @Test
    void testToTypeValuePairObject() {
        commonTypeValuePairObject("string", Type.TEXT, "string");
        commonTypeValuePairObject(10d, Type.NUMBER, 10d);
        commonTypeValuePairObject(true, Type.BOOLEAN, true);
        assertThat(ConversionUtils.toTypeValuePair(new Object()).isPresent()).isFalse();
    }

    private void commonTypeValuePairObject(Object input, Type type, Object value) {
        Optional<Pair<Type, Value>> result = ConversionUtils.toTypeValuePair(input);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getKey()).isEqualTo(type);
        assertThat(result.get().getValue().getUnderlyingObject()).isEqualTo(value);
    }

    @Test
    void testToTypeValuePairJson() {
        commonTypeValuePairJson(new TextNode("string"), Type.TEXT, "string");
        commonTypeValuePairJson(new DoubleNode(10d), Type.NUMBER, 10d);
        commonTypeValuePairJson(BooleanNode.TRUE, Type.BOOLEAN, true);
        assertThat(ConversionUtils.toTypeValuePair(new ObjectNode(JsonNodeFactory.instance)).isPresent()).isFalse();
    }

    private void commonTypeValuePairJson(JsonNode input, Type type, Object value) {
        Optional<Pair<Type, Value>> result = ConversionUtils.toTypeValuePair(input);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getKey()).isEqualTo(type);
        assertThat(result.get().getValue().getUnderlyingObject()).isEqualTo(value);
    }

    @Test
    void testToFeatureDomain_UnitRangeInteger() {
        FeatureDomain featureDomain = ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("int",
                        "int",
                        true,
                        new CounterfactualDomainRange(IntNode.valueOf(18),
                                IntNode.valueOf(65))));
        assertThat((featureDomain instanceof NumericalFeatureDomain)).isTrue();
        NumericalFeatureDomain numericalFeatureDomain = (NumericalFeatureDomain) featureDomain;
        assertThat(numericalFeatureDomain.getLowerBound()).isEqualTo(18.0);
        assertThat(numericalFeatureDomain.getUpperBound()).isEqualTo(65.0);
        assertThat(numericalFeatureDomain.getCategories()).isNull();
    }

    @Test
    void testToFeatureDomain_UnitRangeDouble() {
        FeatureDomain featureDomain = ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("double",
                        "double",
                        true,
                        new CounterfactualDomainRange(DoubleNode.valueOf(-273.15),
                                DoubleNode.valueOf(Double.MAX_VALUE))));

        assertThat((featureDomain instanceof NumericalFeatureDomain)).isTrue();
        NumericalFeatureDomain numericalFeatureDomain = (NumericalFeatureDomain) featureDomain;
        assertThat(numericalFeatureDomain.getLowerBound()).isEqualTo(-273.15);
        assertThat(numericalFeatureDomain.getUpperBound()).isEqualTo(Double.MAX_VALUE);
        assertThat(numericalFeatureDomain.getCategories()).isNull();
    }

    @Test
    void testToFeatureDomain_UnitRangeString() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("string",
                        "string",
                        true,
                        new CounterfactualDomainRange(TextNode.valueOf("A"),
                                TextNode.valueOf("Z")))));
    }

    @Test
    void testToFeatureDomain_UnitCategoricalNumber() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("string",
                        "string",
                        true,
                        new CounterfactualDomainCategorical(List.of(IntNode.valueOf(1), IntNode.valueOf(2))))));
    }

    @Test
    void testToFeatureDomain_UnitCategoricalString() {
        FeatureDomain featureDomain = ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("string",
                        "string",
                        true,
                        new CounterfactualDomainCategorical(List.of(TextNode.valueOf("Black"), TextNode.valueOf("White")))));
        assertThat((featureDomain instanceof CategoricalFeatureDomain)).isTrue();
        CategoricalFeatureDomain categoricalFeatureDomain = (CategoricalFeatureDomain) featureDomain;
        assertThat(categoricalFeatureDomain.getCategories().size()).isEqualTo(2);
        assertThat(categoricalFeatureDomain.getCategories().containsAll(List.of("White", "Black"))).isTrue();
        assertThat(categoricalFeatureDomain.getLowerBound()).isNull();
        assertThat(categoricalFeatureDomain.getUpperBound()).isNull();
    }

    @Test
    void testToFeatureDomain_UnitFixedNumber() {
        FeatureDomain featureDomain = ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("integer",
                        "integer",
                        true,
                        null));
        assertThat((featureDomain instanceof EmptyFeatureDomain)).isTrue();
    }

    @Test
    void testToFeatureDomain_UnitFixedString() {
        FeatureDomain featureDomain = ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("string",
                        "string",
                        true,
                        null));
        assertThat((featureDomain instanceof EmptyFeatureDomain)).isTrue();
    }

    @Test
    void testToFeatureDomain_UnitNull() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ConversionUtils.toFeatureDomain(
                new CounterfactualSearchDomainUnitValue("integer",
                        "integer",
                        true,
                        new CounterfactualDomain() {
                            //New (unsupported) domain type
                        })));
    }

}
