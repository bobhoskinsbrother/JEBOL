package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.SecureSession;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class TlsExchangeNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "tls-exchange";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("session", Set.of(HandleValue.TYPE)),
                Parameter.of("step", Set.of(WordValue.TYPE)),
                Parameter.of("data", Set.of(BinaryValue.TYPE, StringValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            TlsSessions.Held held = TlsSessions.unwrap(arguments.getFirst());
            SecureSession session = held.session();
            Value data = arguments.get(2);
            SecureSession.Exchange exchange = switch (((AnyWordValue) arguments.get(1)).canonical()) {
                case "begin" -> session.begin();
                case "receive" -> session.receive(octetsOf(data));
                case "send" -> session.send(octetsOf(data));
                case "close" -> session.close();
                default -> throw Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(1));
            };
            return TlsSessions.describe(exchange, held.host());
        };
    }

    private static byte[] octetsOf(Value data) {
        return data instanceof NoneValue ? new byte[0] : data.asOctets();
    }
}
