package org.jebol.domain.eval.natives;

import org.jebol.domain.value.Typeset;

public class IsAnyTypeNative extends TypesetPredicateNative {

    @Override
    protected Typeset asked() {
        return Typeset.ANY_TYPE;
    }
}
