package org.jebol.domain.eval.compression.zstd;

final class ZstdFrameHeader {

    static final long CONTENT_SIZE_UNKNOWN = -1L;
    static final int MAGIC_NUMBER = 0xFD2FB528;
    static final int SKIPPABLE_START = 0x184D2A50;
    static final int SKIPPABLE_MASK = 0xFFFFFFF0;
    static final int PREFIX_SIZE = 5;
    static final int SKIPPABLE_HEADER_SIZE = 8;
    static final int FRAME_ID_SIZE = 4;
    static final int BLOCK_SIZE_MAX = 1 << 17;
    private static final int WINDOW_LOG_ABSOLUTE_MIN = 10;
    private static final int WINDOW_LOG_MAX = 31;
    private static final int[] DICTIONARY_ID_FIELD_SIZE = {0, 1, 2, 4};
    private static final int[] CONTENT_SIZE_FIELD_SIZE = {0, 2, 4, 8};

    boolean isSkippable;
    long frameContentSize;
    long windowSize;
    int blockSizeMax;
    int dictionaryId;
    boolean hasChecksum;
    int headerSize;

    static boolean isSkippableMagic(int magic) {
        return (magic & SKIPPABLE_MASK) == SKIPPABLE_START;
    }

    static int headerSizeAt(byte[] source, int from, int length) {
        if (length < PREFIX_SIZE) {
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        int descriptor = source[from + PREFIX_SIZE - 1] & 0xFF;
        int dictionaryIdCode = descriptor & 3;
        int singleSegment = (descriptor >> 5) & 1;
        int contentSizeCode = descriptor >> 6;
        return PREFIX_SIZE + (singleSegment == 0 ? 1 : 0)
                + DICTIONARY_ID_FIELD_SIZE[dictionaryIdCode] + CONTENT_SIZE_FIELD_SIZE[contentSizeCode]
                + (singleSegment == 1 && contentSizeCode == 0 ? 1 : 0);
    }

    long read(byte[] source, int from, int length) {
        if (length < PREFIX_SIZE) {
            if (length > 0) {
                byte[] magic = {(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD};
                int toCopy = length;
                System.arraycopy(source, from, magic, 0, toCopy);
                if (ZstdOctets.readInt(magic, 0) != MAGIC_NUMBER) {
                    byte[] skippable = {(byte) 0x50, (byte) 0x2A, (byte) 0x4D, (byte) 0x18};
                    System.arraycopy(source, from, skippable, 0, toCopy);
                    if (!isSkippableMagic(ZstdOctets.readInt(skippable, 0))) {
                        throw ZstdError.PREFIX_UNKNOWN.failure();
                    }
                }
            }
            return PREFIX_SIZE;
        }
        clear();
        int magic = ZstdOctets.readInt(source, from);
        if (magic != MAGIC_NUMBER) {
            if (isSkippableMagic(magic)) {
                if (length < SKIPPABLE_HEADER_SIZE) {
                    return SKIPPABLE_HEADER_SIZE;
                }
                clear();
                isSkippable = true;
                dictionaryId = magic - SKIPPABLE_START;
                headerSize = SKIPPABLE_HEADER_SIZE;
                frameContentSize = ZstdOctets.readInt(source, from + FRAME_ID_SIZE) & 0xFFFFFFFFL;
                return 0;
            }
            throw ZstdError.PREFIX_UNKNOWN.failure();
        }
        int fullSize = headerSizeAt(source, from, length);
        if (length < fullSize) {
            return fullSize;
        }
        headerSize = fullSize;
        int descriptor = source[from + PREFIX_SIZE - 1] & 0xFF;
        int at = from + PREFIX_SIZE;
        int dictionaryIdCode = descriptor & 3;
        boolean isChecksummed = ((descriptor >> 2) & 1) != 0;
        boolean isSingleSegment = ((descriptor >> 5) & 1) != 0;
        int contentSizeCode = descriptor >> 6;
        long window = 0;
        int dictionary = 0;
        long contentSize = CONTENT_SIZE_UNKNOWN;
        if ((descriptor & 0x08) != 0) {
            throw ZstdError.FRAME_PARAMETER_UNSUPPORTED.failure();
        }
        if (!isSingleSegment) {
            int windowDescriptor = source[at++] & 0xFF;
            int windowLog = (windowDescriptor >> 3) + WINDOW_LOG_ABSOLUTE_MIN;
            if (windowLog > WINDOW_LOG_MAX) {
                throw ZstdError.FRAME_PARAMETER_WINDOW_TOO_LARGE.failure();
            }
            window = 1L << windowLog;
            window += (window >> 3) * (windowDescriptor & 7);
        }
        switch (dictionaryIdCode) {
            case 1 -> dictionary = source[at++] & 0xFF;
            case 2 -> {
                dictionary = ZstdOctets.readShort(source, at);
                at += 2;
            }
            case 3 -> {
                dictionary = ZstdOctets.readInt(source, at);
                at += 4;
            }
            default -> {
            }
        }
        switch (contentSizeCode) {
            case 1 -> contentSize = ZstdOctets.readShort(source, at) + 256L;
            case 2 -> contentSize = ZstdOctets.readInt(source, at) & 0xFFFFFFFFL;
            case 3 -> contentSize = ZstdOctets.readLong(source, at);
            default -> {
                if (isSingleSegment) {
                    contentSize = source[at] & 0xFF;
                }
            }
        }
        if (isSingleSegment) {
            window = contentSize;
        }
        isSkippable = false;
        frameContentSize = contentSize;
        windowSize = window;
        blockSizeMax = Long.compareUnsigned(window, BLOCK_SIZE_MAX) < 0 ? (int) window : BLOCK_SIZE_MAX;
        dictionaryId = dictionary;
        hasChecksum = isChecksummed;
        return 0;
    }

    private void clear() {
        isSkippable = false;
        frameContentSize = 0;
        windowSize = 0;
        blockSizeMax = 0;
        dictionaryId = 0;
        hasChecksum = false;
        headerSize = 0;
    }
}
