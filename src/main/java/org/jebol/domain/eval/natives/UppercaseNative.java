package org.jebol.domain.eval.natives;

import org.jebol.domain.value.UnicodeCases;

public class UppercaseNative extends CaseChangeNative {

    @Override
    public String nativeName() {
        return "uppercase";
    }

    @Override
    int changed(int codepoint) {
        return UnicodeCases.TABLES.upper(codepoint);
    }
}
