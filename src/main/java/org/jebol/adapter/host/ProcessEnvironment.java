package org.jebol.adapter.host;

import org.jebol.domain.eval.EnvironmentPort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The environment of the Java process this interpreter runs in, with whatever
 * the script has laid over it.
 */
public final class ProcessEnvironment implements EnvironmentPort {

    private static final String TAKEN_AWAY = null;

    private final Map<String, String> whatTheScriptLaidOverTheProcess =
            new LinkedHashMap<>();

    @Override
    public String valueOf(String name) {
        return whatTheScriptLaidOverTheProcess.containsKey(name)
                ? whatTheScriptLaidOverTheProcess.get(name)
                : System.getenv(name);
    }

    @Override
    public Map<String, String> all() {
        Map<String, String> everything = new HashMap<>(System.getenv());
        whatTheScriptLaidOverTheProcess.forEach((name, held) -> {
            if (held == TAKEN_AWAY) {
                everything.remove(name);
            } else {
                everything.put(name, held);
            }
        });
        return Map.copyOf(everything);
    }

    @Override
    public void nameHolds(String name, String value) {
        whatTheScriptLaidOverTheProcess.put(name, value);
    }
}
