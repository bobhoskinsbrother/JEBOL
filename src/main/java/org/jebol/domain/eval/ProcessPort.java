package org.jebol.domain.eval;

import java.util.List;

/**
 * Starting another program.
 *
 * <p>A port the domain owns and an adapter fills, so the evaluator never
 * starts a process itself. There is no default that answers: a host that
 * has not thought about whether a script may start another program has not
 * decided that it may.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public interface ProcessPort {

    /** What a finished process left behind. */
    record Finished(int exitCode, String output, String errorOutput) { }

    /**
     * Starts a program and waits for it.
     *
     * @param command the program and its arguments, already separated
     * @param throughShell whether to hand the command to the host's shell
     *     instead of starting the program directly. A shell reads the
     *     command as text, thus anything the script put in it is read as
     *     part of the command
     */
    Finished runAndWait(List<String> command, boolean throughShell);

    /**
     * Starts a program and does not wait.
     *
     * @return the number the host gave the new process
     */
    long start(List<String> command, boolean throughShell);

    /** A port that starts nothing, which is what a script gets by default. */
    static ProcessPort none() {
        return new ProcessPort() {
            @Override
            public Finished runAndWait(List<String> command, boolean throughShell) {
                throw refuse();
            }

            @Override
            public long start(List<String> command, boolean throughShell) {
                throw refuse();
            }

            private FilePort.Denied refuse() {
                return new FilePort.Denied("no-port",
                        "this script was given no way to start a program");
            }
        };
    }
}
