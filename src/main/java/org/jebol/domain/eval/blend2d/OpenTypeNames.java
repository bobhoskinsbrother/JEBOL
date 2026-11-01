package org.jebol.domain.eval.blend2d;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

final class OpenTypeNames {

    private static final int HEADER_SIZE = 6;

    private static final int RECORD_SIZE = 12;

    private static final int FAMILY = 1;

    private static final int SUBFAMILY = 2;

    private static final int FULL = 4;

    private static final int POST_SCRIPT = 6;

    private static final int TYPOGRAPHIC_FAMILY = 16;

    private static final int TYPOGRAPHIC_SUBFAMILY = 17;

    private static final int WWS_FAMILY = 21;

    private static final int WWS_SUBFAMILY = 22;

    private static final int LARGEST_COMMON_ID = 26;

    private static final int IMPORTANT_IDS = 1 << FAMILY | 1 << SUBFAMILY | 1 << FULL | 1 << POST_SCRIPT
            | 1 << TYPOGRAPHIC_FAMILY | 1 << TYPOGRAPHIC_SUBFAMILY | 1 << WWS_FAMILY | 1 << WWS_SUBFAMILY;

    private static final int PLATFORM_UNICODE = 0;

    private static final int PLATFORM_MAC = 1;

    private static final int PLATFORM_WINDOWS = 3;

    private static final int MAC_ROMAN = 0;

    private static final int MAC_ENGLISH = 0;

    private static final int WINDOWS_SYMBOL = 0;

    private static final int WINDOWS_UCS2 = 1;

    private static final int WINDOWS_ENGLISH = 0x09;

    private static final int WINDOWS_ENGLISH_US = 0x0409;

    private static final int WINDOWS_ENGLISH_UK = 0x0809;

    private static final int MAC_EMPTY_SUBFAMILY_SCORE = 0xFFFF;

    private OpenTypeNames() {
    }

    static boolean read(OpenTypeFace face, OpenTypeTable name) {
        if (name.isEmpty() || !name.fits(HEADER_SIZE)) {
            return false;
        }
        int format = name.readU16(0);
        int recordCount = name.readU16(2);
        int stringRegionOffset = name.readU16(4);
        if (stringRegionOffset >= name.size() || format > 1) {
            return false;
        }
        if (recordCount == 0 || !name.fits(HEADER_SIZE + (long) recordCount * RECORD_SIZE)) {
            return false;
        }
        int[] scores = new int[LARGEST_COMMON_ID + 1];
        int[] indexes = new int[LARGEST_COMMON_ID + 1];
        int matched = 0;
        int stringRegionSize = name.size() - stringRegionOffset;
        for (int record = 0; record < recordCount; record++) {
            int at = HEADER_SIZE + record * RECORD_SIZE;
            int nameId = name.readU16(at + 6);
            if (nameId > LARGEST_COMMON_ID || (IMPORTANT_IDS & 1 << nameId) == 0) {
                continue;
            }
            int length = name.readU16(at + 8);
            int stringOffset = length == 0 ? 0 : name.readU16(at + 10);
            if (stringOffset >= stringRegionSize || stringRegionSize - stringOffset < length) {
                continue;
            }
            int platformId = name.readU16(at);
            int score = scoreRecord(platformId, name.readU16(at + 2), name.readU16(at + 4));
            if (score < 0) {
                continue;
            }
            if (score != 0) {
                Optional<byte[]> converted = convertToUtf8(name, stringRegionOffset + stringOffset, length, platformId);
                if (converted.isEmpty()) {
                    score = 0;
                    face.diagFlags |= OpenTypeFace.DIAG_WRONG_NAME_DATA;
                } else if (platformId == PLATFORM_MAC && nameId == SUBFAMILY && converted.get().length == 0) {
                    score = MAC_EMPTY_SUBFAMILY_SCORE;
                }
                if (score > scores[nameId]) {
                    scores[nameId] = score;
                    indexes[nameId] = record;
                    matched |= 1 << nameId;
                }
            }
        }
        if ((matched & 1 << TYPOGRAPHIC_FAMILY) != 0) {
            matched &= ~(1 << FAMILY | 1 << WWS_FAMILY);
        }
        if ((matched & 1 << TYPOGRAPHIC_SUBFAMILY) != 0) {
            matched &= ~(1 << SUBFAMILY | 1 << WWS_SUBFAMILY);
        }
        int typographic = 1 << TYPOGRAPHIC_FAMILY | 1 << TYPOGRAPHIC_SUBFAMILY;
        if ((matched & typographic) == typographic) {
            face.faceFlags |= OpenTypeFace.FLAG_TYPOGRAPHIC_NAMES;
        }
        byte[] family = new byte[0];
        byte[] subfamily = new byte[0];
        for (int nameId = 0; nameId <= LARGEST_COMMON_ID; nameId++) {
            if ((matched & 1 << nameId) == 0) {
                continue;
            }
            int at = HEADER_SIZE + indexes[nameId] * RECORD_SIZE;
            int length = name.readU16(at + 8);
            int stringOffset = length == 0 ? 0 : name.readU16(at + 10);
            if (stringOffset >= stringRegionSize || stringRegionSize - stringOffset < length) {
                return false;
            }
            boolean isFamily = nameId == FAMILY || nameId == WWS_FAMILY || nameId == TYPOGRAPHIC_FAMILY;
            boolean isSubfamily = nameId == SUBFAMILY || nameId == WWS_SUBFAMILY || nameId == TYPOGRAPHIC_SUBFAMILY;
            if (!isFamily && !isSubfamily && nameId != FULL && nameId != POST_SCRIPT) {
                continue;
            }
            Optional<byte[]> converted = convertToUtf8(name, stringRegionOffset + stringOffset, length,
                    name.readU16(at));
            if (converted.isEmpty()) {
                return false;
            }
            if (isFamily) {
                family = converted.get();
            } else if (isSubfamily) {
                subfamily = converted.get();
            }
        }
        if (family.length >= subfamily.length && subfamily.length != 0
                && Arrays.equals(family, family.length - subfamily.length, family.length, subfamily, 0,
                subfamily.length)) {
            face.diagFlags |= OpenTypeFace.DIAG_FIXED_NAME_DATA;
        }
        return true;
    }

    private static int scoreRecord(int platformId, int specificId, int languageId) {
        return switch (platformId) {
            case PLATFORM_UNICODE -> 3;
            case PLATFORM_MAC -> specificId != MAC_ROMAN ? -1 : 2 | (languageId == MAC_ENGLISH ? 0x01 << 8 : 0);
            case PLATFORM_WINDOWS -> {
                int base;
                if (specificId == WINDOWS_SYMBOL) {
                    base = 1;
                } else if (specificId == WINDOWS_UCS2) {
                    base = 4;
                } else {
                    yield -1;
                }
                if ((languageId & 0xFF) == WINDOWS_ENGLISH) {
                    base |= (languageId == WINDOWS_ENGLISH_US ? 0x04 : languageId == WINDOWS_ENGLISH_UK ? 0x03 : 0x02)
                            << 8;
                }
                yield base;
            }
            default -> 0;
        };
    }

    private static Optional<byte[]> convertToUtf8(OpenTypeTable name, int at, int length, int platformId) {
        StringBuilder out = new StringBuilder();
        int nulls = 0;
        if (platformId == PLATFORM_UNICODE || platformId == PLATFORM_WINDOWS) {
            int end = at + (length & ~1);
            int cursor = at;
            while (cursor < end) {
                int unit = name.readU16(cursor);
                cursor += 2;
                int codePoint = unit;
                if (unit >= 0xD800 && unit <= 0xDFFF) {
                    if (unit > 0xDBFF || cursor >= end) {
                        return Optional.empty();
                    }
                    int low = name.readU16(cursor);
                    if (low < 0xDC00 || low > 0xDFFF) {
                        return Optional.empty();
                    }
                    cursor += 2;
                    codePoint = 0x10000 + ((unit - 0xD800) << 10) + (low - 0xDC00);
                }
                nulls += codePoint == 0 ? 1 : 0;
                out.appendCodePoint(codePoint);
            }
        } else {
            for (int cursor = at; cursor < at + length; cursor++) {
                int unit = name.readU8(cursor);
                nulls += unit == 0 ? 1 : 0;
                out.appendCodePoint(unit);
            }
        }
        byte[] written = out.toString().getBytes(StandardCharsets.UTF_8);
        int kept = written.length;
        while (kept > 0 && written[kept - 1] == 0) {
            kept--;
            nulls--;
        }
        return nulls != 0 ? Optional.empty() : Optional.of(Arrays.copyOf(written, kept));
    }
}
