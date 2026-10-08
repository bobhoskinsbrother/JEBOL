package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.EllipticCurveKey;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class EcdsaNative extends CipherNative {

    @Override
    public String nativeName() {
        return "ecdsa";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("key", Set.of(Datatype.HANDLE, Datatype.BINARY)),
                Parameter.required("hash", Set.of(Datatype.BINARY)),
                Parameter.belongingTo("verify", "signature", Set.of(Datatype.BINARY)),
                Parameter.belongingTo("curve", "type", Set.of(Datatype.WORD)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("sign", "verify", "curve");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] hash = ((BinaryValue) arguments.get(1)).octetsFromHere();
            if (refinements.contains("curve")
                    && arguments.getFirst() instanceof BinaryValue published) {
                return trueOrNone(EllipticCurveKey.aPublishedPointVerifies(
                        published.octetsFromHere(),
                        ((WordValue) argumentOf("curve", 0, arguments, refinements)
                                .orElseThrow()).canonical(),
                        hash,
                        ((BinaryValue) arguments.get(2)).octetsFromHere()));
            }
            Optional<EllipticCurveKey> key = keyHeldBy(
                    arguments.getFirst(), ECDH_HANDLE_TYPE, EllipticCurveKey.class)
                    .filter(held -> !held.released());
            if (key.isEmpty()) {
                return NoneValue.none();
            }
            return refinements.contains("verify")
                    ? trueOrNone(key.get().verifies(hash,
                            ((BinaryValue) arguments.get(2)).octetsFromHere()))
                    : signatureOver(key.get(), hash);
        };
    }

    private Value trueOrNone(boolean holds) {
        return holds ? LogicValue.yes() : NoneValue.none();
    }

    private Value signatureOver(EllipticCurveKey key, byte[] hash) {
        return key.signed(hash)
                .<Value>map(BinaryValue::ofBytes)
                .orElseGet(NoneValue::none);
    }
}
