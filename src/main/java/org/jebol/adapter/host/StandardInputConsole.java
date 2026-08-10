package org.jebol.adapter.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.jebol.domain.eval.ConsolePort;
import org.jebol.domain.eval.FilePort;

/**
 * A console that reads the standard input of the Java process.
 *
 * <p>Hidden reading works only when the process has a real terminal. A JVM
 * started without one cannot stop the typing being shown, thus this
 * refuses rather than reading a password in the open.
 */
public final class StandardInputConsole implements ConsolePort {

    private final BufferedReader lines = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Override
    public String readLine() {
        try {
            return lines.readLine();
        } catch (IOException unreadable) {
            throw new FilePort.Denied("cannot-open", "the console cannot be read");
        }
    }

    @Override
    public String readHiddenLine() {
        java.io.Console terminal = System.console();
        if (terminal == null) {
            throw new FilePort.Denied("no-permission",
                    "this process has no terminal, thus typing cannot be hidden");
        }
        char[] typed = terminal.readPassword();
        return typed == null ? null : new String(typed);
    }
}
