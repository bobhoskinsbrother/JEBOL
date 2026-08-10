package org.jebol.adapter.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.ProcessPort;

/**
 * Starts a program with the JDK, and nothing else.
 *
 * <p>The shell is chosen by what the host runs on, because there is no one
 * shell every machine has. A command handed to a shell is read as text,
 * thus anything a script put in it becomes part of the command. A host
 * that does not want that must not grant this service.
 */
public final class JavaProcesses implements ProcessPort {

    @Override
    public Finished runAndWait(List<String> command, boolean throughShell) {
        Process started = start(asShellCommand(command, throughShell));
        try {
            String output = read(started.getInputStream());
            String errors = read(started.getErrorStream());
            return new Finished(started.waitFor(), output, errors);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new FilePort.Denied("cannot-open", "waiting for the program was stopped");
        }
    }

    @Override
    public long start(List<String> command, boolean throughShell) {
        return start(asShellCommand(command, throughShell)).pid();
    }

    private static Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException refused) {
            throw new FilePort.Denied("cannot-open",
                    "the program " + command.getFirst() + " could not be started");
        }
    }

    /** The command as it must be handed over, with a shell in front when asked. */
    private static List<String> asShellCommand(List<String> command, boolean throughShell) {
        if (!throughShell) {
            return command;
        }
        List<String> withShell = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows) {
            withShell.add("cmd.exe");
            withShell.add("/c");
        } else {
            withShell.add("/bin/sh");
            withShell.add("-c");
        }
        withShell.add(String.join(" ", command));
        return withShell;
    }

    private static String read(java.io.InputStream from) {
        try (from) {
            return new String(from.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }
}
