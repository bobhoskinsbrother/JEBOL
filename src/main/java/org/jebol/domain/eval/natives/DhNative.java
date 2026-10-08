package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.crypto.DiffieHellmanKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DhNative extends CipherNative {

    @Override
    public String nativeName() {
        return "dh";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("dh-key", Set.of(Datatype.HANDLE)),
                Parameter.belongingTo("secret", "public-key", Set.of(Datatype.BINARY)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("public", "secret");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("public") && refinements.contains("secret")) {
                throw Raised.of(EvaluationFailure.BAD_REFINES,
                        "dh publishes or agrees, not both");
            }
            Optional<DiffieHellmanKey> key = keyHeldBy(
                    arguments.getFirst(), DHM_HANDLE_TYPE, DiffieHellmanKey.class)
                    .filter(held -> !held.released());
            if (key.isEmpty()) {
                return NoneValue.none();
            }
            if (refinements.contains("public")) {
                return BinaryValue.ofBytes(key.get().publishedPaddedToTheWidthOfThePrime());
            }
            if (refinements.contains("secret")) {
                return secretAgreedBetween(key.get(),
                        ((BinaryValue) arguments.get(1)).octetsFromHere());
            }
            return NoneValue.none();
        };
    }

    private Value secretAgreedBetween(DiffieHellmanKey key, byte[] peersValue) {
        return key.agreedWith(peersValue)
                .<Value>map(BinaryValue::ofBytes)
                .orElseGet(NoneValue::none);
    }
}
