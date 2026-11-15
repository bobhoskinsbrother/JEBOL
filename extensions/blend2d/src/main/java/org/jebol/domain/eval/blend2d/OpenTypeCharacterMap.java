package org.jebol.domain.eval.blend2d;

final class OpenTypeCharacterMap {

    private static final int TABLE_SIZE = 12;

    private static final int HEADER_SIZE = 4;

    private static final int ENCODING_SIZE = 8;

    private static final int FORMAT_0_SIZE = 262;

    private static final int FORMAT_4_SIZE = 24;

    private static final int FORMAT_6_SIZE = 12;

    private static final int FORMAT_10_SIZE = 20;

    private static final int FORMAT_12_SIZE = 16;

    private static final int FORMAT_14_SIZE = 10;

    private static final int GROUP_SIZE = 12;

    private static final int LAST_CODE_POINT = 0x10FFFF;

    private static final int SCORE_MAC_ROMAN = 0x00001;

    private static final int SCORE_SYMBOL_FONT = 0x00002;

    private static final int SCORE_ANY_UNICODE = 0x10000;

    private static final int SCORE_WINDOWS_UNICODE = 0x20000;

    private static final int PLATFORM_UNICODE = 0;

    private static final int PLATFORM_MAC = 1;

    private static final int PLATFORM_WINDOWS = 3;

    private static final int WINDOWS_SYMBOL = 0;

    private static final int WINDOWS_UCS2 = 1;

    private static final int WINDOWS_UCS4 = 10;

    private static final int MAC_ROMAN = 0;

    private enum Validation { VALID, INVALID, NOT_IMPLEMENTED }

    private final int format;

    private final OpenTypeTable table;

    private final int encodingOffset;

    private final int entryCount;

    private Validation lastValidation;

    private int validatedFormat;

    private int validatedEntryCount;

    private OpenTypeCharacterMap(int format, OpenTypeTable table, int encodingOffset, int entryCount) {
        this.format = format;
        this.table = table;
        this.encodingOffset = encodingOffset;
        this.entryCount = entryCount;
    }

    static OpenTypeCharacterMap none() {
        return new OpenTypeCharacterMap(-1, OpenTypeTable.EMPTY, 0, 0);
    }

    boolean hasMapping() {
        return format >= 0;
    }

    static OpenTypeCharacterMap read(OpenTypeFace face, OpenTypeTable cmap) {
        if (cmap.isEmpty()) {
            return none();
        }
        if (!cmap.fits(TABLE_SIZE)) {
            face.diagFlags |= OpenTypeFace.DIAG_WRONG_CMAP_DATA;
            return none();
        }
        int encodingCount = cmap.readU16(2);
        if (cmap.size() < HEADER_SIZE + (long) encodingCount * ENCODING_SIZE) {
            face.diagFlags |= OpenTypeFace.DIAG_WRONG_CMAP_DATA;
            return none();
        }
        int matchedScore = 0;
        OpenTypeCharacterMap matched = none();
        OpenTypeCharacterMap validator = none();
        for (int encoding = 0; encoding < encodingCount; encoding++) {
            int at = HEADER_SIZE + encoding * ENCODING_SIZE;
            long offset = cmap.readU32(at + 4);
            if (offset >= cmap.size() - 4L) {
                continue;
            }
            int platformId = cmap.readU16(at);
            int encodingId = cmap.readU16(at + 2);
            int subFormat = cmap.readU16((int) offset);
            if (!isSupportedFormat(subFormat)) {
                continue;
            }
            int score = 0;
            if (platformId == PLATFORM_UNICODE) {
                score = SCORE_ANY_UNICODE + encodingId;
            } else if (platformId == PLATFORM_WINDOWS) {
                if (encodingId == WINDOWS_SYMBOL) {
                    score = SCORE_SYMBOL_FONT;
                    face.faceFlags |= OpenTypeFace.FLAG_SYMBOL_FONT;
                } else if (encodingId == WINDOWS_UCS2 || encodingId == WINDOWS_UCS4) {
                    score = SCORE_WINDOWS_UNICODE + encodingId;
                }
            } else if (platformId == PLATFORM_MAC && encodingId == MAC_ROMAN && subFormat == 0) {
                score = SCORE_MAC_ROMAN;
            }
            if (score > matchedScore) {
                validator.validate(cmap, (int) offset);
                if (validator.lastValidation != Validation.VALID) {
                    face.diagFlags |= validator.lastValidation == Validation.NOT_IMPLEMENTED
                            ? OpenTypeFace.DIAG_WRONG_CMAP_FORMAT : OpenTypeFace.DIAG_WRONG_CMAP_DATA;
                    continue;
                }
                matchedScore = score;
                matched = new OpenTypeCharacterMap(validator.validatedFormat, cmap, (int) offset,
                        validator.validatedEntryCount);
            }
        }
        if (matchedScore != 0) {
            face.faceFlags |= OpenTypeFace.FLAG_CHAR_TO_GLYPH_MAPPING;
            return matched;
        }
        return none();
    }

    private static boolean isSupportedFormat(int candidate) {
        return candidate == 0 || candidate == 4 || candidate == 6 || candidate == 10 || candidate == 12
                || candidate == 13;
    }

    private void accept(int chosenFormat, int count) {
        lastValidation = Validation.VALID;
        validatedFormat = chosenFormat;
        validatedEntryCount = count;
    }

    private void validate(OpenTypeTable cmap, int subTableOffset) {
        lastValidation = Validation.INVALID;
        if (cmap.size() < 4 || subTableOffset > cmap.size() - 4) {
            return;
        }
        OpenTypeTable sub = cmap.subTableUnchecked(subTableOffset);
        int subFormat = sub.readU16(0);
        switch (subFormat) {
            case 0 -> {
                if (sub.fits(FORMAT_0_SIZE)) {
                    int length = sub.readU16(2);
                    if (length >= FORMAT_0_SIZE && length <= sub.size()) {
                        accept(0, 256);
                    }
                }
            }
            case 2, 8 -> lastValidation = Validation.NOT_IMPLEMENTED;
            case 4 -> validateFormat4(sub);
            case 6 -> {
                if (sub.fits(FORMAT_6_SIZE)) {
                    int length = sub.readU16(2);
                    int first = sub.readU16(6);
                    int count = sub.readU16(8);
                    if (length >= FORMAT_6_SIZE && length <= sub.size() && count != 0 && first + count <= 0xFFFF
                            && length >= FORMAT_10_SIZE + count * 2) {
                        accept(6, count);
                    }
                }
            }
            case 10 -> {
                if (sub.fits(FORMAT_10_SIZE)) {
                    long length = sub.readU32(4);
                    long first = sub.readU32(12);
                    long count = sub.readU32(16);
                    if (length >= FORMAT_10_SIZE && length <= sub.size() && first < LAST_CODE_POINT && count != 0
                            && count <= LAST_CODE_POINT && first + count <= LAST_CODE_POINT
                            && length >= FORMAT_10_SIZE + count * 2) {
                        accept(10, (int) count);
                    }
                }
            }
            case 12, 13 -> validateFormat12(sub, subFormat);
            case 14 -> {
                if (sub.fits(FORMAT_14_SIZE)) {
                    lastValidation = Validation.NOT_IMPLEMENTED;
                }
            }
            default -> {
            }
        }
    }

    private void validateFormat4(OpenTypeTable sub) {
        if (!sub.fits(FORMAT_4_SIZE)) {
            return;
        }
        int length = sub.readU16(2);
        if (length < FORMAT_4_SIZE || length > sub.size()) {
            return;
        }
        int segmentsTwice = sub.readU16(6);
        if (segmentsTwice == 0 || (segmentsTwice & 1) != 0) {
            return;
        }
        int segments = segmentsTwice / 2;
        if (length < 16 + segments * 8) {
            return;
        }
        int previousEnd = 0;
        int segmentsAfterCheck = segments;
        for (int segment = 0; segment < segments; segment++) {
            int last = sub.readU16(14 + segment * 2);
            int first = sub.readU16(16 + segments * 2 + segment * 2);
            int idOffset = sub.readU16(16 + segments * 6 + segment * 2);
            if (first == 0xFFFF && last == 0xFFFF) {
                segmentsAfterCheck = Math.min(segmentsAfterCheck, segment);
            } else {
                if (first < previousEnd || first > last || (segment != 0 && first == previousEnd)) {
                    return;
                }
                if (idOffset != 0 && ((idOffset & 1) != 0
                        || 16 + segments * 6 + idOffset + (last - first) * 2 >= length)) {
                    return;
                }
            }
            previousEnd = last;
        }
        if (segmentsAfterCheck != 0) {
            accept(4, segmentsAfterCheck);
        }
    }

    private void validateFormat12(OpenTypeTable sub, int subFormat) {
        if (!sub.fits(FORMAT_12_SIZE)) {
            return;
        }
        long length = sub.readU32(4);
        if (length < FORMAT_12_SIZE || length > sub.size()) {
            return;
        }
        long count = sub.readU32(12);
        if (count > LAST_CODE_POINT || length < FORMAT_12_SIZE + count * GROUP_SIZE) {
            return;
        }
        long first = sub.readU32(16);
        long last = sub.readU32(20);
        if (first > last || last > LAST_CODE_POINT) {
            return;
        }
        for (int group = 1; group < count; group++) {
            first = sub.readU32(16 + group * GROUP_SIZE);
            if (first <= last) {
                return;
            }
            last = sub.readU32(20 + group * GROUP_SIZE);
            if (first > last || last > LAST_CODE_POINT) {
                return;
            }
        }
        accept(subFormat, (int) count);
    }

    boolean mapToGlyphs(int[] content) {
        if (format < 0) {
            return false;
        }
        OpenTypeTable sub = table.subTableUnchecked(encodingOffset);
        switch (format) {
            case 0 -> {
                for (int at = 0; at < content.length; at++) {
                    content[at] = Integer.compareUnsigned(content[at], 256) < 0 ? sub.readU8(6 + content[at]) : 0;
                }
            }
            case 4 -> mapFormat4(sub, content);
            case 6 -> mapRange(content, sub.readU16(6), sub.readU16(8), sub, 10);
            case 10 -> mapRange(content, sub.readI32(12), (int) sub.readU32(16), sub, 20);
            default -> mapFormat12(sub, content, format == 12);
        }
        return true;
    }

    private static void mapRange(int[] content, int first, int count, OpenTypeTable sub, int arrayAt) {
        for (int at = 0; at < content.length; at++) {
            int index = content[at] - first;
            content[at] = Integer.compareUnsigned(index, count) < 0 ? sub.readU16(arrayAt + index * 2) : 0;
        }
    }

    private int findSegmentFormat4(int codePoint, int segments, int[] range) {
        int lastCharAt = 14;
        for (int span = entryCount; span != 0; span >>>= 1) {
            int endAt = lastCharAt + (span & ~1);
            OpenTypeTable sub = table.subTableUnchecked(encodingOffset);
            int last = sub.readU16(endAt);
            range[1] = last;
            if (Integer.compareUnsigned(last, codePoint) < 0) {
                lastCharAt = endAt + 2;
                span--;
                continue;
            }
            int first = sub.readU16(endAt + 2 + segments * 2);
            range[0] = first;
            if (Integer.compareUnsigned(first, codePoint) <= 0) {
                return endAt;
            }
        }
        return -1;
    }

    private void mapFormat4(OpenTypeTable sub, int[] content) {
        int segments = sub.readU16(6) >>> 1;
        int dataEnd = table.size() - encodingOffset - 1;
        int idDeltaArrayOffset = 2 + segments * 4;
        int idOffsetArrayOffset = 2 + segments * 6;
        int[] range = new int[2];
        int at = 0;
        while (at < content.length) {
            int codePoint = content[at];
            int match = findSegmentFormat4(codePoint, segments, range);
            if (match < 0) {
                content[at++] = 0;
                continue;
            }
            int idOffset = sub.readU16(match + idOffsetArrayOffset);
            while (true) {
                int glyph = codePoint;
                boolean isDefined = true;
                if (idOffset != 0) {
                    long rawRemain = dataEnd - match;
                    long rawOffset = idOffsetArrayOffset + (long) (codePoint - range[0]) * 2 + idOffset;
                    if (rawOffset >= rawRemain) {
                        isDefined = false;
                    } else {
                        glyph = sub.readU16(match + (int) rawOffset);
                    }
                }
                if (isDefined) {
                    glyph = (glyph + sub.readU16(match + idDeltaArrayOffset)) & 0xFFFF;
                    isDefined = glyph != 0;
                }
                if (!isDefined) {
                    content[at++] = 0;
                    break;
                }
                content[at] = glyph;
                if (++at == content.length) {
                    return;
                }
                codePoint = content[at];
                if (Integer.compareUnsigned(codePoint, range[0]) < 0 || Integer.compareUnsigned(codePoint, range[1]) > 0) {
                    break;
                }
            }
        }
    }

    private void mapFormat12(OpenTypeTable sub, int[] content, boolean isFormat12) {
        int at = 0;
        long[] group = new long[3];
        while (at < content.length) {
            long codePoint = content[at] & 0xFFFFFFFFL;
            if (!findGroup(sub, codePoint, group)) {
                content[at++] = 0;
                continue;
            }
            while (true) {
                int glyph = (int) ((isFormat12 ? group[2] + codePoint - group[0] : group[2]) & 0xFFFF);
                if (glyph == 0) {
                    content[at++] = 0;
                    break;
                }
                content[at] = glyph;
                if (++at == content.length) {
                    return;
                }
                codePoint = content[at] & 0xFFFFFFFFL;
                if (codePoint < group[0] || codePoint > group[1]) {
                    break;
                }
            }
        }
    }

    private boolean findGroup(OpenTypeTable sub, long codePoint, long[] group) {
        int start = 0;
        for (int span = entryCount; span != 0; span >>>= 1) {
            int middle = start + (span >>> 1);
            int at = 16 + middle * GROUP_SIZE;
            long last = sub.readU32(at + 4);
            group[1] = last;
            if (last < codePoint) {
                start = middle + 1;
                span--;
                continue;
            }
            long first = sub.readU32(at);
            group[0] = first;
            if (first > codePoint) {
                continue;
            }
            group[2] = sub.readU32(at + 8);
            return true;
        }
        return false;
    }
}
