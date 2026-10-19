package org.jebol.domain.eval.natives;

import org.jebol.domain.value.UnicodeCases;

public class LowercaseNative extends CaseChangeNative {

    @Override
    public String nativeName() {
        return "lowercase";
    }

    @Override
    int change(int codepoint) {
        return UnicodeCases.TABLES.toLower(codepoint);
    }
}
