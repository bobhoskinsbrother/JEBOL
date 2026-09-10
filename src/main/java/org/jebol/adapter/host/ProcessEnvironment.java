package org.jebol.adapter.host;

import org.jebol.domain.eval.EnvironmentPort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The environment of the Java process this interpreter runs in, with whatever
 * the script has laid over it.
 *
 * <p>The process's own names cannot be changed: {@code System.getenv} is a
 * read-only view and there is no portable {@code setenv} behind it. What can
 * be changed is what this interpreter reports and what a program it starts
 * inherits, and those are the two things a script means by setting a
 * variable, so SET-ENV writes here and every reader looks through the overlay
 * first.
 *
 * <p>The overlay belongs to this port rather than to the process, so two
 * interpreters in one JVM read the same host names and do not see each
 * other's changes. That is the arrangement a script can reason about: the
 * alternative, one static map, would let a test leak a password into the next
 * one.
 *
 * <p>A name removed is remembered as removed rather than dropped, because the
 * host may hold one of its own underneath and forgetting would let it show
 * through again.
 */
public final class ProcessEnvironment implements EnvironmentPort {

    /** What the script has set, and the names it has taken away, as nulls. */
    private final Map<String, String> laidOver = new LinkedHashMap<>();

    @Override
    public String valueOf(String name) {
        return laidOver.containsKey(name) ? laidOver.get(name) : System.getenv(name);
    }

    @Override
    public Map<String, String> all() {
        Map<String, String> everything = new HashMap<>(System.getenv());
        laidOver.forEach((name, held) -> {
            if (held == null) {
                everything.remove(name);
            } else {
                everything.put(name, held);
            }
        });
        return Map.copyOf(everything);
    }

    @Override
    public void nameHolds(String name, String value) {
        laidOver.put(name, value);
    }
}
