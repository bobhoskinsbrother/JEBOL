package org.jebol.domain.eval.actions;

import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.Xor;

public class BitwiseXorAction extends BitwiseAction {

    @Override
    public String nativeName() {
        return "xor~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new Xor();
    }
}
