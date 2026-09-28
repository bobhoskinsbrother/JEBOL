package org.jebol.domain.eval;

import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.sets.MembersKept;
import org.jebol.domain.value.sets.SetOperation;

public final class Combining {

    private Combining() {
    }

    public static Value bitwise(Value left, Value right, BitwiseOperation operation) {
        return left.bitwise(right, operation);
    }

    public static Value sets(Value first, Value second, SetOperation how) {
        return sets(first, second, how, false, 1);
    }

    public static Value sets(
            Value first, Value second, SetOperation how, boolean mindingCase, int stride) {

        return first.asASetWith(second, keeping(how, mindingCase, stride), mindingCase);
    }

    private static MembersKept keeping(
            SetOperation how, boolean mindingCase, int stride) {

        return new MembersKept(how, stride, mindingCase
                ? Comparison::identicallyEqual
                : Comparison::looselyEqual);
    }

}
