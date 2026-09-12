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

    /** Nothing can offer it, thus JEBOL never will. */
    NEVER_PORTABLE
}
