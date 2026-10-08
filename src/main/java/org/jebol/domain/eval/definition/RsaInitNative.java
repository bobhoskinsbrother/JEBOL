package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.RsaKey;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RsaInitNative extends CipherNative {

    private static final Set<Datatype> A_BINARY = Set.of(Datatype.BINARY);

    @Override
    public String nativeName() {
        return "rsa-init";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("n", A_BINARY),
                Parameter.required("e", A_BINARY),
                Parameter.belongingTo("private", "d", A_BINARY),
                Parameter.belongingTo("private", "p", A_BINARY),
                Parameter.belongingTo("private", "q", A_BINARY));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("private");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] modulus = octetsAt(arguments, 0);
            byte[] publicExponent = octetsAt(arguments, 1);
            Optional<RsaKey> built = refinements.contains("private")
                    ? RsaKey.privateKeyFrom(modulus, publicExponent,
                            octetsAt(arguments, 2), octetsAt(arguments, 3),
                            octetsAt(arguments, 4))
                    : RsaKey.publicKeyFrom(modulus, publicExponent);
            return built.map(key -> aHandleHolding(RSA_HANDLE_TYPE, key, evaluator))
                    .orElseGet(NoneValue::none);
        };
    }

    private byte[] octetsAt(List<Value> arguments, int at) {
        return ((BinaryValue) arguments.get(at)).octetsFromHere();
    }
}
