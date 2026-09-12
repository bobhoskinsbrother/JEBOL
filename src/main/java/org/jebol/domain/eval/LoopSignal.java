package org.jebol.domain.eval;

final class LoopSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final LoopSignal BREAK = new LoopSignal(null);

    private final transient org.jebol.domain.value.Value answer;

    private LoopSignal(org.jebol.domain.value.Value answer) {
        super("break", null, false, false);
        this.answer = answer;
    }

    static LoopSignal breaking() {
        return BREAK;
    }

    static LoopSignal breakingWith(org.jebol.domain.value.Value answer) {
        return new LoopSignal(answer);
    }

    org.jebol.domain.value.Value answer() {
        return answer == null
                ? org.jebol.domain.value.UnsetValue.unset()
                : answer;
    }
}
