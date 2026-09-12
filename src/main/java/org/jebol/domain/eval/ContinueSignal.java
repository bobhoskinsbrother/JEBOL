package org.jebol.domain.eval;

final class ContinueSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final ContinueSignal INSTANCE = new ContinueSignal();

    private ContinueSignal() {
        super("continue", null, false, false);
    }

    static ContinueSignal instance() {
        return INSTANCE;
    }
}
