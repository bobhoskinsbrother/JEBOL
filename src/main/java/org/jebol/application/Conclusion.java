package org.jebol.application;

/**
 * Why a script stopped.
 *
 * <p>Every way out is one of these, including the ones a host would
 * otherwise have to catch a throwable to learn about. A host that must
 * distinguish "the script failed" from "JEBOL has a bug" cannot do it by
 * catching exceptions, because both would arrive the same way.
 */
public enum Conclusion {
    /** The script finished and its value is available. */
    PRODUCED_A_VALUE,
    /** The script failed. The error is available and the script could have caught it. */
    RAISED,
    /** The deadline passed before the script finished. */
    TIMED_OUT,
    /** The host asked for it to stop. */
    CANCELLED
}
