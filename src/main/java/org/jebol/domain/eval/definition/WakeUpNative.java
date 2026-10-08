package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;

import java.util.List;
import java.util.Set;

public class WakeUpNative extends PortWakingNative {

    @Override
    public String nativeName() {
        return "wake-up";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("port", Set.of(Datatype.PORT)),
                Parameter.required("event", Set.of(Datatype.EVENT)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                wakes((PortValue) arguments.get(0), arguments.get(1), evaluator));
    }
}
