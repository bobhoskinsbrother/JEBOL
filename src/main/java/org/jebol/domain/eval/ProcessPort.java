package org.jebol.domain.eval;

import java.util.List;
import java.util.Optional;

/**
 * Starting another program.
 *
 * <p>A port the domain owns and an adapter fills, so the evaluator never
 * starts a process itself. There is no default that answers: a host that
 * has not thought about whether a script may start another program has not
 * decided that it may.
 *
 * <p>Specified in {@code spec/embed.allium} as ProgramToStart and
 * ProgramResult.
 */
public interface ProcessPort {

    enum ProgramInput { THE_HOSTS_OWN, SUPPLIED_BYTES, A_FILES_CONTENTS, NOTHING_AT_ALL }

    enum ProgramOutput { THE_HOSTS_OWN, CAPTURED, INTO_A_FILE, DISCARDED }

    /**
     * @param environment what the child's environment is to be, rather than
     *     what to add to it. The interpreter's own view already has whatever
     *     SET-ENV laid over the host's, and the child inherits that view --
     *     which is the half of SET-ENV a program other than this one can
     *     observe, and the only half a JVM can offer at all.
     */
    record ProgramToStart(
            List<String> command,
            boolean readByTheShell,
            boolean attachedToTheHostsConsole,
            boolean waitedFor,
            ProgramInput standardInput,
            Optional<byte[]> inputBytes,
            Optional<String> inputFile,
            ProgramOutput standardOutput,
            Optional<String> outputFile,
            ProgramOutput standardError,
            Optional<String> errorFile,
            java.util.Map<String, String> environment) {

        public ProgramToStart {
            command = List.copyOf(command);
            environment = java.util.Map.copyOf(environment);
        }
    }

    record ProgramResult(
            long processNumber,
            Optional<Integer> exitCode,
            Optional<byte[]> capturedOutput,
            Optional<byte[]> capturedError,
            Optional<String> refusalMessage) { }

    ProgramResult run(ProgramToStart program);

    static ProcessPort none() {
        return program -> {
            throw new FilePort.Denied("no-port",
                    "this script was given no way to start a program");
        };
    }
}
