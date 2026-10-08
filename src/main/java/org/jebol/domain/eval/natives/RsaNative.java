package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.crypto.RsaKey;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;

public class RsaNative extends CipherNative {

    private static final List<String> WHAT_RSA_DOES =
            List.of("encrypt", "decrypt", "sign", "verify");

    private static final String THE_DIGEST_WHEN_NONE_IS_NAMED = "sha256";

    @Override
    public String nativeName() {
        return "rsa";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("rsa-key", Set.of(Datatype.HANDLE)),
                Parameter.required("data", Typeset.ANY_STRING.membersAnd(Datatype.BINARY)),
                Parameter.belongingTo("verify", "signature", Set.of(Datatype.BINARY)),
                Parameter.belongingTo("hash", "algorithm", Set.of(Datatype.WORD, Datatype.NONE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("encrypt", "decrypt", "sign", "verify", "hash", "oaep", "pss");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<String> asked = WHAT_RSA_DOES.stream().filter(refinements::contains).toList();
            refuseAnythingButOneThing(asked, refinements);
            RsaKey key = keyHeldBy(arguments.getFirst(), RSA_HANDLE_TYPE, RsaKey.class)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_HANDLE,
                            arguments.getFirst() instanceof HandleValue other
                                    ? other.typeName() : "rsa-key"));
            if (asked.isEmpty()) {
                return NoneValue.none();
            }
            String action = asked.getFirst();
            if (!key.canDecryptAndSign() && (action.equals("decrypt") || action.equals("sign"))) {
                return NoneValue.none();
            }
            return done(action, key, arguments, refinements);
        };
    }

    private void refuseAnythingButOneThing(List<String> asked, Set<String> refinements) {
        boolean padded = refinements.contains("oaep") || refinements.contains("pss");
        if (asked.size() > 1
                || ((padded || refinements.contains("hash")) && asked.isEmpty())) {
            throw Raised.of(EvaluationFailure.BAD_REFINES, "rsa does one thing per call");
        }
    }

    private Value done(String action, RsaKey key, List<Value> arguments,
            Set<String> refinements) {

        byte[] data = arguments.get(1).asOctets();
        try {
            return switch (action) {
                case "encrypt" -> BinaryValue.ofBytes(
                        key.enciphered(data, refinements.contains("oaep")));
                case "decrypt" -> BinaryValue.ofBytes(
                        key.deciphered(data, refinements.contains("oaep")));
                case "sign" -> BinaryValue.ofBytes(key.signed(data,
                        digestNamedIn(arguments, refinements), refinements.contains("pss")));
                default -> LogicValue.of(key.verifies(data,
                        signatureGivenTo(arguments, refinements),
                        digestNamedIn(arguments, refinements),
                        refinements.contains("pss")));
            };
        } catch (Exception refused) {
            return NoneValue.none();
        }
    }

    private String digestNamedIn(List<Value> arguments, Set<String> refinements) {
        return argumentOf("hash", 0, arguments, refinements)
                .filter(AnyWordValue.class::isInstance)
                .map(digest -> ((AnyWordValue) digest).canonical())
                .orElse(THE_DIGEST_WHEN_NONE_IS_NAMED);
    }

    private byte[] signatureGivenTo(List<Value> arguments, Set<String> refinements) {
        return argumentOf("verify", 0, arguments, refinements)
                .filter(BinaryValue.class::isInstance)
                .map(signature -> ((BinaryValue) signature).octetsFromHere())
                .orElseGet(() -> new byte[0]);
    }
}
