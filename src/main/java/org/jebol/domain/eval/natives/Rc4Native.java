package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.crypto.StreamCipher;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class Rc4Native extends CipherNative {

    @Override
    public String nativeName() {
        return "rc4";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongTo("key", "crypt-key", Set.of(BinaryValue.TYPE)),
                Parameter.belongTo("stream", "ctx", Set.of(HandleValue.TYPE)),
                Parameter.belongTo("stream", "data", Set.of(BinaryValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("key", "stream");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("stream")) {
                return encipherThroughStreamInPlace(
                        (HandleValue) refinementArgument("stream", 0, arguments, refinements)
                                .orElseThrow(),
                        (BinaryValue) refinementArgument("stream", 1, arguments, refinements)
                                .orElseThrow());
            }
            if (refinements.contains("key")) {
                return wrapInHandle(RC4_HANDLE_TYPE,
                        StreamCipher.keyWith(
                                ((BinaryValue) arguments.getFirst()).octetsFromHere()),
                        evaluator);
            }
            return UnsetValue.unset();
        };
    }

    private Value encipherThroughStreamInPlace(HandleValue held, BinaryValue data) {
        StreamCipher cipher = readKeyHeldBy(held, RC4_HANDLE_TYPE, StreamCipher.class)
                .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_HANDLE, held.typeName()));
        data.requireChangeable();
        for (int at = data.index(); at <= data.storageLength(); at++) {
            data.storage().set(at, data.storage().at(at)
                    ^ cipher.readNextKeystreamByte());
        }
        return data;
    }
}
