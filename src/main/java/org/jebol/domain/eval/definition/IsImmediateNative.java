package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Typeset;

public class IsImmediateNative extends TypesetPredicateNative {

    @Override
    protected Typeset asked() {
        return Typeset.IMMEDIATE;
    }
}
