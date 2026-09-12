package org.jebol.adapter.host;

import org.jebol.domain.eval.ConsolePort;
import org.jebol.domain.eval.FilePort;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        Console terminal = System.console();
        if (terminal == null) {
            throw new FilePort.Denied("no-permission",
                    "this process has no terminal, thus typing cannot be hidden");
        }
        char[] typed = terminal.readPassword();
        return typed == null ? null : new String(typed);
    }

    @Override
    public boolean isATerminal() {
        Console consoleWhichIsAnsweredEvenWhenInputIsRedirected = System.console();
        return consoleWhichIsAnsweredEvenWhenInputIsRedirected != null
                && consoleWhichIsAnsweredEvenWhenInputIsRedirected.isTerminal();
    }

    @Override
    public int readKey() {
        try {
            return oneCharacterOnceATerminalInLineModeHasTakenAWholeLine();
        } catch (IOException unreadable) {
            return -1;
        }
    }

    private int oneCharacterOnceATerminalInLineModeHasTakenAWholeLine()
            throws IOException {

        return lines.read();
    }
}
