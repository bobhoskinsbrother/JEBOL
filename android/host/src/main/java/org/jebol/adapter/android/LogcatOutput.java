package org.jebol.adapter.android;

import android.util.Log;

import org.jebol.domain.host.OutputPort;

public final class LogcatOutput implements OutputPort {

    public static final String TAG = "jebol";

    private final LogLines lines;
    private final StringBuilder heldOutput = new StringBuilder();
    private final StringBuilder heldErrors = new StringBuilder();

    public LogcatOutput(LogLines lines) {
        this.lines = lines;
    }

    public static LogcatOutput toLogcat() {
        return new LogcatOutput(new LogLines() {
            @Override
            public void info(String line) {
                Log.i(TAG, line);
            }

            @Override
            public void error(String line) {
                Log.e(TAG, line);
            }
        });
    }

    @Override
    public synchronized void write(String text) {
        logFinishedLines(text, heldOutput, false);
    }

    @Override
    public synchronized void writeError(String text) {
        logFinishedLines(text, heldErrors, true);
    }

    @Override
    public synchronized void flush() {
        logHeld(heldOutput, false);
        logHeld(heldErrors, true);
    }

    private void logFinishedLines(String text, StringBuilder held, boolean isError) {
        int from = 0;
        for (int end = text.indexOf('\n'); end >= 0; end = text.indexOf('\n', from)) {
            held.append(text, from, end);
            if (!held.isEmpty() && held.charAt(held.length() - 1) == '\r') {
                held.setLength(held.length() - 1);
            }
            log(held.toString(), isError);
            held.setLength(0);
            from = end + 1;
        }
        held.append(text, from, text.length());
    }

    private void logHeld(StringBuilder held, boolean isError) {
        if (!held.isEmpty()) {
            log(held.toString(), isError);
            held.setLength(0);
        }
    }

    private void log(String line, boolean isError) {
        if (isError) {
            lines.error(line);
        } else {
            lines.info(line);
        }
    }

    public interface LogLines {

        void info(String line);

        void error(String line);
    }
}
