package org.jebol.adapter.cli;

import org.jebol.domain.eval.OutputPort;

import java.io.PrintStream;

/** The output port, wired to a stream. */
public final class StreamOutput implements OutputPort {

    private final PrintStream stream;

    public StreamOutput(PrintStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("output needs a stream");
        }
        this.stream = stream;
    }

    public static StreamOutput toStandardOut() {
        return new StreamOutput(System.out);
    }

    @Override
    public void write(String text) {
        stream.print(text);
        stream.flush();
    }

    @Override
    public void flush() {
        stream.flush();
    }
}
