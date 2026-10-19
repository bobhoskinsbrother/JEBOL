package org.jebol.adapter.host;

import org.jebol.application.JavaMachine;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaProcess implements ProcessPort {

    private static final Pattern THE_WHOLE_NUMBER_ID_PRINTS = Pattern.compile("\\d+");

    private static final Pattern SYSTEM_EXCEPTION_MATCHER =
            Pattern.compile("(?:\\(([^()]*)\\)|error=\\d+, (.+?))\\s*$");

    private static final int TERMINATE = 15;

    private final MachinePort machine = new JavaMachine();

    @Override
    public boolean isRunning(long processNumber) {
        return ProcessHandle.of(processNumber).isPresent();
    }

    @Override
    public boolean end(long processNumber, int signal) {
        return ProcessHandle.of(processNumber)
                .map(running -> signal == TERMINATE ? running.destroy() : running.destroyForcibly())
                .orElse(false);
    }

    @Override
    public ProgramResult run(Program program) {
        ProcessBuilder builder = buildProcess(program);
        Process child;
        try {
            child = builder.start();
        } catch (IOException refused) {
            return new ProgramResult(0, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(parseException(refused)));
        }
        Thread feeding = Thread.startVirtualThread(
                () -> writeBytesAndCloseStdin(program, child));
        if (!program.isWaitedFor()) {
            return new ProgramResult(child.pid(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
        return waitFor(program, child, feeding);
    }

    private String parseException(IOException exception) {
        String exceptionString = Optional.ofNullable(exception.getMessage()).orElse("");
        Matcher matcher = SYSTEM_EXCEPTION_MATCHER.matcher(exceptionString);
        if (!matcher.find()) {
            return exceptionString;
        }
        return Optional.ofNullable(matcher.group(1)).orElseGet(() -> matcher.group(2));
    }

    private ProgramResult waitFor(Program program, Process child, Thread feeding) {
        AtomicReference<byte[]> errors = new AtomicReference<>();
        Thread draining = Thread.startVirtualThread(() -> errors.set(
                capture(child.getErrorStream(), program.standardError())
                        .orElse(null)));
        Optional<byte[]> output = capture(
                child.getInputStream(), program.standardOutput());
        try {
            int exitCode = child.waitFor();
            feeding.join();
            draining.join();
            return new ProgramResult(child.pid(), Optional.of(exitCode),
                    output, Optional.ofNullable(errors.get()), Optional.empty());
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return new ProgramResult(child.pid(), Optional.empty(), output,
                    Optional.ofNullable(errors.get()),
                    Optional.of("waiting for the program was stopped"));
        }
    }

    private ProcessBuilder buildProcess(Program program) {
        ProcessBuilder builder = new ProcessBuilder(asShellCommand(program.command(), program.isReadByShell()));
        builder.redirectInput(chooseInput(program));
        builder.redirectOutput(chooseOutput(
                program.standardOutput(), program.outputFile()));
        builder.redirectError(chooseOutput(
                program.standardError(), program.errorFile()));
        putAllProgramEnvironment(builder, program);
        startInWorkingDirectory(builder, program);
        return builder;
    }

    private void startInWorkingDirectory(ProcessBuilder builder, Program program) {
        program.workingDirectory()
                .map(File::new)
                .filter(File::isDirectory)
                .ifPresent(builder::directory);
    }

    private void putAllProgramEnvironment(ProcessBuilder builder, Program program) {
        if (program.environment().isEmpty()) {
            return;
        }
        builder.environment().clear();
        builder.environment().putAll(program.environment());
    }

    private ProcessBuilder.Redirect chooseInput(Program program) {
        return switch (program.standardInput()) {
            case THE_HOSTS_OWN -> ProcessBuilder.Redirect.INHERIT;
            case FILE_CONTENTS -> ProcessBuilder.Redirect.from(
                    new File(program.inputFile().orElseThrow()));
            case SUPPLIED_BYTES, NOTHING_AT_ALL -> ProcessBuilder.Redirect.PIPE;
        };
    }

    private ProcessBuilder.Redirect chooseOutput(ProgramOutput route, Optional<String> file) {
        return switch (route) {
            case THE_HOSTS_OWN -> ProcessBuilder.Redirect.INHERIT;
            case INTO_A_FILE -> ProcessBuilder.Redirect.to(
                    new File(file.orElseThrow()));
            case DISCARDED -> ProcessBuilder.Redirect.DISCARD;
            case CAPTURED -> ProcessBuilder.Redirect.PIPE;
        };
    }

    private void writeBytesAndCloseStdin(Program program, Process child) {
        if (program.standardInput() != ProgramInput.SUPPLIED_BYTES
                && program.standardInput() != ProgramInput.NOTHING_AT_ALL) {
            return;
        }
        try (OutputStream stdin = child.getOutputStream()) {
            if (program.inputBytes().isPresent()) {
                stdin.write(program.inputBytes().orElseThrow());
            }
        } catch (IOException ignored) {
        }
    }

    private Optional<byte[]> capture(InputStream input, ProgramOutput output) {
        if (output != ProgramOutput.CAPTURED) {
            return Optional.empty();
        }
        try (input) {
            return Optional.of(input.readAllBytes());
        } catch (IOException unreadable) {
            return Optional.of(new byte[0]);
        }
    }

    private List<String> asShellCommand(List<String> command, boolean throughShell) {
        if (!throughShell) {
            return command;
        }
        return isWindows()
                ? wrapForCmd(command)
                : wrapForSh(command);
    }

    private boolean isWindows() {
        return machine.operatingSystem().equals("Windows");
    }

    private List<String> wrapForCmd(List<String> command) {
        return List.of("cmd.exe", "/c", String.join(" ", command));
    }

    private List<String> wrapForSh(List<String> command) {
        List<String> withShell = new ArrayList<>(List.of("/bin/sh", "-c"));
        withShell.addAll(command);
        return withShell;
    }

    @Override
    public OptionalLong identify(WhoTheProcessRunsAs asked) {
        List<String> question = switch (asked) {
            case REAL_USER -> List.of("id", "-ru");
            case EFFECTIVE_USER -> List.of("id", "-u");
            case REAL_GROUP -> List.of("id", "-rg");
            case EFFECTIVE_GROUP -> List.of("id", "-g");
        };
        try {
            return askTheProcessId(new ProcessBuilder(question)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start());
        } catch (IOException noIdOnThisMachine) {
            return OptionalLong.empty();
        }
    }

    private OptionalLong askTheProcessId(Process asking) throws IOException {
        String answered = new String(asking.getInputStream().readAllBytes(),
                StandardCharsets.US_ASCII).strip();
        if (!waitForCleanExit(asking) || !THE_WHOLE_NUMBER_ID_PRINTS.matcher(answered).matches()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(answered));
    }

    private boolean waitForCleanExit(Process process) {
        try {
            return process.waitFor() == 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
