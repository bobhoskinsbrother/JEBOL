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
 * <p>Writing, in the one sense a JVM can. The process's own environment cannot
 * be changed -- {@code getenv} is a read-only view and there is no portable
 * {@code setenv} behind it -- but what this interpreter reports and what a
 * program it starts inherits both can be, and those are the two things a
 * script means by setting a variable. So SET-ENV lays a name over the host's
 * and GET-ENV, LIST-ENV and the process runner all read the result. Refusing
 * instead, on the ground that the process environment is fixed, answered a
 * question nobody asked.
 *
 * <p>Specified in {@code spec/embed.allium} and {@code spec/natives.allium}.
 */
public interface EnvironmentPort {

    /** What one name holds, or null when nothing here holds it. */
    String valueOf(String name);

    /** Every name this interpreter reports, and what each one holds. */
    Map<String, String> all();

    /**
     * Lays a value over a name, or takes the name away when given null.
     *
     * <p>Over rather than into: the host's own names stay where they are and a
     * script that puts back what it found restores what was there.
     */
    void nameHolds(String name, String value);

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

            @Override
            public void nameHolds(String name, String value) {
                throw refuse();
            }

            private FilePort.Denied refuse() {
                return new FilePort.Denied("no-port",
                        "this script was given no environment to read");
            }
        };
    }
}
