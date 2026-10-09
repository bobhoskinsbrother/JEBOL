package org.jebol.domain.eval.natives;

import org.jebol.domain.value.TypesetValue;

public class IsCopyableNative extends TypesetPredicateNative {

    @Override
    protected TypesetValue asked() {
        return TypesetValue.COPYABLE;
    }
}
