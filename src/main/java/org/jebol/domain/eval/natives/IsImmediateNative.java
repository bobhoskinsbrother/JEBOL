package org.jebol.domain.eval.natives;

import org.jebol.domain.value.TypesetValue;

public class IsImmediateNative extends TypesetPredicateNative {

    @Override
    protected TypesetValue asked() {
        return TypesetValue.IMMEDIATE;
    }
}
