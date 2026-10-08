package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.crypto.DiffieHellmanKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DhInitNative extends CipherNative {

    @Override
    public String nativeName() {
        return "dh-init";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("g", Set.of(BinaryValue.TYPE)),
                Parameter.required("p", Set.of(BinaryValue.TYPE)));
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
