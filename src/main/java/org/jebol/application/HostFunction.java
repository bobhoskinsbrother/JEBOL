package org.jebol.application;

import java.util.List;

/**
 * Something the host lets a script call.
 *
 * <p>Arguments arrive already converted to host values where there is an
 * obvious counterpart, so an implementation deals in {@code Long} and
 * {@code String} rather than in REBOL values. What comes back is converted
 * the same way on the way in.
 */
@FunctionalInterface
public interface HostFunction {

    /**
     * Runs the function.
     *
     * @param arguments the arguments, converted to host values
     * @return the result, converted back on the way in. May be null, which
     *     crosses as a host null rather than as REBOL's none
     */
    Object call(List<Object> arguments);
}
