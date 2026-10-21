package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class CheckNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "check";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("series", TypesetValue.SERIES.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (carriesZeroAnywhere(arguments.getFirst())) {
                throw Raised.of(EvaluationFailure.BAD_SERIES);
            }
            return arguments.getFirst();
        };
    }

    private boolean carriesZeroAnywhere(Value series) {
        return switch (series) {
            case AnyStringValue text -> text.head().text().indexOf(0) >= 0;
            case BinaryValue octets -> holdsAZero(octets.head().octetsFromHere());
            default -> false;
        };
    }

    private boolean holdsAZero(byte[] octets) {
        for (byte one : octets) {
            if (one == 0) {
                return true;
            }
        }
        return false;
    }
}
