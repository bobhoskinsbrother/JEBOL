package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.OutputPort;

public class PrinNative extends OutputNative {

    @Override
    public String nativeName() {
        return "prin";
    }

    @Override
    protected void write(OutputPort output, String text) {
        output.write(text);
    }
}
