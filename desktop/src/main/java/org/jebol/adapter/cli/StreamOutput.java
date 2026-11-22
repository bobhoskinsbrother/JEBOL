package org.jebol.adapter.cli;

import org.jebol.domain.host.OutputPort;

import java.io.PrintStream;
import java.util.Locale;

/** The output port, wired to a stream. */
public final class StreamOutput implements OutputPort {

    private static final String LINE_FEED = "\n";
    private static final String WINDOWS_LINE_ENDING = "\r\n";

    private final PrintStream ordinary;
    private final PrintStream errors;
    private final String lineEnding;
    private volatile PrintStream current;

    public StreamOutput(PrintStream stream) {
        this(stream, stream);
    }

    public StreamOutput(PrintStream ordinary, PrintStream errors) {
        this(ordinary, errors, LINE_FEED);
    }

    StreamOutput(PrintStream ordinary, PrintStream errors, String lineEnding) {
        if (ordinary == null || errors == null) {
            throw new IllegalArgumentException("output needs a stream");
        }
        this.ordinary = ordinary;
        this.errors = errors;
        this.lineEnding = lineEnding;
        this.current = ordinary;
    }

    static StreamOutput withSystemLineEndings(PrintStream ordinary, PrintStream errors) {
        return new StreamOutput(ordinary, errors, isWindows() ? WINDOWS_LINE_ENDING : LINE_FEED);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private String convertLineEndings(String text) {
        return lineEnding.equals(LINE_FEED) ? text : text.replace(LINE_FEED, lineEnding);
    }

    @Override
    public void write(String text) {
        current.print(convertLineEndings(text));
        current.flush();
    }

    @Override
    public void writeError(String text) {
        errors.print(convertLineEndings(text));
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
