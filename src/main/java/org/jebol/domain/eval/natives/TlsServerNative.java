package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.crypto.RsaKey;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.host.RsaServerKey;
import org.jebol.domain.host.SecureSession;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class TlsServerNative extends CipherNative {

    @Override
    public String nativeName() {
        return "tls-server";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("certificates", Set.of(BlockValue.TYPE)),
                Parameter.of("key", Set.of(HandleValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<byte[]> chain = ((AnyBlockValue) arguments.getFirst()).remaining().stream()
                    .map(TlsServerNative::readCertificate)
                    .toList();
            RsaServerKey key = readKeyHeldBy(arguments.get(1), RSA_HANDLE_TYPE, RsaKey.class)
                    .flatMap(RsaKey::servingParts)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(1)));
            try {
                SecureSession session = evaluator.ports().secureTransport().startServer(chain, key);
                return TlsSessions.wrap(session, "a client", evaluator);
            } catch (NetworkPort.Refused refused) {
                throw new Raised(ErrorValue.of(ErrorCategory.ACCESS, refused.errorId(), refused.getMessage(),
                        StringValue.of(refused.subject())));
            }
        };
    }

    private static byte[] readCertificate(Value certificate) {
        if (!(certificate instanceof BinaryValue binary)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, certificate);
        }
        return binary.readBytesFromHere();
    }
}
