package org.jebol.domain.eval.blend2d;

public final class Blend2DNotPortedYet extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Blend2DNotPortedYet(String what) {
        super("blend2d cannot yet draw " + what + " in JEBOL: its rasteriser is not ported", null, false, false);
    }
}
