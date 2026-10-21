package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SeriesSearch;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;

public class SelectAction extends SeriesSearchAction {

    @Override
    public String nativeName() {
        return "select";
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case", "skip", "any", "only", "last", "part", "same", "with",
                "reverse");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .askTheRebolActor("select", arguments, refinements)
                .orElseGet(() -> select(arguments, refinements));
    }

    private Value select(List<Value> arguments, Set<String> refinements) {
        Value subject = arguments.getFirst();
        Value wanted = arguments.get(1);
        return switch (subject) {
            case MapValue map -> map.select(wanted, refinements.contains("case"));
            case VectorValue numbers -> refuseDatatype(numbers);
            case NoneValue nothing -> nothing;
            case Value object when object.isAnyObject() -> readFieldWanted(object, wanted);
            case RebolSeries series -> {
                series.refuseNeedleItCannotHold(wanted, nativeName());
                yield findItemAfterMatch(series, wanted, arguments, refinements);
            }
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }

    private Value readFieldWanted(Value object, Value wanted) {
        if (!object.declaresAFieldFindCanReachBy(wanted)) {
            return NoneValue.none();
        }
        return object.readFieldValue(((AnyWordValue) wanted).canonical());
    }

    private Value findItemAfterMatch(
            RebolSeries series, Value wanted, List<Value> arguments, Set<String> refinements) {
        SeriesSearch search = new SeriesSearch(series, wanted, refinements,
                readTheArgumentOf("part", 0, arguments, refinements),
                readTheArgumentOf("skip", 0, arguments, refinements),
                readTheArgumentOf("with", 0, arguments, refinements));
        if (search.stridesForwardByLessThanOne()) {
            return refuseRecordWidthBelowOne(series, search.stride());
        }
        int found = search.findPosition();
        if (found < 0) {
            return NoneValue.none();
        }
        int after = found - 1 + search.measureTheLengthMatchedAt(found);
        return after >= search.end()
                ? NoneValue.none()
                : series.head().listTheItems().get(after);
    }

    private Value refuseRecordWidthBelowOne(RebolSeries series, long stride) {
        if (series instanceof AnyBlockValue) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "select/skip needs a record width of at least one, not " + stride);
        }
        return NoneValue.none();
    }
}
