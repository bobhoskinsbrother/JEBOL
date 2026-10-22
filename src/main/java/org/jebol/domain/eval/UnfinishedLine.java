package org.jebol.domain.eval;

import org.jebol.domain.host.OutputPort;

final class UnfinishedLine implements OutputPort {

    private final OutputPort output;
    private final StringBuilder sinceLineEnded = new StringBuilder();

    UnfinishedLine(OutputPort output) {
        this.output = output;
    }

    @Override
    public void write(String text) {
        output.write(text);
        int lineEnds = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
        if (lineEnds >= 0) {
            sinceLineEnded.setLength(0);
        }
        sinceLineEnded.append(text, lineEnds + 1, text.length());
    }

    @Override
    public void flush() {
        output.flush();
    }

    String read() {
        return sinceLineEnded.toString().strip();
    }
}
