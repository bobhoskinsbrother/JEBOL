package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;

public class EncloakNative extends CloakNative {

    public EncloakNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "encloak";
    }

    @Override
    boolean decodes() {
        return false;
    }
}
