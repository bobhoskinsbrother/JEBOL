package org.jebol.domain.eval.natives;

import org.jebol.domain.value.TypesetValue;

public class IsInternalNative extends TypesetPredicateNative {

    @Override
    protected TypesetValue asked() {
        return TypesetValue.INTERNAL;
    }
}
