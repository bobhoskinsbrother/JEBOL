package org.jebol.domain.eval.natives;

import org.jebol.domain.host.OutputPort;

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
