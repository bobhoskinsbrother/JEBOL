package org.jebol.domain.eval.mathpresso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MathPressoScope {

    enum Type { GLOBAL, SHADOW, NESTED }

    private final MathPressoScope parent;
    private final Type type;
    private final Map<String, MathPressoSymbol> symbols = new LinkedHashMap<>();

    MathPressoScope(MathPressoScope parent, Type type) {
        this.parent = parent;
        this.type = type;
    }

    boolean isGlobal() {
        return type == Type.GLOBAL;
    }

    boolean has(String name) {
        return symbols.containsKey(name);
    }

    void put(MathPressoSymbol symbol) {
        symbols.put(symbol.name(), symbol);
    }

    List<MathPressoSymbol> symbols() {
        return new ArrayList<>(symbols.values());
    }

    Optional<Resolved> resolve(String name) {
        for (MathPressoScope scope = this; scope != null; scope = scope.parent) {
            MathPressoSymbol found = scope.symbols.get(name);
            if (found != null) {
                return Optional.of(new Resolved(found, scope));
            }
        }
        return Optional.empty();
    }

    record Resolved(MathPressoSymbol symbol, MathPressoScope scope) {
    }
}
