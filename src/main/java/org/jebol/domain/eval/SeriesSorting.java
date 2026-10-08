package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SeriesSorting {

    private final Evaluator evaluator;
    private final Optional<Value> comparator;
    private final boolean mindingCase;
    private final boolean reversed;
    private final boolean wholeRecord;
    private final boolean unstably;

    public SeriesSorting(Evaluator evaluator, Optional<Value> comparator, boolean mindingCase,
            boolean reversed, boolean wholeRecord, boolean unstably) {
        this.evaluator = evaluator;
        this.comparator = comparator;
        this.mindingCase = mindingCase;
        this.reversed = reversed;
        this.wholeRecord = wholeRecord;
        this.unstably = unstably;
    }

    public VectorValue sortedFront(VectorValue vector, int howMany) {
        int sorting = Math.max(0, howMany);
        long[] front = new long[sorting];
        for (int at = 0; at < sorting; at++) {
            front[at] = vector.storage().at(vector.index() + at);
        }
        VectorQuery.sortAscending(vector.kind(), front);
        for (int at = 0; at < sorting; at++) {
            vector.storage().set(vector.index() + at,
                    reversed ? front[sorting - 1 - at] : front[at]);
        }
        return vector;
    }

    public Value sorted(RebolSeries series, int stride, int howMany) {
        int step = Math.max(1, stride);
        List<Value> items = series.items().subList(0, Math.min(howMany, series.items().size()));
        List<List<Value>> records = new ArrayList<>();
        Map<List<Value>, Integer> whereEachRecordBegan = new IdentityHashMap<>();
        for (int at = 0; at + step <= items.size(); at += step) {
            List<Value> record = List.copyOf(items.subList(at, at + step));
            whereEachRecordBegan.put(record, at);
            records.add(record);
        }
        Comparator<List<Value>> ordering = (left, right) -> {
            int order = wholeRecord && comparator.isEmpty()
                    ? compareWholeRecords(left, right)
                    : compareRecords(left, right, series);
            return reversed ? -order : order;
        };
        if (unstably) {
            SymmetryPartitionSort.sort(records, ordering);
        } else {
            records = mergeSortedTakingFromTheLeftUnlessOutOfOrder(records, ordering);
        }
        if (series instanceof AnyBlockValue block) {
            putTheRecordsBackWithTheirMarks(block, records, whereEachRecordBegan, step);
            return series;
        }
        List<Value> ordered = records.stream().flatMap(List::stream).toList();
        for (int at = 0; at < ordered.size(); at++) {
            if (series instanceof AnyStringValue text
                    && ordered.get(at) instanceof CharacterValue(int codepoint)) {
                text.storage().set(text.index() + at, codepoint);
            } else if (series instanceof BinaryValue(BinaryStorage storage, int index)
                    && ordered.get(at) instanceof IntegerValue(long magnitude)) {
                storage.set(index + at, (int) magnitude);
            }
        }
        return series;
    }

    private void putTheRecordsBackWithTheirMarks(AnyBlockValue block, List<List<Value>> records,
                                                 Map<List<Value>, Integer> whereEachRecordBegan, int step) {
        List<Boolean> marksBefore = new ArrayList<>(records.size() * step);
        for (int at = 0; at < records.size() * step; at++) {
            marksBefore.add(block.storage().breaksLineAt(block.index() + at));
        }
        int landing = 0;
        for (List<Value> record : records) {
            int cameFrom = whereEachRecordBegan.get(record);
            for (int within = 0; within < record.size(); within++) {
                block.storage().set(block.index() + landing, record.get(within));
                block.storage().setLineBreakAt(block.index() + landing,
                        marksBefore.get(cameFrom + within));
                landing++;
            }
        }
    }

    private int compareRecords(List<Value> left, List<Value> right, RebolSeries series) {
        if (comparator.isEmpty()) {
            return Comparison.compareForSorting(left.getFirst(), right.getFirst(), mindingCase);
        }
        Value asked = comparator.get();
        if (asked instanceof IntegerValue column) {
            return compareByColumns(left, right, List.of(column));
        }
        if (asked instanceof AnyBlockValue columns) {
            return compareByColumns(left, right, columns.remaining());
        }
        return wholeRecord
                ? askComparatorTheOtherWayRound(asked,
                        lentRecordOf(series, left), lentRecordOf(series, right))
                : askComparatorTheOtherWayRound(asked,
                        lentElementOf(series, left.getFirst()),
                        lentElementOf(series, right.getFirst()));
    }

    private Value lentRecordOf(RebolSeries series, List<Value> record) {
        if (series instanceof BinaryValue) {
            int[] octets = new int[record.size()];
            for (int at = 0; at < record.size(); at++) {
                octets[at] = (int) ((IntegerValue) record.get(at)).magnitude();
            }
            return BinaryValue.of(octets);
        }
        if (series instanceof AnyStringValue) {
            StringBuilder characters = new StringBuilder();
            for (Value element : record) {
                characters.appendCodePoint(((CharacterValue) element).codepoint());
            }
            return StringValue.of(characters.toString());
        }
        AnyBlockValue lent = BlockValue.block(record);
        lent.storage().protectFromChange(true);
        return lent;
    }

    private Value lentElementOf(RebolSeries series, Value element) {
        return series instanceof BinaryValue && element instanceof IntegerValue(long magnitude)
                ? CharacterValue.of((int) magnitude)
                : element;
    }

    private <T> List<T> mergeSortedTakingFromTheLeftUnlessOutOfOrder(
            List<T> items, Comparator<T> order) {
        if (items.size() < 2) {
            return items;
        }
        int half = items.size() / 2;
        List<T> front = mergeSortedTakingFromTheLeftUnlessOutOfOrder(
                new ArrayList<>(items.subList(0, half)), order);
        List<T> back = mergeSortedTakingFromTheLeftUnlessOutOfOrder(
                new ArrayList<>(items.subList(half, items.size())), order);
        List<T> merged = new ArrayList<>(items.size());
        int here = 0;
        int there = 0;
        while (here < front.size() && there < back.size()) {
            if (order.compare(front.get(here), back.get(there)) <= 0) {
                merged.add(front.get(here));
                here++;
            } else {
                merged.add(back.get(there));
                there++;
            }
        }
        merged.addAll(front.subList(here, front.size()));
        merged.addAll(back.subList(there, back.size()));
        return merged;
    }

    private int compareWholeRecords(List<Value> left, List<Value> right) {
        for (int at = 0; at < Math.min(left.size(), right.size()); at++) {
            int order = Comparison.compareForSorting(left.get(at), right.get(at), mindingCase);
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private int compareByColumns(List<Value> left, List<Value> right, List<Value> columns) {
        for (Value asked : columns) {
            if (!(asked instanceof IntegerValue(long magnitude))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a column to sort by is a number, not "
                                + asked.datatype().literalSpelling());
            }
            int at = (int) magnitude - 1;
            if (at < 0 || at >= left.size() || at >= right.size()) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "there is no column " + magnitude + " to sort by");
            }
            int ordering = Comparison.compareForSorting(left.get(at), right.get(at), mindingCase);
            if (ordering != 0) {
                return ordering;
            }
        }
        return 0;
    }

    private int askComparatorTheOtherWayRound(Value asked, Value left, Value right) {
        Value answer = evaluator.applyFunction(asked, List.of(right, left));
        if (answer instanceof LogicValue(boolean truth)) {
            return truth ? 1 : -1;
        }
        if (Comparison.isNumeric(answer)) {
            double amount = Comparison.asDouble(answer);
            return amount > 0 ? 1 : amount == 0 ? 0 : -1;
        }
        return -1;
    }
}
