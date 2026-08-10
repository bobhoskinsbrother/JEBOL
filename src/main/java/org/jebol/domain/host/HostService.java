package org.jebol.domain.host;

/**
 * A kind of thing outside the interpreter that a script can ask for.
 *
 * <p>A host grants a kind and not a function. A host that lets a script
 * read one file almost always lets it read every file it can see, and a
 * host that does not want the file system does not want one corner of it
 * either. A grant per function reads as finer control and gives none: the
 * host can say which verb and not which file, and the verb was never the
 * interesting half.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public enum HostService {

    /** Read, write, delete, rename, list, and ask about files. */
    FILES,

    /** Which directory a relative path counts from. */
    WORKING_DIRECTORY,

    /** The names and values the host was started with. */
    ENVIRONMENT,

    /** Start another program and wait for it. */
    PROCESSES,

    /** Read a line from the operator and write one back. */
    CONSOLE,

    /** What the time is now. */
    CLOCK,

    /** Open a connection to another machine. */
    NETWORK,

    /** Ask the operator with a window on a screen. */
    WINDOWS
}
