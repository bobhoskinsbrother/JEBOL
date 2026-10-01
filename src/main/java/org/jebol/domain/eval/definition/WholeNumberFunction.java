package org.jebol.domain.eval.definition;

import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public abstract class WholeNumberFunction extends DefaultFunction {

    protected long wholeNumberOf(Value given) {
        return ((IntegerValue) given).magnitude();
    }

    protected long theGreatestDivisorSharedBy(long first, long second) {
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
