package org.jebol.domain.host;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Map.*;

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

    enum ProgramInput { HOSTS_OWN, SUPPLIED_BYTES, FILE_CONTENTS, NOTHING_AT_ALL }

    enum ProgramOutput { HOSTS_OWN, CAPTURED, INTO_FILE, DISCARDED }

    /**
     * @param environment what the child's environment is to be, rather than
     *     what to add to it. The interpreter's own view already has whatever
     *     SET-ENV laid over the host's, and the child inherits that view --
     *     which is the half of SET-ENV a program other than this one can
     *     observe, and the only half a JVM can offer at all.
     * @param workingDirectory where on the machine the child starts, which is
     *     where the script is standing rather than where the embedding
     *     application was launched from. A script that does {@code cd sub} and
     *     then calls a program means the two to agree, and a script confined
     *     to a directory of its own would otherwise write outside it through
     *     the one door confinement cannot close. Empty where no filesystem was
     *     granted, and the child then starts wherever the host process is.
     */
    record Program(
            List<String> command,
            boolean isReadByShell,
            boolean attachedToHostsConsole,
            boolean isWaitedFor,
            ProgramInput standardInput,
            Optional<byte[]> inputBytes,
            Optional<String> inputFile,
            ProgramOutput standardOutput,
            Optional<String> outputFile,
            ProgramOutput standardError,
            Optional<String> errorFile,
            Map<String, String> environment,
            Optional<String> workingDirectory) {

        public Program {
            command = List.copyOf(command);
            environment = copyOf(environment);
        }
    }

    record ProgramResult(
            long processNumber,
            Optional<Integer> exitCode,
            Optional<byte[]> capturedOutput,
            Optional<byte[]> capturedError,
            Optional<String> refusalMessage) { }

    ProgramResult run(Program program);

    enum WhoProcessRunsAs { REAL_USER, EFFECTIVE_USER, REAL_GROUP, EFFECTIVE_GROUP }

    default OptionalLong identify(WhoProcessRunsAs asked) {
        return OptionalLong.empty();
    }

    /** Whether a process is running under this number. */
    default boolean isRunning(long processNumber) {
        throw refuse();
    }

    /** Sends the signal to the process, and answers whether the host could. */
    default boolean end(long processNumber, int signal) {
        throw refuse();
    }

    private FilePort.Denied refuse() {
        return new FilePort.Denied("no-port",
                "this script was given no way to start a program");
    }

    static ProcessPort none() {
        return program -> {
            throw new FilePort.Denied("no-port",
                    "this script was given no way to start a program");
        };
    }
}
