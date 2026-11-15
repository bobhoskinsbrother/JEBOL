package org.jebol.adapter.cli;

import org.jebol.domain.host.OutputPort;

import java.io.PrintStream;

/** The output port, wired to a stream. */
public final class StreamOutput implements OutputPort {

    private final PrintStream ordinary;
    private final PrintStream errors;
    private volatile PrintStream current;

    public StreamOutput(PrintStream stream) {
        this(stream, stream);
    }

    public StreamOutput(PrintStream ordinary, PrintStream errors) {
        if (ordinary == null || errors == null) {
            throw new IllegalArgumentException("output needs a stream");
        }
        this.ordinary = ordinary;
        this.errors = errors;
        this.current = ordinary;
    }

    @Override
    public void write(String text) {
        current.print(text);
        current.flush();
    }

    @Override
    public void writeError(String text) {
        errors.print(text);
        errors.flush();
    }

    @Override
    public void flush() {
        current.flush();
    }

    void useErrorStream(boolean toErrors) {
        current.flush();
        current = toErrors ? errors : ordinary;
    }
}
