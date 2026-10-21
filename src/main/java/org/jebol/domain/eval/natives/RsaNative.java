package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.crypto.RsaKey;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;

public class RsaNative extends CipherNative {

    private static final List<String> WHAT_RSA_DOES =
            List.of("encrypt", "decrypt", "sign", "verify");

    private static final String DIGEST_WHEN_NONE_IS_NAMED = "sha256";

    @Override
    public String nativeName() {
        return "rsa";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("rsa-key", Set.of(HandleValue.TYPE)),
                Parameter.of("data", TypesetValue.ANY_STRING.membersAnd(BinaryValue.TYPE)),
                Parameter.belongTo("verify", "signature", Set.of(BinaryValue.TYPE)),
                Parameter.belongTo("hash", "algorithm", Set.of(WordValue.TYPE, NoneValue.TYPE)));
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
            RsaKey key = readKeyHeldBy(arguments.getFirst(), RSA_HANDLE_TYPE, RsaKey.class)
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
            return perform(action, key, arguments, refinements);
        };
    }

    private void refuseAnythingButOneThing(List<String> asked, Set<String> refinements) {
        boolean padded = refinements.contains("oaep") || refinements.contains("pss");
        if (asked.size() > 1
                || ((padded || refinements.contains("hash")) && asked.isEmpty())) {
            throw Raised.of(EvaluationFailure.BAD_REFINES, "rsa does one thing per call");
        }
    }

    private Value perform(String action, RsaKey key, List<Value> arguments,
                          Set<String> refinements) {

        byte[] data = arguments.get(1).asOctets();
        try {
            return switch (action) {
                case "encrypt" -> BinaryValue.ofBytes(
                        key.encipher(data, refinements.contains("oaep")));
                case "decrypt" -> BinaryValue.ofBytes(
                        key.decipher(data, refinements.contains("oaep")));
                case "sign" -> BinaryValue.ofBytes(key.sign(data,
                        digestNamedIn(arguments, refinements), refinements.contains("pss")));
                default -> LogicValue.of(key.verifies(data,
                        readSignatureGivenTo(arguments, refinements),
                        digestNamedIn(arguments, refinements),
                        refinements.contains("pss")));
            };
        } catch (Exception refused) {
            return NoneValue.none();
        }
    }

    private String digestNamedIn(List<Value> arguments, Set<String> refinements) {
        return readTheArgumentOf("hash", 0, arguments, refinements)
                .filter(AnyWordValue.class::isInstance)
                .map(digest -> ((AnyWordValue) digest).canonical())
                .orElse(DIGEST_WHEN_NONE_IS_NAMED);
    }

    private byte[] readSignatureGivenTo(List<Value> arguments, Set<String> refinements) {
        return readTheArgumentOf("verify", 0, arguments, refinements)
                .filter(BinaryValue.class::isInstance)
                .map(signature -> ((BinaryValue) signature).octetsFromHere())
                .orElseGet(() -> new byte[0]);
    }
}
