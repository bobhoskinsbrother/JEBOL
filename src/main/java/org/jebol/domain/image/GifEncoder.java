package org.jebol.domain.image;

import org.jebol.domain.eval.compression.Octets;
import org.jebol.domain.host.ImagePort.Pixels;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GifEncoder {

    private static final int PALETTE_ENTRIES = 256;
    private static final int CHANNELS = 4;
    private static final int TABLE_PRESENT = 0x80;
    private static final int RESOLUTION_SHIFT = 4;
    private static final int IMAGE = 0x2C;
    private static final int TRAILER = 0x3B;
    private static final int SMALLEST_CODE_SIZE = 2;
    private static final int LARGEST_SUB_BLOCK = 255;

    private GifEncoder() {
    }

    public static byte[] encode(Pixels image) {
        int[] rgb = opaqueColours(image);
        Map<Integer, Integer> ownPalette = paletteInOrderSeen(rgb);
        int[] palette;
        byte[] indices;
        if (ownPalette != null) {
            palette = ownPalette.keySet().stream().mapToInt(Integer::intValue).toArray();
            indices = new byte[rgb.length];
            for (int at = 0; at < rgb.length; at++) {
                indices[at] = (byte) (int) ownPalette.get(rgb[at]);
            }
        } else {
            palette = MedianCutQuantiser.choosePalette(rgb, PALETTE_ENTRIES);
            indices = MedianCutQuantiser.ditherOnto(rgb, image.wide(), image.high(), palette);
        }
        return write(image.wide(), image.high(), palette, indices);
    }

    private static int[] opaqueColours(Pixels image) {
        byte[] rgba = image.rgba();
        int[] rgb = new int[image.wide() * image.high()];
        for (int at = 0; at < rgb.length; at++) {
            int pixel = at * CHANNELS;
            rgb[at] = ((rgba[pixel] & 0xFF) << 16) | ((rgba[pixel + 1] & 0xFF) << 8) | (rgba[pixel + 2] & 0xFF);
        }
        return rgb;
    }

    private static Map<Integer, Integer> paletteInOrderSeen(int[] rgb) {
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int colour : rgb) {
            if (!palette.containsKey(colour)) {
                if (palette.size() == PALETTE_ENTRIES) {
                    return null;
                }
                palette.put(colour, palette.size());
            }
        }
        return palette;
    }

    private static byte[] write(int wide, int high, int[] palette, byte[] indices) {
        int bits = 1;
        while ((1 << bits) < palette.length) {
            bits++;
        }
        Octets out = new Octets();
        byte[] signature = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        out.write(signature, 0, signature.length);
        writeTwoBytes(out, wide);
        writeTwoBytes(out, high);
        out.write(TABLE_PRESENT | ((bits - 1) << RESOLUTION_SHIFT) | (bits - 1));
        out.write(0);
        out.write(0);
        for (int entry = 0; entry < (1 << bits); entry++) {
            int colour = entry < palette.length ? palette[entry] : 0;
            out.write(colour >> 16);
            out.write(colour >> 8);
            out.write(colour);
        }
        out.write(IMAGE);
        writeTwoBytes(out, 0);
        writeTwoBytes(out, 0);
        writeTwoBytes(out, wide);
        writeTwoBytes(out, high);
        out.write(0);
        int codeSize = Math.max(SMALLEST_CODE_SIZE, bits);
        out.write(codeSize);
        byte[] compressed = LzwEncoder.encode(indices, codeSize);
        for (int at = 0; at < compressed.length; at += LARGEST_SUB_BLOCK) {
            int length = Math.min(LARGEST_SUB_BLOCK, compressed.length - at);
            out.write(length);
            out.write(compressed, at, length);
        }
        out.write(0);
        out.write(TRAILER);
        return out.toArray();
    }

    private static void writeTwoBytes(Octets out, int value) {
        out.write(value);
        out.write(value >> 8);
    }
}
