package org.jebol.adapter.cli;

import java.io.PrintStream;
import org.jebol.domain.eval.OutputPort;

/**
 * The output port, wired to a stream.
 *
 * <p>This class is the only thing in JEBOL that knows a {@link PrintStream}
 * exists. The domain writes through the port and never learns where the text
 * went, which is what lets a test capture it without a file.
 */
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
}
