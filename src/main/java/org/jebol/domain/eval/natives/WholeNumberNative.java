package org.jebol.domain.eval.natives;

import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public abstract class WholeNumberNative extends ArithmeticNative {

    protected long readTheWholeNumberOf(Value given) {
        return ((IntegerValue) given).magnitude();
    }

    protected long findGreatestDivisorSharedBy(long first, long second) {
        long larger = Math.abs(first);
        long smaller = Math.abs(second);
        while (smaller != 0) {
            long carried = smaller;
            smaller = larger % smaller;
            larger = carried;
        }
        return larger;
    }
}
