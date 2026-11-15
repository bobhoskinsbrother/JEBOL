package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class Blend2DFont {

    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final int LAST_CODE_POINT = 0x10FFFF;

    private final OpenTypeFace face;

    private final double scale;

    record GlyphRun(int[] glyphs, int[][] placements) {
    }

    private Blend2DFont(OpenTypeFace face, double scale) {
        this.face = face;
        this.scale = scale;
    }

    public static Blend2DFont of(OpenTypeFace face, double size) {
        float single = (float) size;
        double scale = face.unitsPerEm != 0 ? (double) single / (double) face.unitsPerEm : 0.0;
        return new Blend2DFont(face, scale);
    }

    OpenTypeFace face() {
        return face;
    }

    double scale() {
        return scale;
    }

    public static int[] decodeUtf8(int[] bytes) {
        int[] decoded = new int[bytes.length];
        int count = 0;
        int at = 0;
        while (at < bytes.length) {
            int[] read = readUtf8(bytes, at);
            if (read == null) {
                decoded[count++] = REPLACEMENT_CHARACTER;
                at++;
            } else {
                decoded[count++] = read[0];
                at += read[1];
            }
        }
        return java.util.Arrays.copyOf(decoded, count);
    }

    private static int[] readUtf8(int[] bytes, int at) {
        int lead = bytes[at];
        if (lead < 0x80) {
            return new int[] {lead, 1};
        }
        int offsetLead = (lead - 0xC2) & 0xFFFFFFFF;
        if (Integer.compareUnsigned(offsetLead, 0xE0 - 0xC2) < 0) {
            if (at + 2 > bytes.length) {
                return null;
            }
            int b1 = bytes[at + 1] ^ 0x80;
            return b1 > 0x3F ? null : new int[] {((lead - 0xC0) << 6) + b1, 2};
        }
        if (Integer.compareUnsigned(offsetLead, 0xF0 - 0xC2) < 0) {
            if (at + 3 > bytes.length) {
                return null;
            }
            int b1 = bytes[at + 1] ^ 0x80;
            int b2 = bytes[at + 2] ^ 0x80;
            int codePoint = ((lead - 0xE0) << 12) + (b1 << 6) + b2;
            return (b1 | b2) > 0x3F || codePoint < 0x800 ? null : new int[] {codePoint, 3};
        }
        if (at + 4 > bytes.length) {
            return null;
        }
        int b1 = bytes[at + 1] ^ 0x80;
        int b2 = bytes[at + 2] ^ 0x80;
        int b3 = bytes[at + 3] ^ 0x80;
        int codePoint = ((lead - 0xF0) << 18) + (b1 << 12) + (b2 << 6) + b3;
        boolean isInvalid = (b1 | b2 | b3) > 0x3F || Integer.compareUnsigned(codePoint, 0x10000) < 0
                || Integer.compareUnsigned(codePoint, LAST_CODE_POINT) > 0;
        return isInvalid ? null : new int[] {codePoint, 4};
    }

    public static int[] decodeUtf16(int[] units) {
        int[] decoded = new int[units.length];
        int count = 0;
        int at = 0;
        while (at < units.length) {
            int unit = units[at++];
            if (unit >= 0xD800 && unit <= 0xDBFF && at < units.length) {
                int low = units[at];
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    decoded[count++] = 0x10000 + ((unit - 0xD800) << 10) + (low - 0xDC00);
                    at++;
                    continue;
                }
            }
            decoded[count++] = unit;
        }
        return java.util.Arrays.copyOf(decoded, count);
    }

    Optional<GlyphRun> shape(int[] codePoints) {
        if (codePoints.length == 0) {
            return Optional.of(new GlyphRun(new int[0], new int[0][]));
        }
        int[] glyphs = codePoints.clone();
        if (!face.characterMap.mapToGlyphs(glyphs)) {
            return Optional.empty();
        }
        OpenTypeLayout layout = face.layout;
        if (layout.hasSubstitutions()) {
            glyphs = layout.substitute(glyphs);
        }
        int[][] placements = new int[glyphs.length][4];
        double[] advances = new double[glyphs.length];
        if (face.metrics.fillAdvances(glyphs, advances)) {
            for (int at = 0; at < glyphs.length; at++) {
                placements[at][2] = (int) advances[at];
            }
        }
        if (layout.hasPositioning()) {
            layout.position(glyphs, placements);
        }
        if (face.kerning != null && face.kerning.isAvailable() && !layout.hasGPosKern()) {
            face.kerning.apply(glyphs, placements);
        }
        return Optional.of(new GlyphRun(glyphs, placements));
    }

    boolean decodeGlyph(int glyphId, Blend2DMatrix transform, Blend2DPath out) {
        if (face.cff != null) {
            return face.cff.decode(glyphId, transform, out);
        }
        return new GlyfOutlines(face).decode(glyphId, transform, out);
    }
}
