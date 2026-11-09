package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.SecureSession;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Locale;

final class TlsSessions {

    static final String HANDLE_TYPE = "tls-session";

    record Held(SecureSession session, String host) {
    }

    private TlsSessions() {
    }

    static Value wrap(SecureSession session, String host, Evaluator evaluator) {
        return HandleValue.wrapContext(HANDLE_TYPE, evaluator.nextCipherIdentity(),
                JavaObjectValue.of(new Held(session, host)));
    }

    static Held unwrap(Value given) {
        if (given instanceof HandleValue handle
                && HANDLE_TYPE.equals(handle.typeName())
                && handle.payload() instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof Held held) {
            return held;
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, given);
    }

    static Value describe(SecureSession.Exchange exchange, String host) {
        Value reason = exchange.failure().<Value>map(failure -> switch (failure) {
            case SecureSession.Failure.Mistrusted(var mistrust) -> StringValue.of(mistrust.explainTo(host));
            case SecureSession.Failure.Refused(String why) -> StringValue.of(why);
        }).orElseGet(NoneValue::none);
        return BlockValue.of(List.of(
                BinaryValue.ofBytes(exchange.toNetwork()),
                BinaryValue.ofBytes(exchange.arrived()),
                WordValue.of(exchange.state().name().toLowerCase(Locale.ROOT)),
                reason));
    }
}
