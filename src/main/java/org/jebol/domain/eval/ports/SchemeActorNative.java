package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public final class SchemeActorNative extends DefaultNative {

    private static final String THE_ARGUMENT_NOTHING_CAN_FILL = "internal";

    private final String scheme;

    public SchemeActorNative(String scheme) {
        this.scheme = scheme;
        declaredBy(BlockValue.block(List.of(NoneValue.none(), WordValue.of(THE_ARGUMENT_NOTHING_CAN_FILL))),
                parametersAsWritten());
    }

    @Override
    public String nativeName() {
        return "the " + scheme + " actor";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required(THE_ARGUMENT_NOTHING_CAN_FILL, Set.of(Datatype.END)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw Raised.of(EvaluationFailure.CANNOT_USE, nativeName());
        };
    }
}
