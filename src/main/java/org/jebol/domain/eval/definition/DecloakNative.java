package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;

public class DecloakNative extends CloakNative {

    public DecloakNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String name() {
        return "decloak";
    }

    @Override
    boolean decodes() {
        return true;
    }
}
