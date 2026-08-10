package org.jebol.domain.host;

/**
 * Why a host service is not available to a script.
 *
 * <p>A script must be able to tell these apart. The first two are the
 * host's decision and can change between one run and the next. The third
 * never changes.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public enum ServiceRefusal {

    /** The host has this service and did not grant it. */
    NOT_GRANTED,

    /** The host itself has no such service to grant. */
    NOT_PRESENT,

    /**
     * Nothing can offer it, thus JEBOL never will.
     *
     * <p>Four of R3's natives exist to call code written in C:
     * LOAD-EXTENSION, DO-CALLBACK, DO-COMMANDS and ACCESS-OS. A JVM can
     * be made to do this and the result stops being portable, which is
     * the one thing JEBOL is for.
     */
    NEVER_PORTABLE
}
