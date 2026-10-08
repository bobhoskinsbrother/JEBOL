package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class PickzNative extends PickingNative {

    @Override
    public String nativeName() {
        return "pickz";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series"),
                Parameter.required("index", Set.of(Datatype.INTEGER)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (arguments.getFirst() instanceof BitsetValue members) {
                return pickedBy(members, arguments.get(1));
            }
            int countedFromNought = (int) ((IntegerValue) arguments.get(1)).magnitude();
            return arguments.getFirst().picked(countedFromNought >= 0
                    ? countedFromNought + 1
                    : countedFromNought);
        };
    }
}
