package org.jebol.domain.parse;

import org.jebol.domain.value.Value;

/** RETURN's answer, thrown past the walk to whoever asked for the parse. */
public final class Returned extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value answer;

    public Returned(Value answer) {
        super(null, null, false, false);
        this.answer = answer;
    }

    public Value answer() {
        return answer;
    }
}
