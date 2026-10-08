package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.EllipticCurveKey;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class EcdhNative extends CipherNative {

    private static final List<String> WHAT_ECDH_DOES = List.of("init", "curve", "public", "secret");

    @Override
    public String nativeName() {
        return "ecdh";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("key", Set.of(Datatype.HANDLE, Datatype.NONE)),
                Parameter.belongingTo("init", "type", Set.of(Datatype.WORD)),
                Parameter.belongingTo("secret", "public-key", Set.of(Datatype.BINARY)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.copyOf(WHAT_ECDH_DOES);
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            refuseMoreThanOneThingAtOnce(refinements);
            if (refinements.contains("init")) {
                return curveKeyMadeOn(arguments.getFirst(),
                        ((WordValue) arguments.get(1)).canonical(), evaluator);
            }
            Optional<EllipticCurveKey> key = keyHeldBy(
                    arguments.getFirst(), ECDH_HANDLE_TYPE, EllipticCurveKey.class)
                    .filter(held -> !held.released());
            if (key.isEmpty()) {
                return NoneValue.none();
            }
            if (refinements.contains("curve")) {
                return WordValue.of(key.get().curveName());
            }
            if (refinements.contains("public")) {
                return BinaryValue.ofBytes(key.get().publishedPoint());
            }
            if (refinements.contains("secret")) {
                return secretAgreedBetween(key.get(), ((BinaryValue) argumentOf(
                        "secret", 0, arguments, refinements).orElseThrow()).octetsFromHere());
            }
            return UnsetValue.unset();
        };
    }

    private void refuseMoreThanOneThingAtOnce(Set<String> refinements) {
        if (WHAT_ECDH_DOES.stream().filter(refinements::contains).count() > 1) {
            throw Raised.of(EvaluationFailure.BAD_REFINES, "ecdh does one thing per call");
        }
    }

    private Value curveKeyMadeOn(Value given, String curveName, Evaluator evaluator) {
        Optional<EllipticCurveKey> standing =
                keyHeldBy(given, ECDH_HANDLE_TYPE, EllipticCurveKey.class);
        if (standing.isPresent()) {
            return standing.get().startAgainOn(curveName) ? given : NoneValue.none();
        }
        return EllipticCurveKey.onCurve(curveName)
                .map(key -> aHandleHolding(ECDH_HANDLE_TYPE, key, evaluator))
                .orElseGet(NoneValue::none);
    }

    private Value secretAgreedBetween(EllipticCurveKey key, byte[] peersPoint) {
        return key.agreedWith(peersPoint)
                .<Value>map(BinaryValue::ofBytes)
                .orElseGet(NoneValue::none);
    }
}
