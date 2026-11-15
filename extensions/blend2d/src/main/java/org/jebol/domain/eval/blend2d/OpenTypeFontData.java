package org.jebol.domain.eval.blend2d;

import java.util.Optional;

final class OpenTypeFontData {

    private static final int SFNT_HEADER_SIZE = 12;

    private static final int TABLE_RECORD_SIZE = 16;

    private static final int COLLECTION_HEADER_SIZE = 12;

    private static final int LARGEST_FACE_COUNT = 256;

    private static final long COLLECTION_TAG = tag("ttcf");

    private static final long[] VERSION_TAGS = {tag("OTTO"), 0x00010000L, tag("true")};

    private final OpenTypeTable whole;

    private final int faceCount;

    private final boolean isCollection;

    private OpenTypeFontData(OpenTypeTable whole, int faceCount, boolean isCollection) {
        this.whole = whole;
        this.faceCount = faceCount;
        this.isCollection = isCollection;
    }

    static long tag(String name) {
        return (long) name.charAt(0) << 24 | name.charAt(1) << 16 | name.charAt(2) << 8 | name.charAt(3);
    }

    static Optional<OpenTypeFontData> read(byte[] bytes) {
        OpenTypeTable whole = new OpenTypeTable(bytes, 0, bytes.length);
        if (bytes.length < Math.min(SFNT_HEADER_SIZE, COLLECTION_HEADER_SIZE)) {
            return Optional.empty();
        }
        long headerTag = whole.readU32(0);
        if (headerTag == COLLECTION_TAG) {
            long count = whole.readU32(8);
            if (count == 0 || count > LARGEST_FACE_COUNT || COLLECTION_HEADER_SIZE + count * 4 > bytes.length) {
                return Optional.empty();
            }
            return Optional.of(new OpenTypeFontData(whole, (int) count, true));
        }
        if (!isVersionTag(headerTag)) {
            return Optional.empty();
        }
        return Optional.of(new OpenTypeFontData(whole, 1, false));
    }

    private static boolean isVersionTag(long candidate) {
        for (long version : VERSION_TAGS) {
            if (candidate == version) {
                return true;
            }
        }
        return false;
    }

    int faceCount() {
        return faceCount;
    }

    OpenTypeTable[] findTables(int faceIndex, long[] tags) {
        OpenTypeTable[] found = new OpenTypeTable[tags.length];
        java.util.Arrays.fill(found, OpenTypeTable.EMPTY);
        long dataSize = whole.size();
        long headerOffset = isCollection ? whole.readU32(COLLECTION_HEADER_SIZE + faceIndex * 4) : 0;
        if (headerOffset > dataSize - SFNT_HEADER_SIZE || !isVersionTag(whole.readU32((int) headerOffset))) {
            return found;
        }
        int tableCount = whole.readU16((int) headerOffset + 4);
        if (dataSize - headerOffset < SFNT_HEADER_SIZE + (long) tableCount * TABLE_RECORD_SIZE) {
            return found;
        }
        for (int record = 0; record < tableCount; record++) {
            int at = (int) headerOffset + SFNT_HEADER_SIZE + record * TABLE_RECORD_SIZE;
            long recordTag = whole.readU32(at);
            for (int asked = 0; asked < tags.length; asked++) {
                if (tags[asked] != recordTag) {
                    continue;
                }
                long tableOffset = whole.readU32(at + 8);
                long tableSize = whole.readU32(at + 12);
                if (tableOffset < dataSize && tableSize != 0 && tableSize <= dataSize - tableOffset) {
                    found[asked] = new OpenTypeTable(whole.data(), (int) tableOffset, (int) tableSize);
                }
            }
        }
        return found;
    }
}
