package org.jebol.domain.eval;

import java.util.Map;

/**
 * The names and values the host was started with.
 *
 * <p>A port the domain owns and an adapter fills, so the evaluator never
 * reads the process environment itself. There is no default that answers,
 * because a host that has not thought about what a script may read has not
 * decided that it may read everything.
 *
 * <p>Reading only. A JVM cannot change the environment of its own process,
 * thus SET-ENV has no port method to call and refuses with the reason that
 * says no host can offer it.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public interface EnvironmentPort {

    /** What one name holds, or null when the host has no such name. */
    String valueOf(String name);

    /** Every name the host was started with, and what each one holds. */
    Map<String, String> all();

    /** A port that answers nothing, which is what a script gets by default. */
    static EnvironmentPort none() {
        return new EnvironmentPort() {
            @Override
            public String valueOf(String name) {
                throw refuse();
            }

            @Override
            public Map<String, String> all() {
                throw refuse();
            }

            private FilePort.Denied refuse() {
                return new FilePort.Denied("no-port",
                        "this script was given no environment to read");
            }
        };
    }
}
