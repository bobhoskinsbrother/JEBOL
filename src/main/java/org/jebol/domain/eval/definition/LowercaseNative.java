package org.jebol.domain.eval.definition;

import org.jebol.domain.value.UnicodeCases;

public class LowercaseNative extends CaseChangeNative {

    @Override
    public String nativeName() {
        return "lowercase";
    }

    @Override
    int changed(int codepoint) {
        return UnicodeCases.TABLES.lower(codepoint);
    }
}
