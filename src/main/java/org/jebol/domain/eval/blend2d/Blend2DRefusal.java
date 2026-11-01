package org.jebol.domain.eval.blend2d;

public final class Blend2DRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Blend2DRefusal(String what) {
        super("blend2d will not draw " + what + ": the extension's C reads such a vector as 64-bit decimals, past its end",
                null, false, false);
    }
}
