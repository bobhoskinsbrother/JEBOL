package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;

public class DecloakNative extends CloakNative {

    public DecloakNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "decloak";
    }

    @Override
    boolean decodes() {
        return true;
    }
}
