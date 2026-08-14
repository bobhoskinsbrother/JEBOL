package org.jebol.adapter.host;

import org.jebol.domain.eval.EnvironmentPort;

import java.util.Map;

/**
 * The environment of the Java process this interpreter runs in.
 *
 * <p>Reading only, because a JVM cannot change the environment of its own
 * process. Every interpreter in one process reads the same names, thus
 * this holds nothing of its own.
 */
public final class ProcessEnvironment implements EnvironmentPort {

    @Override
    public String valueOf(String name) {
        return System.getenv(name);
    }

    @Override
    public Map<String, String> all() {
        return Map.copyOf(System.getenv());
    }
}
