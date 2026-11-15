package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.Optional;

public final class BrowserImages implements BrowserPictures {

    @Override
    public Optional<ImagePort.Pixels> decode(byte[] encoded) {
        Decoded decoded = decodeInBrowser(toTypedArray(encoded)).await();
        if (decoded.getFailed() != null) {
            return Optional.empty();
        }
        return Optional.of(new ImagePort.Pixels(decoded.getWide(), decoded.getHigh(), toBytes(decoded.getRgba())));
    }

    @Override
    public Optional<byte[]> encode(ImagePort.Pixels image, String mediaType) {
        Encoded encoded = encodeInBrowser(toTypedArray(image.rgba()), image.wide(), image.high(), mediaType).await();
        if (encoded.getFailed() != null) {
            return Optional.empty();
        }
        return Optional.of(toBytes(encoded.getOctets()));
    }

    private Int8Array toTypedArray(byte[] octets) {
        Int8Array array = new Int8Array(octets.length);
        for (int at = 0; at < octets.length; at++) {
            array.set(at, octets[at]);
        }
        return array;
    }

    private byte[] toBytes(Int8Array octets) {
        byte[] bytes = new byte[octets.getLength()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = octets.get(at);
        }
        return bytes;
    }

    interface Decoded extends JSObject {

        @JSProperty
        String getFailed();

        @JSProperty
        int getWide();

        @JSProperty
        int getHigh();

        @JSProperty
        Int8Array getRgba();
    }

    interface Encoded extends JSObject {

        @JSProperty
        String getFailed();

        @JSProperty
        Int8Array getOctets();
    }

    @JSBody(params = "octets", script = "return jebolDecodeImage(octets);")
    private static native JSPromise<Decoded> decodeInBrowser(Int8Array octets);

    @JSBody(params = {"rgba", "wide", "high", "mediaType"},
            script = "return jebolEncodeImage(rgba, wide, high, mediaType);")
    private static native JSPromise<Encoded> encodeInBrowser(Int8Array rgba, int wide, int high, String mediaType);
}
