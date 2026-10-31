package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.render.DrawingCommand;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.List;
import java.util.Optional;

final class BrowserPainting {

    private BrowserPainting() {
    }

    static Optional<ImagePort.Pixels> paintOnWhite(List<DrawingCommand> painting, int wide, int high) {
        if (wide == 0 || high == 0) {
            return Optional.of(new ImagePort.Pixels(wide, high, new byte[0]));
        }
        Painted painted = paintInWorker(JsonConverter.write(painting, wide, high)).await();
        if (painted.getFailed() != null) {
            return Optional.empty();
        }
        Int8Array rgba = painted.getRgba();
        byte[] bytes = new byte[rgba.getLength()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = rgba.get(at);
        }
        return Optional.of(new ImagePort.Pixels(wide, high, bytes));
    }

    interface Painted extends JSObject {

        @JSProperty
        String getFailed();

        @JSProperty
        Int8Array getRgba();
    }

    @JSBody(params = "picture", script = "return jebolPaintOffscreen(picture);")
    private static native JSPromise<Painted> paintInWorker(String picture);
}
