package org.jebol.application;

import org.jebol.domain.eval.blurhash.BlurHashDecoder;
import org.jebol.domain.eval.blurhash.BlurHashEncoder;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class BlurHashExtension {

    private static final String HEADER = "REBOL [Title: {Rebol BlurHash Extension} Type: module]";

    private static final int COMPONENTS_EACH_WAY = 3;

    private static final int BYTES_PIXEL = 4;

    private static final int WIDEST_CHARACTER_STORED_IN_BYTE = 0xFF;

    private BlurHashExtension() {
    }

    static BundledExtension create() {
        return new BundledExtension(new Extension("blurhash", "1.0.0", List.of(
                new ExtensionCommand("encode",
                        "{Encode an image using BlurHash algorithm} img [image!] {Source image}",
                        call -> encode((ImageValue) call.arguments().getFirst())),
                new ExtensionCommand("decode",
                        "{Decode BlurHash string to an image} "
                                + "text [any-string! binary!] {Textual image representation} "
                                + "image [image! pair!] {Output image or size of the new image} "
                                + "/contrast punch [integer!] {The factor to improve the contrast, default = 1}",
                        call -> decode(call.arguments().getFirst(), call.arguments().get(1),
                                call.refinements().contains("contrast")
                                        ? (int) ((IntegerValue) call.arguments().get(2)).magnitude()
                                        : 1)))), HEADER);
    }

    private static Value encode(ImageValue image) {
        ImageStorage storage = image.storage();
        int wide = storage.wide();
        int high = storage.high();
        return BlurHashEncoder.encode(COMPONENTS_EACH_WAY, COMPONENTS_EACH_WAY, wide, high,
                        readInDesktopMemoryOrder(storage, wide * high), BYTES_PIXEL * wide)
                .<Value>map(StringValue::of)
                .orElseGet(NoneValue::none);
    }

    private static Value decode(Value text, Value target, int punch) {
        ImageValue image = target instanceof PairValue size
                ? makeImageOfSize(size)
                : ((ImageValue) target).head();
        ImageStorage storage = image.storage();
        int wide = storage.wide();
        int high = storage.high();
        byte[] pixels = new byte[wide * high * BYTES_PIXEL];
        if (!BlurHashDecoder.decodeInto(readAsStoredFromHead(text), wide, high, punch, BYTES_PIXEL, pixels)) {
            return NoneValue.none();
        }
        writeFromDesktopMemoryOrder(pixels, storage);
        return image;
    }

    private static ImageValue makeImageOfSize(PairValue size) {
        int wide = (int) Math.floor(size.x() + 0.5);
        int high = (int) Math.floor(size.y() + 0.5);
        if (Integer.compareUnsigned(wide, ImageStorage.LONGEST_SIDE) > 0
                || Integer.compareUnsigned(high, ImageStorage.LONGEST_SIDE) > 0) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT, WordValue.of("image!"));
        }
        return ImageValue.of(wide, high);
    }

    private static byte[] readInDesktopMemoryOrder(ImageStorage storage, int pixelCount) {
        byte[] pixels = new byte[pixelCount * BYTES_PIXEL];
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int[] rgba = storage.readPixelAt(pixel + 1);
            int at = pixel * BYTES_PIXEL;
            pixels[at] = (byte) rgba[2];
            pixels[at + 1] = (byte) rgba[1];
            pixels[at + 2] = (byte) rgba[0];
            pixels[at + 3] = (byte) rgba[3];
        }
        return pixels;
    }

    private static void writeFromDesktopMemoryOrder(byte[] pixels, ImageStorage storage) {
        for (int pixel = 0; pixel < pixels.length / BYTES_PIXEL; pixel++) {
            int at = pixel * BYTES_PIXEL;
            storage.setColourAt(pixel + 1, pixels[at + 2] & 0xFF, pixels[at + 1] & 0xFF, pixels[at] & 0xFF);
            storage.setAlphaAt(pixel + 1, pixels[at + 3] & 0xFF);
        }
    }

    private static byte[] readAsStoredFromHead(Value text) {
        if (text instanceof BinaryValue bytes) {
            return bytes.head().octetsFromHere();
        }
        String characters = ((AnyStringValue) text).head().text();
        boolean isStoredWide = characters.codePoints().anyMatch(code -> code > WIDEST_CHARACTER_STORED_IN_BYTE);
        return characters.getBytes(isStoredWide ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1);
    }
}
