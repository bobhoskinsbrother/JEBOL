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

    /**
     * Whether standard input is a real terminal.
     *
     * <p>`System.console()` on its own is not the test on a modern JDK: it
     * answers a console even when the input is redirected. `isTerminal` is the
     * question TTY? is really asking, and it belongs here rather than in the
     * domain -- a `java.io.Console` is exactly what the dependency rule keeps
     * out of it.
     */
    @Override
    public boolean isATerminal() {
        java.io.Console console = System.console();
        return console != null && console.isTerminal();
    }

    /**
     * One character from standard input.
     *
     * <p>Honest about a limit rather than pretending past it: a terminal in its
     * ordinary mode buffers by line, so this returns once a line has been
     * entered rather than the instant a key goes down. Putting the terminal
     * into raw mode needs something the JDK does not offer -- there is no
     * portable call for it -- and doing it by running `stty` would be a process
     * call and POSIX-only.
     *
     * <p>The seam is the point: when a raw-mode adapter exists, it implements
     * this and the domain does not change. See decision 18 on why no library is
     * fetched to close the gap.
     */
    @Override
    public int readKey() {
        try {
            return lines.read();
        } catch (java.io.IOException unreadable) {
            return -1;
        }
    }
}
