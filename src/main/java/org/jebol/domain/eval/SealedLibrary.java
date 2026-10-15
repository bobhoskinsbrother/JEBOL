package org.jebol.domain.eval;

import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

public final class SealedLibrary {

    private final Context lib;

    private boolean sealed;

    public SealedLibrary(Context lib) {
        this.lib = lib;
    }

    public void seal() {
        sealed = true;
    }

    public boolean holds(String name) {
        return lib.hasItsOwnSlotFor(Context.canonicalise(name));
    }

    public void register(String name, Value value) {
        if (sealed && holds(name)) {
            throw Raised.of(EvaluationFailure.ALREADY_USED, WordValue.of(name));
        }
        lib.register(name, value);
    }
}
