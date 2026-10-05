package org.jebol.domain.eval.definition;

import org.jebol.domain.value.UnicodeCases;

public class UppercaseNative extends CaseChangeNative {

    @Override
    public String name() {
        return "uppercase";
    }

    @Override
    int changed(int codepoint) {
        return UnicodeCases.TABLES.upper(codepoint);
    }
}
