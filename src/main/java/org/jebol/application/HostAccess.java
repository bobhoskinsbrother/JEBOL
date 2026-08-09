package org.jebol.application;

/**
 * What a script is allowed to reach outside itself.
 *
 * <p>A policy the host sets, in the shape GraalVM's {@code HostAccess} uses,
 * rather than a paragraph of documentation asking the host to be careful.
 * A choice expressed in code can be enforced; advice cannot.
 *
 * <p>The default is {@link #NONE_AT_ALL}, because a host that has not thought
 * about what a script may reach has not decided that it may reach everything.
 */
public enum HostAccess {
    /** The script sees only REBOL. Host values handed in cannot be called. */
    NONE_AT_ALL,
    /** The script may read host values it was given, but may not call them. */
    READING,
    /** The script may also call functions the host defined for it. */
    READING_AND_CALLING;

    public boolean allowsReading() {
        return this != NONE_AT_ALL;
    }

    public boolean allowsCalling() {
        return this == READING_AND_CALLING;
    }
}
