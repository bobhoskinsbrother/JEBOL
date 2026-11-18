package org.jebol.domain.image;

import org.jebol.domain.host.ImagePort.Pixels;

public final class QuantisedGif {

    public static final String MD5 = "6CFEDB53097DFE9840965A20C9B6408F";

    private static final int WIDE = 64;

    private static final int HIGH = 48;

    private QuantisedGif() {
    }

    public static Pixels gradient() {
        byte[] rgba = new byte[WIDE * HIGH * 4];
        for (int down = 0; down < HIGH; down++) {
            for (int across = 0; across < WIDE; across++) {
                int at = (down * WIDE + across) * 4;
                rgba[at] = (byte) (across * 255 / (WIDE - 1));
                rgba[at + 1] = (byte) (down * 255 / (HIGH - 1));
                rgba[at + 2] = (byte) ((across * 7 + down * 13) & 0xFF);
                rgba[at + 3] = (byte) 0xFF;
            }
        }
        return new Pixels(WIDE, HIGH, rgba);
    }
}
