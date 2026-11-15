package org.jebol.application;

import org.jebol.adapter.host.Java2DSurface;

import java.awt.Font;

public final class TheCarriedFace {

    private static final Font FACE = Java2DSurface.faceFrom(TheCarriedFont.font().readFontBytes());

    private TheCarriedFace() {
    }

    public static Font face() {
        return FACE;
    }
}
