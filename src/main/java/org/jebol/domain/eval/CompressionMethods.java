package org.jebol.domain.eval;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The compressions extensions have registered in one interpreter, over the built-in ones of the same names. */
public final class CompressionMethods {

    private final Map<String, CompressionMethod> registered = new HashMap<>();

    public void register(String name, CompressionMethod method) {
        registered.put(name, method);
    }

    /** Puts the built-in method back under its own name, as registering the built-in's own functions would. */
    public void restoreBuiltIn(String name) {
        registered.remove(name);
    }

    public Optional<CompressionMethod> find(String name) {
        return Optional.ofNullable(registered.get(name));
    }

    public boolean isKnown(String name) {
        return Encodings.COMPRESSIONS.contains(name) || registered.containsKey(name);
    }
}
