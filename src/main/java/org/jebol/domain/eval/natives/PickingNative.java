package org.jebol.domain.eval.natives;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.eval.ImagePath;
import org.jebol.domain.eval.VectorPath;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

public abstract class PickingNative extends DefaultNative {

    protected Value pickBy(Value subject, Value selector) {
        return switch (subject) {
            case BitsetValue members -> new BitsetActions(members).pickForPath(selector);
            case DateValue date -> DatePart.readFrom(date, selector);
            case ImageValue image when selector instanceof AnyWordValue -> ImagePath.read(image, selector);
            case VectorValue vector when selector instanceof AnyWordValue -> VectorPath.read(vector, selector);
            case ImageValue image when !isPosition(selector) ->
                    throw Raised.of(EvaluationFailure.INVALID_PATH, image, selector);
            case VectorValue vector when !isPosition(selector) ->
                    throw Raised.of(EvaluationFailure.INVALID_PATH, vector, selector);
            case Value anythingElse -> anythingElse.pickBy(selector);
        };
    }

    private static boolean isPosition(Value selector) {
        return selector instanceof IntegerValue
                || selector instanceof AnyDecimalValue
                || selector instanceof LogicValue
                || selector instanceof PairValue
                || selector instanceof NoneValue;
    }
}
