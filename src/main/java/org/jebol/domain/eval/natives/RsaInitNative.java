package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.crypto.RsaKey;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RsaInitNative extends CipherNative {

    private static final Set<Datatype> A_BINARY = Set.of(BinaryValue.TYPE);

    @Override
    public String nativeName() {
        return "rsa-init";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("n", A_BINARY),
                Parameter.of("e", A_BINARY),
                Parameter.belongTo("private", "d", A_BINARY),
                Parameter.belongTo("private", "p", A_BINARY),
                Parameter.belongTo("private", "q", A_BINARY));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("private");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] modulus = readOctetsAt(arguments, 0);
            byte[] publicExponent = readOctetsAt(arguments, 1);
            Optional<RsaKey> built = refinements.contains("private")
                    ? RsaKey.makeAPrivateKeyFrom(modulus, publicExponent,
                            readOctetsAt(arguments, 2), readOctetsAt(arguments, 3),
                            readOctetsAt(arguments, 4))
                    : RsaKey.makeAPublicKeyFrom(modulus, publicExponent);
            return built.map(key -> wrapInHandle(RSA_HANDLE_TYPE, key, evaluator))
                    .orElseGet(NoneValue::none);
        };
    }

    private byte[] readOctetsAt(List<Value> arguments, int at) {
        return ((BinaryValue) arguments.get(at)).octetsFromHere();
    }
}
