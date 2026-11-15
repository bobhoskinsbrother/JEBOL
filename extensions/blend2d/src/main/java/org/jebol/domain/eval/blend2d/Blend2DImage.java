package org.jebol.domain.eval.blend2d;

import org.jebol.domain.eval.Qoi;
import org.jebol.domain.host.ImagePort;

import java.util.Optional;
import java.util.Set;

public record Blend2DImage(int wide, int high, int[] pixels, boolean isOpaqueFormat) {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] BMP_SIGNATURE = {'B', 'M'};

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8};

    private static final byte[] QOI_SIGNATURE = {'q', 'o', 'i', 'f'};

    private static final int PNG_COLOUR_TYPE_AT = 25;

    private static final int PNG_RGB = 2;

    private static final int QOI_CHANNELS_AT = 12;

    private static final int QOI_WITH_ALPHA = 4;

    private static final int BMP_HEADER_SIZE_AT = 14;

    private static final int BMP_DEPTH_AT = 28;

    private static final int BMP_COMPRESSION_AT = 30;

    private static final int BMP_ALPHA_MASK_AT = 66;

    private static final int BMP_FIRST_HEADER = 40;

    private static final int BMP_HEADER_WITH_ALPHA_MASK = 56;

    private static final int BMP_ALPHA_BIT_FIELDS = 6;

    private static final int BMP_DEEPEST_INDEXED = 8;

    private static final Set<Integer> BMP_HEADER_SIZES_READ = Set.of(12, 40, 52, 56, 108);

    private static final int OPAQUE = 0xFF000000;

    private enum Treatment { PREMULTIPLY, TAKE_AS_PREMULTIPLIED, OPAQUE }

    public static Optional<Blend2DImage> decode(byte[] encoded, ImagePort codec) {
        if (startsWith(encoded, QOI_SIGNATURE)) {
            return decodeQoi(encoded);
        }
        Optional<Treatment> treatment = chooseTreatment(encoded);
        if (treatment.isEmpty()) {
            return Optional.empty();
        }
        ImagePort.Pixels read = codec.decode(encoded, "", 1);
        if (read == null || read.wide() <= 0 || read.high() <= 0) {
            return Optional.empty();
        }
        return Optional.of(convert(read.wide(), read.high(), read.rgba(), treatment.get()));
    }

    private static Optional<Treatment> chooseTreatment(byte[] encoded) {
        if (startsWith(encoded, PNG_SIGNATURE) && encoded.length > PNG_COLOUR_TYPE_AT) {
            return Optional.of(encoded[PNG_COLOUR_TYPE_AT] == PNG_RGB ? Treatment.OPAQUE : Treatment.PREMULTIPLY);
        }
        if (startsWith(encoded, JPEG_SIGNATURE)) {
            return Optional.of(Treatment.OPAQUE);
        }
        if (startsWith(encoded, BMP_SIGNATURE) && hasReadableBmpHeader(encoded)) {
            return Optional.of(hasBmpAlphaMask(encoded) ? Treatment.TAKE_AS_PREMULTIPLIED : Treatment.OPAQUE);
        }
        return Optional.empty();
    }

    private static boolean hasReadableBmpHeader(byte[] encoded) {
        return encoded.length >= BMP_HEADER_SIZE_AT + 4
                && BMP_HEADER_SIZES_READ.contains(readLittleEndian(encoded, BMP_HEADER_SIZE_AT, 4));
    }

    private static boolean hasBmpAlphaMask(byte[] encoded) {
        if (encoded.length < BMP_ALPHA_MASK_AT + 4) {
            return false;
        }
        int headerSize = readLittleEndian(encoded, BMP_HEADER_SIZE_AT, 4);
        int depth = readLittleEndian(encoded, BMP_DEPTH_AT, 2);
        int compression = readLittleEndian(encoded, BMP_COMPRESSION_AT, 4);
        boolean carriesMask = headerSize >= BMP_HEADER_WITH_ALPHA_MASK
                || (headerSize == BMP_FIRST_HEADER && compression == BMP_ALPHA_BIT_FIELDS);
        return depth > BMP_DEEPEST_INDEXED && carriesMask && readLittleEndian(encoded, BMP_ALPHA_MASK_AT, 4) != 0;
    }

    private static Optional<Blend2DImage> decodeQoi(byte[] encoded) {
        Qoi.Decoded read = Qoi.decode(encoded);
        if (read == null || encoded.length <= QOI_CHANNELS_AT) {
            return Optional.empty();
        }
        Treatment treatment = encoded[QOI_CHANNELS_AT] == QOI_WITH_ALPHA ? Treatment.PREMULTIPLY : Treatment.OPAQUE;
        return Optional.of(convert(read.wide(), read.high(), read.pixels(), treatment));
    }

    private static Blend2DImage convert(int wide, int high, byte[] rgba, Treatment treatment) {
        int[] pixels = new int[wide * high];
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            int at = pixel * 4;
            int straight = (rgba[at + 3] & 0xFF) << 24 | (rgba[at] & 0xFF) << 16 | (rgba[at + 1] & 0xFF) << 8
                    | (rgba[at + 2] & 0xFF);
            pixels[pixel] = switch (treatment) {
                case PREMULTIPLY -> SolidFill.premultiply(straight);
                case TAKE_AS_PREMULTIPLIED -> straight;
                case OPAQUE -> straight | OPAQUE;
            };
        }
        return new Blend2DImage(wide, high, pixels, treatment == Treatment.OPAQUE);
    }

    private static boolean startsWith(byte[] encoded, byte[] signature) {
        if (encoded.length < signature.length) {
            return false;
        }
        for (int at = 0; at < signature.length; at++) {
            if (encoded[at] != signature[at]) {
                return false;
            }
        }
        return true;
    }

    private static int readLittleEndian(byte[] bytes, int at, int length) {
        int value = 0;
        for (int step = length - 1; step >= 0; step--) {
            value = value << 8 | (bytes[at + step] & 0xFF);
        }
        return value;
    }
}
