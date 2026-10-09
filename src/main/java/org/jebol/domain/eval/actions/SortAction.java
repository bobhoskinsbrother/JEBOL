package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SeriesSorting;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SortAction extends DefaultNative implements ActionValue {

    private static final int EACH_ITEM_ON_ITS_OWN = 1;

    @Override
    public String nativeName() {
        return "sort";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series", TypesetValue.SERIES.members()),
                Parameter.belongingTo("skip", "size", Set.of(IntegerValue.TYPE)),
                Parameter.belongingTo("compare", "comparator", Set.of()),
                Parameter.belongingTo("part", "count", aPartLimit()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case", "compare", "skip", "reverse", "all", "part", "unstable");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (!(arguments.getFirst() instanceof RebolSeries series)) {
                return refuseTheDatatype(arguments.getFirst());
            }
            Optional<Value> comparator = argumentOf("compare", 0, arguments, refinements);
            Optional<Value> partCount = argumentOf("part", 0, arguments, refinements);
            SeriesSorting sorting = new SeriesSorting(evaluator, comparator,
                    refinements.contains("case"), refinements.contains("reverse"),
                    refinements.contains("all"),
                    refinements.contains("unstable") || series instanceof BinaryValue);
            if (series instanceof VectorValue vector) {
                refuseRecordsInAVector(refinements);
                return sorting.sortedFront(vector, howManyOf(vector, partCount));
            }
            int howMany = Math.max(0, howManyOf(series, partCount));
            if (howMany <= 1) {
                return series;
            }
            int stride = argumentOf("skip", 0, arguments, refinements)
                    .map(size -> (int) ((IntegerValue) size).magnitude())
                    .orElse(EACH_ITEM_ON_ITS_OWN);
            refuseWhatCannotBeSorted(refinements, howMany, stride, comparator);
            return sorting.sorted(series, stride, howMany);
        };
    }

    private int howManyOf(RebolSeries series, Optional<Value> partCount) {
        return partCount.filter(IntegerValue.class::isInstance)
                .map(count -> (int) Math.min(((IntegerValue) count).magnitude(),
                        series.lengthFromHere()))
                .orElse(series.lengthFromHere());
    }

    private void refuseRecordsInAVector(Set<String> refinements) {
        if (refinements.contains("skip") || refinements.contains("compare")) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "sort/skip and sort/compare on a vector!");
        }
    }

    private void refuseWhatCannotBeSorted(Set<String> refinements, int howMany, int stride,
            Optional<Value> comparator) {
        if (refinements.contains("skip")
                && (stride < 1 || stride > howMany || howMany % stride != 0)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a record width of " + stride + " does not divide " + howMany);
        }
        if (comparator.isPresent()
                && comparator.get() instanceof IntegerValue(long magnitude)
                && (!refinements.contains("skip") || magnitude < 1 || magnitude > stride)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "there is no column " + Molder.mold(comparator.get()) + " to sort by");
        }
        if (refinements.contains("all")
                && comparator.isPresent()
                && !comparator.get().datatype().belongsTo(TypesetValue.ANY_FUNCTION)) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "sort/all compares whole records, so a column has nothing left to say");
        }
    }
}
