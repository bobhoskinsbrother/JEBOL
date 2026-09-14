package org.jebol.domain.host;


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
