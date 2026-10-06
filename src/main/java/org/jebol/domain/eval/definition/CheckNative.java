package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class CheckNative extends DefaultNative {

    @Override
    public String name() {
        return "check";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series", Typeset.SERIES.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (theWholeSeriesCarriesAZero(arguments.getFirst())) {
                throw Raised.of(EvaluationFailure.BAD_SERIES);
            }
            return arguments.getFirst();
        };
    }

    private boolean theWholeSeriesCarriesAZero(Value series) {
        return switch (series) {
            case StringValue text -> text.head().text().indexOf(0) >= 0;
            case BinaryValue octets -> anyOfThemIsZero(octets.head().octetsFromHere());
            default -> false;
        };
    }

    private boolean anyOfThemIsZero(byte[] octets) {
        for (byte one : octets) {
            if (one == 0) {
                return true;
            }
        }
        return false;
    }
}
