package org.jebol.domain.parse;

public final class AlternativeSkipped extends RuntimeException {

    private static final long serialVersionUID = 1L;

    AlternativeSkipped() {
        super(null, null, false, false);
    }
}
