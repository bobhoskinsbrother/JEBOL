package org.jebol.domain.eval.natives;

import org.jebol.domain.host.OutputPort;

public class PrintNative extends OutputNative {

    @Override
    public String nativeName() {
        return "print";
    }

    @Override
    protected void write(OutputPort output, String text) {
        output.writeLine(text);
    }
}
