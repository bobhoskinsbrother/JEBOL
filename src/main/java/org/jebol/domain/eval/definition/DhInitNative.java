package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.DiffieHellmanKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DhInitNative extends CipherNative {

    @Override
    public String name() {
        return "dh-init";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("g", Set.of(Datatype.BINARY)),
                Parameter.required("p", Set.of(Datatype.BINARY)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DiffieHellmanKey.generatedFor(
                        ((BinaryValue) arguments.get(0)).octetsFromHere(),
                        ((BinaryValue) arguments.get(1)).octetsFromHere())
                .map(key -> aHandleHolding(DHM_HANDLE_TYPE, key, evaluator))
                .orElseGet(NoneValue::none);
    }
}
