package org.jebol.domain.eval.natives;

import org.jebol.domain.value.TypesetValue;

public class IsAnyTypeNative extends TypesetPredicateNative {

    @Override
    protected TypesetValue asked() {
        return TypesetValue.ANY_TYPE;
    }
}
