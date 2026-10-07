package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.OutputPort;

public class PrintNative extends OutputNative {

    @Override
    public String name() {
        return "print";
    }

    @Override
    protected void write(OutputPort output, String text) {
        output.writeLine(text);
    }
}
