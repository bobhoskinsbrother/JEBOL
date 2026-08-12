package org.jebol.adapter.host;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    public ProgramResult run(ProgramToStart program) {
        ProcessBuilder builder = builderFor(program);
        Process child;
        try {
            child = builder.start();
        } catch (IOException refused) {
            return new ProgramResult(0, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(refused.getMessage()));
        }
        Thread feeding = Thread.startVirtualThread(
                () -> writeAnySuppliedBytesAndCloseStdin(program, child));
        if (!program.waitedFor()) {
            return new ProgramResult(child.pid(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
        return waitedFor(program, child, feeding);
    }

    private static ProgramResult waitedFor(
            ProgramToStart program, Process child, Thread feeding) {
        var errorsAside = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        Thread draining = Thread.startVirtualThread(() -> errorsAside.set(
                captured(program.standardError(), child.getErrorStream())
                        .orElse(null)));
        Optional<byte[]> output = captured(
                program.standardOutput(), child.getInputStream());
        try {
            int exitCode = child.waitFor();
            feeding.join();
            draining.join();
            return new ProgramResult(child.pid(), Optional.of(exitCode),
                    output, Optional.ofNullable(errorsAside.get()), Optional.empty());
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return new ProgramResult(child.pid(), Optional.empty(), output,
                    Optional.ofNullable(errorsAside.get()),
                    Optional.of("waiting for the program was stopped"));
        }
    }

    private static ProcessBuilder builderFor(ProgramToStart program) {
        ProcessBuilder builder = new ProcessBuilder(
                asShellCommand(program.command(), program.readByTheShell()));
        builder.redirectInput(inputOf(program));
        builder.redirectOutput(outputOf(
                program.standardOutput(), program.outputFile()));
        builder.redirectError(outputOf(
                program.standardError(), program.errorFile()));
        return builder;
    }

    private static ProcessBuilder.Redirect inputOf(ProgramToStart program) {
        return switch (program.standardInput()) {
            case THE_HOSTS_OWN -> ProcessBuilder.Redirect.INHERIT;
            case A_FILES_CONTENTS -> ProcessBuilder.Redirect.from(
                    new File(program.inputFile().orElseThrow()));
            case SUPPLIED_BYTES, NOTHING_AT_ALL -> ProcessBuilder.Redirect.PIPE;
        };
    }

    private static ProcessBuilder.Redirect outputOf(
            ProgramOutput route, Optional<String> file) {
        return switch (route) {
            case THE_HOSTS_OWN -> ProcessBuilder.Redirect.INHERIT;
            case INTO_A_FILE -> ProcessBuilder.Redirect.to(
                    new File(file.orElseThrow()));
            case DISCARDED -> ProcessBuilder.Redirect.DISCARD;
            case CAPTURED -> ProcessBuilder.Redirect.PIPE;
        };
    }

    private static void writeAnySuppliedBytesAndCloseStdin(
            ProgramToStart program, Process child) {
        if (program.standardInput() != ProgramInput.SUPPLIED_BYTES
                && program.standardInput() != ProgramInput.NOTHING_AT_ALL) {
            return;
        }
        try (var stdin = child.getOutputStream()) {
            if (program.inputBytes().isPresent()) {
                stdin.write(program.inputBytes().orElseThrow());
            }
        } catch (IOException childStoppedReadingWhichIsItsBusiness) {
        }
    }

    private static Optional<byte[]> captured(
            ProgramOutput route, java.io.InputStream from) {
        if (route != ProgramOutput.CAPTURED) {
            return Optional.empty();
        }
        try (from) {
            return Optional.of(from.readAllBytes());
        } catch (IOException unreadable) {
            return Optional.of(new byte[0]);
        }
    }

    private static List<String> asShellCommand(List<String> command, boolean throughShell) {
        if (!throughShell) {
            return command;
        }
        return runsOnWindows()
                ? theWholeLineForCmd(command)
                : theFirstEntryAsTheScriptForSh(command);
    }

    private static List<String> theFirstEntryAsTheScriptForSh(List<String> command) {
        List<String> withShell = new ArrayList<>(List.of("/bin/sh", "-c"));
        withShell.addAll(command);
        return withShell;
    }

    private static List<String> theWholeLineForCmd(List<String> command) {
        return List.of("cmd.exe", "/c", String.join(" ", command));
    }

    private static boolean runsOnWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
