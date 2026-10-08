package org.jebol.domain.eval.natives;

import org.jebol.domain.value.Typeset;

public class IsCopyableNative extends TypesetPredicateNative {

    @Override
    protected Typeset asked() {
        return Typeset.COPYABLE;
    }
}
