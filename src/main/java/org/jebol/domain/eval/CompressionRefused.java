package org.jebol.domain.eval;

import java.io.Serial;

/** What a {@link CompressionMethod} throws where its C would answer FALSE, carrying the error number it would set. */
public final class CompressionRefused extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public CompressionRefused(int code) {
        super("compression refused with " + code, null, false, false);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
