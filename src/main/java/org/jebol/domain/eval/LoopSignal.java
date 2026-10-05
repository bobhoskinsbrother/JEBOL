package org.jebol.domain.eval;

import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

public final class LoopSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final LoopSignal BREAK = new LoopSignal(UnsetValue.unset());

    private final transient Value answer;

    private LoopSignal(Value answer) {
        super("break", null, false, false);
        this.answer = answer;
    }

    public static LoopSignal breaking() {
        return BREAK;
    }

    public static LoopSignal breakingWith(Value answer) {
        return new LoopSignal(answer);
    }

    public Value answer() {
        return answer;
    }
}
