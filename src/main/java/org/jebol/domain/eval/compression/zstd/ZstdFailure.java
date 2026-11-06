package org.jebol.domain.eval.compression.zstd;

import java.io.Serial;

public final class ZstdFailure extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ZstdError error;

    ZstdFailure(ZstdError error) {
        super(error.name(), null, false, false);
        this.error = error;
    }

    public ZstdError error() {
        return error;
    }
}
