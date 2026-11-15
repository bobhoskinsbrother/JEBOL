package org.jebol.adapter.host;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.SystemCursor;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.dnd.DragSource;
import java.awt.image.BufferedImage;
import java.util.Optional;

final class AwtCursors {

    private static final Point TOP_LEFT = new Point(0, 0);

    private AwtCursors() {
    }

    static Cursor cursorFor(ScreenCursor wanted) {
        return switch (wanted) {
            case ScreenCursor.Hidden ignored -> pictureCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            case ScreenCursor.FromSystem(SystemCursor system) -> predefinedFor(system);
            case ScreenCursor.Picture(ImagePort.Pixels pixels) -> pictureCursor(convertToImage(pixels));
        };
    }

    static Cursor predefinedFor(SystemCursor system) {
        return switch (system) {
            case ARROW, HELP -> Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
            case I_SHAPE -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
            case WAIT, APP_START -> Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
            case CROSS -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            case UP_ARROW, SIZE_NS -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case SIZE_NWSE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case SIZE_NESW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case SIZE_WE -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case SIZE_ALL -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            case HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            case NO -> Optional.ofNullable(DragSource.DefaultMoveNoDrop)
                    .orElseGet(Cursor::getDefaultCursor);
        };
    }

    private static Cursor pictureCursor(BufferedImage picture) {
        return Toolkit.getDefaultToolkit().createCustomCursor(picture, TOP_LEFT, "rebol");
    }

    private static BufferedImage convertToImage(ImagePort.Pixels pixels) {
        BufferedImage image = new BufferedImage(pixels.wide(), pixels.high(), BufferedImage.TYPE_INT_ARGB);
        byte[] rgba = pixels.rgba();
        for (int at = 0; at < pixels.wide() * pixels.high(); at++) {
            int octet = at * 4;
            image.setRGB(at % pixels.wide(), at / pixels.wide(),
                    (rgba[octet + 3] & 0xFF) << 24 | (rgba[octet] & 0xFF) << 16
                            | (rgba[octet + 1] & 0xFF) << 8 | rgba[octet + 2] & 0xFF);
        }
        return image;
    }
}
