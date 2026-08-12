package org.jebol.domain.eval;

/**
 * Where a script reads a line from the operator.
 *
 * <p>A port the domain owns and an adapter fills, so the evaluator never
 * reads a stream itself. There is no default that answers: a host that has
 * not thought about whether a script may stop and wait has not decided
 * that it may.
 *
 * <p>Writing goes to {@link OutputPort} and not here. The two are separate
 * because a host almost always wants to see what a script printed and
 * almost never wants a script to stop and wait for a person.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public interface ConsolePort {

    /**
     * One line from the operator, without the line ending.
     *
     * <p>Null when there is nothing more to read, which a script must be
     * able to tell from an empty line.
     */
    String readLine();

    /**
     * One line read without showing it.
     *
     * <p>For a password. A host with no way to hide the typing must
     * refuse rather than read it in the open.
     */
    String readHiddenLine();

    /**
     * One keypress, as a codepoint, or -1 when none can be read.
     *
     * <p>What READ-KEY asks for: "Reads a single keypress from the console
     * without echoing it." A single keypress needs the terminal in raw mode,
     * and nothing in the JDK can put it there -- so how much of this an adapter
     * can honour depends on the adapter, and the domain asks the question
     * either way.
     *
     * <p>-1 rather than an exception, because the C answers none when the host
     * cannot read a key, and a script that offered a menu and got nothing back
     * should see nothing rather than a failure.
     */
    default int readKey() {
        return -1;
    }

    /**
     * Whether this console is a real terminal.
     *
     * <p>What TTY? answers. A script asks it to decide whether to prompt, so a
     * console that is really a pipe must say so: prompting into a pipe waits
     * for a person who is not there.
     *
     * <p>False by default, because a port that reads nothing is not a terminal
     * and the safe answer is the one that stops a script waiting.
     */
    default boolean isATerminal() {
        return false;
    }

    /** A port that reads nothing, which is what a script gets by default. */
    static ConsolePort none() {
        return new ConsolePort() {
            @Override
            public String readLine() {
                throw refuse();
            }

            @Override
            public String readHiddenLine() {
                throw refuse();
            }

            private FilePort.Denied refuse() {
                return new FilePort.Denied("no-port",
                        "this script was given no console to read");
            }
        };
    }
}
