package org.jebol.adapter.host;

import org.jebol.domain.host.EnvironmentPort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProcessEnvironment implements EnvironmentPort {

    private final Map<String, String> keyValues =
            new LinkedHashMap<>();

    @Override
    public String valueOf(String name) {
        return keyValues.containsKey(name) ? keyValues.get(name) : System.getenv(name);
    }

    @Override
    public Map<String, String> readAll() {
        Map<String, String> environmentVariables = new HashMap<>(System.getenv());
        keyValues.forEach((name, held) -> {
            if (held == null) {
                environmentVariables.remove(name);
            } else {
                environmentVariables.put(name, held);
            }
        });
        return Map.copyOf(environmentVariables);
    }

    @Override
    public void assign(String name, String value) {
        keyValues.put(name, value);
    }
}
