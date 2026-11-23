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
package org.drools.verifier.core.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.drools.verifier.api.reporting.CheckType;
import org.drools.verifier.api.reporting.Issue;
import org.drools.verifier.api.reporting.Severity;
import org.drools.verifier.core.cache.inspectors.RuleInspector;
import org.drools.verifier.core.cache.inspectors.condition.ConditionInspector;
import org.drools.verifier.core.checks.base.CheckBase;
import org.drools.verifier.core.configuration.AnalyzerConfiguration;
import org.drools.verifier.core.index.model.FieldCondition;
import org.drools.verifier.core.index.model.ObjectField;
import org.drools.verifier.core.relations.Operator;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.drools.verifier.core.relations.Operator.resolve;

public class SingleRangeCheck extends CheckBase {

    private List<RangeError> errors = new ArrayList<>();

    private final Collection<RuleInspector> ruleInspectors;

    public SingleRangeCheck(final AnalyzerConfiguration configuration,
                            final Collection<RuleInspector> ruleInspectors) {
        super(configuration);
        this.ruleInspectors = ruleInspectors;
    }

    @Override
    public boolean check() {
        if (ruleInspectors == null || ruleInspectors.isEmpty()) {
            return hasIssues = false;
        }
        errors.clear();
        int conditionNr = ruleInspectors.iterator().next().getConditionsInspectors().size();
        for (int i = 0; i < conditionNr; i++) {
            checkCondition(i);
        }
        return hasIssues = !errors.isEmpty();
    }

    private void checkCondition(int conditionIndex) {
        Map<OperatorType, Set<ObjectField>> fields = ruleInspectors.stream()
                .map(r -> r.getConditionsInspectors().get(conditionIndex))
                .flatMap(cond -> cond.keySet().stream())
                .collect(groupingBy(f -> getFieldOperatorType(f, conditionIndex), toSet()));

        Set<ObjectField> rangeFields = fields.get(OperatorType.RANGE);
        if (rangeFields != null && !rangeFields.isEmpty()) {
            checkRanges(rangeFields, partition(fields.get(OperatorType.PARTITION), ruleInspectors, conditionIndex), conditionIndex);
        }
    }

    private void checkRanges(Collection<ObjectField> rangeFields, Map<PartitionKey, List<RuleInspector>> partitions, int conditionIndex) {
        for (Map.Entry<PartitionKey, List<RuleInspector>> partition : partitions.entrySet()) {
            List<List<? extends Range<?>>> dimensions = new ArrayList<>();

            for (ObjectField field : rangeFields) {
                if ("Integer".equals(field.getFieldType())) {
                    checkMonodimensionalRange(partition, dimensions, field, conditionIndex, IntegerRange::new, Integer.MIN_VALUE, Integer.MAX_VALUE);
                } else if ("Double".equals(field.getFieldType())) {
                    checkMonodimensionalRange(partition, dimensions, field, conditionIndex, NumericRange::new, Double.MIN_VALUE, Double.MAX_VALUE);
                } else {
                    checkMonodimensionalRange(partition, dimensions, field, conditionIndex, ComparableRange::new, ComparableWrapper.MIN_VALUE, ComparableWrapper.MAX_VALUE);
                }
            }

            checkBidimensionalRanges(partition, dimensions);
        }
    }

    private <T extends Comparable> void checkMonodimensionalRange(Entry<PartitionKey, List<RuleInspector>> partition,
                                                                  List<List<? extends Range<?>>> dimensions, ObjectField field, int conditionIndex,
                                                                  Function<List<ConditionInspector>, Range<T>> rangeSupplier, T min, T max) {
        List<Range<T>> ranges = partition.getValue().stream()
                .map(r -> r.getConditionsInspectors().get(conditionIndex))
                .map(c -> c.get(field))
                .map(rangeSupplier)
                .collect(toList());

        final T upper = getCoverageUpperBound(min, ranges);
        if ((upper != null && upper.equals(max)) || (upper == null && max == null)) {
            dimensions.add(ranges);
        } else {
            errors.add(new RangeError(partition.getValue(), partition.getKey(), upper));
        }
    }

    private void checkBidimensionalRanges(Entry<PartitionKey, List<RuleInspector>> partition, List<List<? extends Range<?>>> dimensions) {
        if (errors.isEmpty() && dimensions.size() >= 2) {
            for (int i = 0; i < dimensions.size() - 1; i++) {
                for (int j = i + 1; j < dimensions.size(); j++) {
                    if (!checkBidimensionalRanges(dimensions.get(i), dimensions.get(j))) {
                        errors.add(new RangeError(partition.getValue(), partition.getKey(), null));
                    }
                }
            }
        }
    }

    private <H extends Comparable, V extends Comparable> boolean checkBidimensionalRanges(List<? extends Range<? extends H>> hRanges,
                                                                                          List<? extends Range<? extends V>> vRanges) {
        List<BidimensionalRange<H, V>> bidiRanges = IntStream.range(0, hRanges.size())
                .mapToObj(i -> new BidimensionalRange<H, V>(hRanges.get(i), vRanges.get(i)))
                .sorted(Comparator.comparing(r -> r.horizontal))
                .collect(toList());

        SortedSet<H> hBreakPoints = new TreeSet<>();
        Map<H, List<BidimensionalRange<H, V>>> lowerHBounds = new HashMap<>();
        Map<H, List<BidimensionalRange<H, V>>> upperHBounds = new HashMap<>();
        for (BidimensionalRange<H, V> bidiRange : bidiRanges) {
            Range<? extends H> hRange = bidiRange.horizontal;
            hBreakPoints.add(hRange.lowerBound);
            lowerHBounds.computeIfAbsent(hRange.lowerBound, x -> new ArrayList<>()).add(bidiRange);
            if (!hRange.upperBound.equals(hRange.maxValue())) {
                hBreakPoints.add(hRange.upperBound);
                upperHBounds.computeIfAbsent(hRange.upperBound, x -> new ArrayList<>()).add(bidiRange);
            }
        }

        V minV = vRanges.get(0).minValue();
        V maxV = vRanges.get(0).maxValue();

        boolean first = true;
        List<BidimensionalRange<H, V>> parsedRanges = new ArrayList<>();
        for (H sweep : hBreakPoints) {
            List<BidimensionalRange<H, V>> enteringRanges = lowerHBounds.get(sweep);
            if (enteringRanges != null) {
                parsedRanges.addAll(enteringRanges);
            }

            List<BidimensionalRange<H, V>> exitingRanges = upperHBounds.get(sweep);
            if (exitingRanges != null) {
                parsedRanges.removeAll(exitingRanges);
            }

            if ((first || exitingRanges != null) &&
                    !maxV.equals(getCoverageUpperBound(minV, parsedRanges.stream().map(br -> br.vertical)))) {
                return false;
            }
            first = false;
        }

        return true;
    }

    private OperatorType getFieldOperatorType(ObjectField field, int conditionIndex) {
        return ruleInspectors.stream()
                .flatMap(r -> getConditionStream(r, field, conditionIndex))
                .map(c -> resolve(((FieldCondition) c.getCondition()).getOperator()))
                .map(OperatorType::decode)
                .reduce(OperatorType.PARTITION, OperatorType::combine);
    }

    enum OperatorType {
        PARTITION,
        RANGE,
        UNKNOWN;

        static OperatorType decode(Operator op) {
            return op == Operator.EQUALS ? PARTITION : op.isRangeOperator() ? RANGE : UNKNOWN;
        }

        OperatorType combine(OperatorType other) {
            if (this == UNKNOWN || other == UNKNOWN) {
                return UNKNOWN;
            }
            if (this == RANGE || other == RANGE) {
                return RANGE;
            }
            return PARTITION;
        }
    }

    private Map<PartitionKey, List<RuleInspector>> partition(Collection<ObjectField> partitionFields, Collection<RuleInspector> rules, int conditionIndex) {
        List<PartitionKey> keysWithNull = new ArrayList<>();

        Map<PartitionKey, List<RuleInspector>> partitions = new HashMap<>();
        for (RuleInspector rule : rules) {
            PartitionKey key = getPartitionKey(partitionFields, rule, conditionIndex);
            partitions.computeIfAbsent(key, k -> {
                if (k.hasNulls()) {
                    keysWithNull.add(k);
                }
                return new ArrayList<>();
            }).add(rule);
        }

        for (PartitionKey key : keysWithNull) {
            for (Map.Entry<PartitionKey, List<RuleInspector>> partition : partitions.entrySet()) {
                if (key.subsumes(partition.getKey())) {
                    partition.getValue().addAll(partitions.get(key));
                }
            }
        }

        keysWithNull.forEach(partitions::remove);
        return partitions;
    }

    private PartitionKey getPartitionKey(Collection<ObjectField> partitionFields, RuleInspector rule, int conditionIndex) {
        return partitionFields == null || partitionFields.isEmpty() ?
                PartitionKey.EMPTY_KEY :
                new PartitionKey(partitionFields.stream().map(f -> getValue(rule, f, conditionIndex)).toArray());
    }

    private Object getValue(RuleInspector rule, ObjectField field, int conditionIndex) {
        List<ConditionInspector> conditions = getConditions(rule, field, conditionIndex);
        return conditions != null ? conditions.get(0).getCondition().getValues().iterator().next() : null;
    }

    private Stream<ConditionInspector> getConditionStream(RuleInspector rule, ObjectField field, int conditionIndex) {
        List<ConditionInspector> conditionInspectors = getConditions(rule, field, conditionIndex);
        return conditionInspectors != null ? conditionInspectors.stream() : Stream.empty();
    }

    private List<ConditionInspector> getConditions(RuleInspector rule, ObjectField field, int conditionIndex) {
        return rule.getConditionsInspectors() != null ? rule.getConditionsInspectors().get(conditionIndex).get(field) : null;
    }

    private static class PartitionKey {

        private static PartitionKey EMPTY_KEY = new PartitionKey(new Object[0]);

        private final Object[] keys;

        private PartitionKey(Object[] keys) {
            this.keys = keys;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            } else {
                return Arrays.equals(keys, ((PartitionKey) obj).keys);
            }
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(keys);
        }

        @Override
        public String toString() {
            return Arrays.toString(keys);
        }

        boolean hasNulls() {
            return Stream.of(keys).anyMatch(Objects::isNull);
        }

        public boolean subsumes(PartitionKey other) {
            return IntStream.range(0, keys.length).allMatch(i -> keys[i] == null || keys[i].equals(other.keys[i]));
        }
    }

    private <T extends Comparable> T getCoverageUpperBound(T lowerBound, List<? extends Range<? extends T>> ranges) {
        return getCoverageUpperBound(lowerBound, ranges.stream());
    }

    private <T extends Comparable> T getCoverageUpperBound(T lowerBound, Stream<? extends Range<? extends T>> ranges) {
        T limit = lowerBound;
        Iterator<? extends Range<? extends T>> i = ranges.sorted().iterator();
        while (i.hasNext()) {
            Range<? extends T> range = i.next();
            if (range.lowerBound.compareTo(limit) > 0) {
                return limit;
            }
            limit = range.upperBound.compareTo(limit) > 0 ? range.upperBound : limit;
        }
        return limit;
    }

    private abstract static class Range<T extends Comparable> implements Comparable<Range<T>> {

        protected T lowerBound = minValue();
        protected T upperBound = maxValue();

        public Range(List<ConditionInspector> conditionInspectors) {
            if (conditionInspectors != null) {
                conditionInspectors.forEach(getConditionParser());
            }
        }

        protected abstract Consumer<ConditionInspector> getConditionParser();

        @Override
        public String toString() {
            return lowerBound + " < x < " + upperBound;
        }

        @Override
        public int compareTo(Range<T> o) {
            return lowerBound.compareTo(o.lowerBound);
        }

        protected abstract T minValue();

        protected abstract T maxValue();
    }

    private static class ComparableRange extends Range<ComparableWrapper> implements Comparable<Range<ComparableWrapper>> {

        ComparableRange(List<ConditionInspector> conditionInspectors) {
            super(conditionInspectors);
        }

        @Override
        protected Consumer<ConditionInspector> getConditionParser() {
            return c -> {
                FieldCondition cond = (FieldCondition) c.getCondition();
                Operator op = resolve(cond.getOperator());
                switch (op) {
                    case LESS_OR_EQUAL:
                    case LESS_THAN:
                        upperBound = new ComparableWrapper((Comparable) cond.getValues().iterator().next());
                        break;
                    case GREATER_THAN:
                    case GREATER_OR_EQUAL:
                        lowerBound = new ComparableWrapper((Comparable) cond.getValues().iterator().next());
                        break;
                }
            };
        }

        @Override
        protected ComparableWrapper minValue() {
            return ComparableWrapper.MIN_VALUE;
        }

        @Override
        protected ComparableWrapper maxValue() {
            return ComparableWrapper.MAX_VALUE;
        }
    }

    private static class IntegerRange extends Range<Integer> implements Comparable<Range<Integer>> {

        IntegerRange(List<ConditionInspector> conditionInspectors) {
            super(conditionInspectors);
        }

        @Override
        protected Consumer<ConditionInspector> getConditionParser() {
            return c -> {
                FieldCondition cond = (FieldCondition) c.getCondition();
                Operator op = resolve(cond.getOperator());
                switch (op) {
                    case LESS_OR_EQUAL:
                        upperBound = (Integer) cond.getValues().iterator().next() + 1;
                        break;
                    case LESS_THAN:
                        upperBound = (Integer) cond.getValues().iterator().next();
                        break;
                    case GREATER_OR_EQUAL:
                        lowerBound = (Integer) cond.getValues().iterator().next() - 1;
                        break;
                    case GREATER_THAN:
                        lowerBound = (Integer) cond.getValues().iterator().next();
                        break;
                    case EQUALS:
                        lowerBound = (Integer) cond.getValues().iterator().next();
                        upperBound = (Integer) cond.getValues().iterator().next();
                        break;
                }
            };
        }

        @Override
        protected Integer minValue() {
            return Integer.MIN_VALUE;
        }

        @Override
        protected Integer maxValue() {
            return Integer.MAX_VALUE;
        }
    }

    private static class NumericRange extends Range<Double> implements Comparable<Range<Double>> {

        NumericRange(List<ConditionInspector> conditionInspectors) {
            super(conditionInspectors);
        }

        @Override
        protected Consumer<ConditionInspector> getConditionParser() {
            return c -> {
                FieldCondition cond = (FieldCondition) c.getCondition();
                Operator op = resolve(cond.getOperator());
                switch (op) {
                    case LESS_OR_EQUAL:
                    case LESS_THAN:
                        upperBound = ((Number) cond.getValues().iterator().next()).doubleValue();
                        break;
                    case GREATER_THAN:
                    case GREATER_OR_EQUAL:
                        lowerBound = ((Number) cond.getValues().iterator().next()).doubleValue();
                        break;
                }
            };
        }

        @Override
        protected Double minValue() {
            return Double.MIN_VALUE;
        }

        @Override
        protected Double maxValue() {
            return Double.MAX_VALUE;
        }
    }

    private static class BidimensionalRange<H extends Comparable, V extends Comparable> {

        private final Range<? extends H> horizontal;
        private final Range<? extends V> vertical;

        private BidimensionalRange(Range<? extends H> horizontal, Range<? extends V> vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public String toString() {
            return "[" + horizontal + "][" + vertical + "]";
        }
    }

    @Override
    protected Issue makeIssue(Severity severity, CheckType checkType) {
        return errors.get(0).toIssue(severity, checkType);
    }

    private static class RangeError {

        private final Collection<RuleInspector> ruleInspectors;
        private final PartitionKey partitionKey;
        private final Object uncoveredValue;

        private RangeError(Collection<RuleInspector> ruleInspectors, PartitionKey partitionKey, Object uncoveredValue) {
            this.ruleInspectors = ruleInspectors;
            this.partitionKey = partitionKey;
            this.uncoveredValue = uncoveredValue;
        }

        private Issue toIssue(Severity severity, CheckType checkType) {
            return new Issue(severity,
                             checkType,
                             new HashSet<>(ruleInspectors.stream().map(r -> r.getRowIndex() + 1).collect(toSet()))
            ).setDebugMessage(getMessage());
        }

        private String getMessage() {
            return "Uncovered range" +
                    (uncoveredValue != null ? " starting from value " + uncoveredValue : "") +
                    (partitionKey != PartitionKey.EMPTY_KEY ? " in partition " + partitionKey : "");
        }
    }

    @Override
    protected CheckType getCheckType() {
        return CheckType.MISSING_RANGE;
    }

    @Override
    protected Severity getDefaultSeverity() {
        return Severity.NOTE;
    }
}
