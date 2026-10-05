package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.Value;

import java.util.Optional;

public abstract class CipherNative extends DefaultNative {

    public static final String RC4_HANDLE_TYPE = "rc4";
    public static final String RSA_HANDLE_TYPE = "rsa";
    public static final String DHM_HANDLE_TYPE = "dhm";
    public static final String ECDH_HANDLE_TYPE = "ecdh";

    protected Value aHandleHolding(String type, Object key, Evaluator evaluator) {
        return HandleValue.context(type, evaluator.nextCipherIdentity(), JavaObjectValue.of(key));
    }

    protected <K> Optional<K> keyHeldBy(Value given, String type, Class<K> kind) {
        if (!(given instanceof HandleValue held)
                || !type.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue carried)) {
            return Optional.empty();
        }
        return carried.held().filter(kind::isInstance).map(kind::cast);
    }
}
