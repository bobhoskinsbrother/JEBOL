package org.jebol.domain.eval.blend2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class CffOutlines {

    private static final int VERSION_1 = 0;

    private static final int HEADER_SIZE = 4;

    private static final int OFFSET_ADJUSTMENT = 1;

    private static final int ESCAPE = 0x0C;

    private static final int DICT_CHAR_STRINGS = 0x0011;

    private static final int DICT_PRIVATE = 0x0012;

    private static final int DICT_SUBRS = 0x0013;

    private static final int DICT_ROS = 0x0C1E;

    private static final int DICT_FD_ARRAY = 0x0C24;

    private static final int DICT_FD_SELECT = 0x0C25;

    private static final int DICT_VALUE_CAPACITY = 48;

    private static final int IS_CID = 0x1;

    private static final int HAS_FD_ARRAY = 0x2;

    private static final int HAS_FD_SELECT = 0x4;

    private static final int ALL_CID_FLAGS = 0x7;

    private static final long PROGRAM_LIMIT = 1000000;

    private static final int CALL_STACK_SIZE = 16;

    private static final int STORAGE_SIZE = 32;

    private static final int VALUE_STACK_SIZE = 48;

    private static final double FROM_F16DOT16 = 1.0 / 65536.0;

    private static final int HAS_WIDTH = 0x01;

    private static final int PATH_OPEN = 0x02;

    private static final int UNKNOWN = 0xFFFF;

    private static final int[] BASE_OPERANDS = new int[32];

    private static final int[] ESCAPED_OPERANDS = new int[48];

    static {
        java.util.Arrays.fill(BASE_OPERANDS, UNKNOWN);
        java.util.Arrays.fill(ESCAPED_OPERANDS, UNKNOWN);
        int[][] base = {{0x0C, 0}, {0x1C, 0}, {0x15, 2}, {0x16, 1}, {0x04, 1}, {0x05, 2}, {0x06, 1}, {0x07, 1},
            {0x08, 6}, {0x1B, 4}, {0x1A, 4}, {0x1E, 4}, {0x1F, 4}, {0x18, 8}, {0x19, 8}, {0x01, 2}, {0x03, 2},
            {0x12, 2}, {0x17, 2}, {0x13, 0}, {0x14, 0}, {0x0A, 1}, {0x1D, 1}, {0x0B, 0}, {0x0E, 0}};
        for (int[] entry : base) {
            BASE_OPERANDS[entry[0]] = entry[1];
        }
        int[][] escaped = {{0x23, 13}, {0x25, 11}, {0x22, 7}, {0x24, 9}, {0x03, 2}, {0x04, 2}, {0x0F, 2}, {0x16, 4},
            {0x05, 1}, {0x0E, 1}, {0x09, 1}, {0x1A, 1}, {0x0A, 2}, {0x0B, 2}, {0x18, 2}, {0x0C, 2}, {0x17, 0},
            {0x1B, 1}, {0x12, 1}, {0x1C, 2}, {0x1D, 2}, {0x1E, 2}, {0x14, 2}, {0x15, 1}};
        for (int[] entry : escaped) {
            ESCAPED_OPERANDS[entry[0]] = entry[1];
        }
    }

    private record Index(int count, int headerSize, int offsetSize, int payloadSize, int totalSize, int offsetsAt,
                         int payloadAt) {

        static final Index NONE = new Index(0, 0, 0, 0, 0, 0, 0);
    }

    private record IndexData(int offset, int size, int entryCount, int headerSize, int offsetSize, int bias) {

        int payloadOffset() {
            return headerSize + (entryCount + 1) * offsetSize;
        }

        int payloadSize() {
            return size - payloadOffset();
        }

        static IndexData of(int offset, Index index, int bias) {
            return new IndexData(offset, index.totalSize(), index.count(), index.headerSize(), index.offsetSize(), bias);
        }
    }

    private static final class InvalidCff extends Exception {

        private static final long serialVersionUID = 1L;

        InvalidCff() {
            super(null, null, false, false);
        }
    }

    private final int version;

    private final OpenTypeTable table;

    private final IndexData globalSubroutines;

    private final IndexData localSubroutines;

    private final IndexData charStrings;

    private final int fdSelectOffset;

    private final int fdSelectFormat;

    private final List<IndexData> fdSubroutines;

    private CffOutlines(int version, OpenTypeTable table, IndexData globalSubroutines,
                        IndexData localSubroutines, IndexData charStrings, int fdSelectOffset, int fdSelectFormat,
                        List<IndexData> fdSubroutines) {
        this.version = version;
        this.table = table;
        this.globalSubroutines = globalSubroutines;
        this.localSubroutines = localSubroutines;
        this.charStrings = charStrings;
        this.fdSelectOffset = fdSelectOffset;
        this.fdSelectFormat = fdSelectFormat;
        this.fdSubroutines = fdSubroutines;
    }

    int version() {
        return version;
    }

    static int asUnsigned32(double value) {
        if (Double.isNaN(value) || value >= 0x1p63 || value < -0x1p63) {
            return 0;
        }
        return (int) (long) value;
    }

    static int asSigned32(double value) {
        if (Double.isNaN(value) || value >= 0x1p31 || value < -0x1p31) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static int bias(int count) {
        if (count < 1240) {
            return 107;
        }
        return count < 33900 ? 1131 : 32768;
    }

    private static int readOffset(OpenTypeTable cff, int at, int size) {
        int offset = 0;
        for (int step = 0; step < size; step++) {
            offset = offset << 8 | cff.readU8(at + step);
        }
        return offset;
    }

    private static Index readIndex(OpenTypeTable cff, int at, long dataSize, int version) throws InvalidCff {
        int count;
        int headerSize;
        if (version == VERSION_1) {
            if (dataSize < 2) {
                throw new InvalidCff();
            }
            count = cff.readU16(at);
            headerSize = 2;
        } else {
            if (dataSize < 4) {
                throw new InvalidCff();
            }
            count = cff.readI32(at);
            headerSize = 4;
        }
        if (count == 0) {
            return new Index(0, 0, 0, 0, headerSize, 0, 0);
        }
        headerSize++;
        if (dataSize < headerSize) {
            throw new InvalidCff();
        }
        int offsetSize = cff.readU8(at + headerSize - 1);
        long offsetArraySize = ((count & 0xFFFFFFFFL) + 1) * offsetSize;
        long indexSize = headerSize + offsetArraySize;
        if (offsetSize < 1 || offsetSize > 4 || indexSize > dataSize) {
            throw new InvalidCff();
        }
        int offsetsAt = at + headerSize;
        long offset = readOffset(cff, offsetsAt, offsetSize) & 0xFFFFFFFFL;
        if (offset != 1) {
            throw new InvalidCff();
        }
        long maxOffset = Math.min(0xFFFFFFFFL, dataSize - indexSize + OFFSET_ADJUSTMENT);
        for (long entry = 1; entry <= (count & 0xFFFFFFFFL); entry++) {
            long next = readOffset(cff, (int) (offsetsAt + entry * offsetSize), offsetSize) & 0xFFFFFFFFL;
            if (next < offset || next > maxOffset) {
                throw new InvalidCff();
            }
            offset = next;
        }
        int payloadSize = (int) (offset - 1);
        return new Index(count, headerSize, offsetSize, payloadSize, (int) (headerSize + offsetArraySize + payloadSize),
                offsetsAt, (int) (offsetsAt + offsetArraySize));
    }

    private static int offsetAt(OpenTypeTable cff, Index index, int entry) {
        return readOffset(cff, index.offsetsAt() + entry * index.offsetSize(), index.offsetSize()) - OFFSET_ADJUSTMENT;
    }

    private record DictEntry(int op, int count, double[] values) {
    }

    private static final class DictIterator {

        private final OpenTypeTable cff;

        private int cursor;

        private final int end;

        DictIterator(OpenTypeTable cff, int at, int size) {
            this.cff = cff;
            this.cursor = at;
            this.end = at + size;
        }

        boolean hasNext() {
            return cursor != end;
        }

        DictEntry next() throws InvalidCff {
            int count = 0;
            double[] values = new double[DICT_VALUE_CAPACITY];
            while (true) {
                int b0 = cff.readU8(cursor++);
                if (b0 < 22) {
                    if (b0 == ESCAPE) {
                        if (cursor == end) {
                            throw new InvalidCff();
                        }
                        b0 = b0 << 8 | cff.readU8(cursor++);
                    }
                    if (count == 0) {
                        throw new InvalidCff();
                    }
                    return new DictEntry(b0, count, values);
                }
                double value;
                if (b0 == 30) {
                    double[] read = readFloat(cff, cursor, end);
                    value = read[0];
                    cursor += (int) read[1];
                } else if (b0 >= 32 && b0 <= 246) {
                    value = b0 - 139;
                } else if (b0 >= 247 && b0 <= 254) {
                    if (cursor == end) {
                        throw new InvalidCff();
                    }
                    int b1 = cff.readU8(cursor++);
                    value = b0 <= 250 ? (108 - 247 * 256) + (b0 * 256 + b1) : (251 * 256 - 108) - (b0 * 256 + b1);
                } else if (b0 == 28) {
                    cursor += 2;
                    if (cursor > end) {
                        throw new InvalidCff();
                    }
                    value = cff.readI16(cursor - 2);
                } else if (b0 == 29) {
                    cursor += 4;
                    if (cursor > end) {
                        throw new InvalidCff();
                    }
                    value = cff.readI32(cursor - 4);
                } else {
                    throw new InvalidCff();
                }
                if (count == DICT_VALUE_CAPACITY - 1) {
                    throw new InvalidCff();
                }
                values[count++] = value;
            }
        }
    }

    private static double[] readFloat(OpenTypeTable cff, int at, int end) throws InvalidCff {
        int cursor = at;
        int accumulator = 0x100;
        int nibble = 0;
        int flags = 0;
        double value = 0.0;
        int digits = 0;
        int scale = 0;
        int decimalPoint = 0xA;
        int positiveExponent = 0xB;
        int negativeExponent = 0xC;
        int minusSign = 0xE;
        int endOfNumber = 0xF;
        while (true) {
            if ((accumulator & 0x100) != 0) {
                if (cursor == end) {
                    throw new InvalidCff();
                }
                accumulator = cff.readU8(cursor++) << 24 | 0x1;
            }
            nibble = accumulator >>> 28;
            accumulator <<= 4;
            int mask = 1 << nibble;
            if (nibble < 10) {
                if (digits < 15) {
                    value = value * 10.0 + nibble;
                    digits += value != 0.0 ? 1 : 0;
                    if ((flags & 1 << decimalPoint) != 0) {
                        scale--;
                    }
                } else if ((flags & 1 << decimalPoint) == 0) {
                    scale++;
                }
                flags |= mask;
            } else {
                if ((flags & mask) != 0) {
                    throw new InvalidCff();
                }
                flags |= mask;
                if (nibble == minusSign) {
                    if ((flags & (0xFFFF ^ (1 << minusSign))) != 0) {
                        throw new InvalidCff();
                    }
                } else if (nibble != decimalPoint) {
                    break;
                }
            }
        }
        if (nibble == positiveExponent || nibble == negativeExponent) {
            int exponent = 0;
            int exponentDigits = 0;
            boolean isPositive = nibble == positiveExponent;
            while (true) {
                if ((accumulator & 0x100) != 0) {
                    if (cursor == end) {
                        throw new InvalidCff();
                    }
                    accumulator = cff.readU8(cursor++) << 24 | 0x1;
                }
                nibble = accumulator >>> 28;
                accumulator <<= 4;
                if (nibble >= 10) {
                    break;
                }
                if (exponentDigits >= 6) {
                    throw new InvalidCff();
                }
                exponent = exponent * 10 + nibble;
                exponentDigits += exponent != 0 ? 1 : 0;
            }
            scale += isPositive ? exponent : -exponent;
        }
        if (nibble != endOfNumber) {
            throw new InvalidCff();
        }
        if (scale != 0) {
            double power = StrictMath.pow(10.0, Math.abs((double) scale));
            value = scale > 0 ? value * power : value / power;
        }
        return new double[] {(flags & 1 << minusSign) != 0 ? -value : value, cursor - at};
    }

    static Optional<CffOutlines> read(OpenTypeTable cff, int version) {
        try {
            return Optional.of(readOrRefuse(cff, version));
        } catch (InvalidCff | IndexOutOfBoundsException refused) {
            return Optional.empty();
        }
    }

    private static CffOutlines readOrRefuse(OpenTypeTable cff, int version) throws InvalidCff {
        if (!cff.fits(HEADER_SIZE) || version + 1 != cff.readU8(0)) {
            throw new InvalidCff();
        }
        int headerSize = cff.readU8(2);
        long topDictSize = 0;
        if (version == VERSION_1) {
            if (headerSize < 4 || headerSize > cff.size() - 4L) {
                throw new InvalidCff();
            }
            int offsetSize = cff.readU8(3);
            if (offsetSize < 1 || offsetSize > 4) {
                throw new InvalidCff();
            }
        } else {
            if (headerSize < 5 || headerSize > cff.size() - 5L) {
                throw new InvalidCff();
            }
            topDictSize = cff.readU16(3);
        }
        int topDictOffset;
        Index nameIndex = Index.NONE;
        if (version == VERSION_1) {
            nameIndex = readIndex(cff, headerSize, cff.size() - (long) headerSize, version);
            if (nameIndex.count() != 1) {
                throw new InvalidCff();
            }
            topDictOffset = headerSize + nameIndex.totalSize();
            topDictSize = cff.size() - (long) topDictOffset;
        } else {
            topDictOffset = headerSize;
            if (topDictSize > cff.size() - (long) topDictOffset) {
                throw new InvalidCff();
            }
        }
        Index topDictIndex = readIndex(cff, topDictOffset, topDictSize, version);
        if (version == VERSION_1 && nameIndex.count() != topDictIndex.count()) {
            throw new InvalidCff();
        }
        int charStringOffset = 0;
        int privateOffset = 0;
        int privateLength = 0;
        int cidFlags = 0;
        int fdArrayOffset = 0;
        int fdSelectOffset = 0;
        int firstDict = offsetAt(cff, topDictIndex, 0);
        DictIterator top = new DictIterator(cff, topDictIndex.payloadAt() + firstDict,
                offsetAt(cff, topDictIndex, 1) - firstDict);
        while (top.hasNext()) {
            DictEntry entry = top.next();
            switch (entry.op()) {
                case DICT_CHAR_STRINGS -> {
                    if (entry.count() != 1) {
                        throw new InvalidCff();
                    }
                    charStringOffset = asUnsigned32(entry.values()[0]);
                }
                case DICT_PRIVATE -> {
                    if (entry.count() != 2) {
                        throw new InvalidCff();
                    }
                    privateOffset = asUnsigned32(entry.values()[1]);
                    privateLength = asUnsigned32(entry.values()[0]);
                }
                case DICT_ROS -> {
                    if (entry.count() == 3) {
                        cidFlags |= IS_CID;
                    }
                }
                case DICT_FD_ARRAY -> {
                    if (entry.count() == 1) {
                        fdArrayOffset = asUnsigned32(entry.values()[0]);
                        cidFlags |= HAS_FD_ARRAY;
                    }
                }
                case DICT_FD_SELECT -> {
                    if (entry.count() == 1) {
                        fdSelectOffset = asUnsigned32(entry.values()[0]);
                        cidFlags |= HAS_FD_SELECT;
                    }
                }
                default -> {
                }
            }
        }
        int globalOffset;
        if (version == VERSION_1) {
            int stringOffset = topDictOffset + topDictIndex.totalSize();
            Index stringIndex = readIndex(cff, stringOffset, cff.size() - (long) stringOffset, version);
            globalOffset = stringOffset + stringIndex.totalSize();
        } else {
            globalOffset = topDictOffset + topDictIndex.totalSize();
        }
        Index globalIndex = readIndex(cff, globalOffset, cff.size() - (long) globalOffset, version);
        long beginData = (globalOffset & 0xFFFFFFFFL) + globalIndex.totalSize();
        long size = cff.size();
        int localOffset = 0;
        if (privateOffset != 0) {
            long offset = privateOffset & 0xFFFFFFFFL;
            long length = privateLength & 0xFFFFFFFFL;
            if (offset < beginData || offset > size || length > size - offset) {
                throw new InvalidCff();
            }
            if (length != 0) {
                DictIterator privateDict = new DictIterator(cff, privateOffset, privateLength);
                while (privateDict.hasNext()) {
                    DictEntry entry = privateDict.next();
                    if (entry.op() == DICT_SUBRS) {
                        if (entry.count() != 1) {
                            throw new InvalidCff();
                        }
                        localOffset = asUnsigned32(entry.values()[0]);
                    }
                }
            }
        }
        Index localIndex = Index.NONE;
        if (localOffset != 0) {
            long offset = localOffset & 0xFFFFFFFFL;
            if (offset < (privateLength & 0xFFFFFFFFL) || offset > size - (privateOffset & 0xFFFFFFFFL)) {
                throw new InvalidCff();
            }
            localOffset += privateOffset;
            localIndex = readIndex(cff, localOffset, size - (localOffset & 0xFFFFFFFFL), version);
        }
        long charStrings = charStringOffset & 0xFFFFFFFFL;
        if (charStrings < beginData || charStrings >= size) {
            throw new InvalidCff();
        }
        Index charStringIndex = readIndex(cff, charStringOffset, size - charStrings, version);
        List<IndexData> fdSubroutines = new ArrayList<>();
        int fdSelectFormat = 0;
        if ((cidFlags & ALL_CID_FLAGS) == ALL_CID_FLAGS && fdArrayOffset != 0 && fdSelectOffset != 0) {
            long fdArray = fdArrayOffset & 0xFFFFFFFFL;
            long fdSelect = fdSelectOffset & 0xFFFFFFFFL;
            if (fdArray < beginData || fdArray >= size || fdSelect < beginData || fdSelect >= size) {
                throw new InvalidCff();
            }
            Index fdArrayIndex = readIndex(cff, fdArrayOffset, size - fdArray, version);
            for (int fd = 0; fd < fdArrayIndex.count(); fd++) {
                fdSubroutines.add(readFontDict(cff, fdArrayIndex, fd, beginData, version));
            }
            fdSelectFormat = cff.readU8(fdSelectOffset);
            if (fdSelectFormat != 0 && fdSelectFormat != 3) {
                throw new InvalidCff();
            }
        }
        return new CffOutlines(version, cff, IndexData.of(globalOffset, globalIndex, bias(globalIndex.count())),
                IndexData.of(localOffset, localIndex, bias(localIndex.count())),
                IndexData.of(charStringOffset, charStringIndex, 0), fdSelectOffset, fdSelectFormat, fdSubroutines);
    }

    private static IndexData readFontDict(OpenTypeTable cff, Index fdArrayIndex, int fd, long beginData, int version)
            throws InvalidCff {
        int at = fdArrayIndex.offsetsAt() + fd * fdArrayIndex.offsetSize();
        int start = readOffset(cff, at, fdArrayIndex.offsetSize()) - OFFSET_ADJUSTMENT;
        int finish = readOffset(cff, at + fdArrayIndex.offsetSize(), fdArrayIndex.offsetSize()) - OFFSET_ADJUSTMENT;
        long size = cff.size();
        int[][] dicts = {{fdArrayIndex.payloadAt() + start, finish - start}, {0, 0}};
        Index subroutineIndex = Index.NONE;
        int subroutineOffset = 0;
        int subroutineBase = 0;
        for (int[] dict : dicts) {
            DictIterator iterator = new DictIterator(cff, dict[0], dict[1]);
            while (iterator.hasNext()) {
                DictEntry entry = iterator.next();
                if (entry.op() == DICT_PRIVATE) {
                    if (entry.count() != 2) {
                        throw new InvalidCff();
                    }
                    long offset = asUnsigned32(entry.values()[1]) & 0xFFFFFFFFL;
                    long length = asUnsigned32(entry.values()[0]) & 0xFFFFFFFFL;
                    if (offset < beginData || offset > size || length > size - offset) {
                        throw new InvalidCff();
                    }
                    dicts[1][0] = (int) offset;
                    dicts[1][1] = (int) length;
                    subroutineBase = (int) offset;
                } else if (entry.op() == DICT_SUBRS) {
                    if (entry.count() != 1) {
                        throw new InvalidCff();
                    }
                    subroutineOffset = asUnsigned32(entry.values()[0]);
                    if ((subroutineOffset & 0xFFFFFFFFL) > size - (subroutineBase & 0xFFFFFFFFL)) {
                        throw new InvalidCff();
                    }
                    subroutineOffset += subroutineBase;
                    subroutineIndex = readIndex(cff, subroutineOffset, size - (subroutineOffset & 0xFFFFFFFFL), version);
                }
            }
        }
        return IndexData.of(subroutineOffset, subroutineIndex, bias(subroutineIndex.count()));
    }

    private int findFontDict(int glyphId) {
        int fdAt = fdSelectOffset + 1;
        long fdDataSize = table.size() - (long) fdAt;
        int fd = -1;
        if (fdSelectFormat == 0) {
            if (glyphId < fdDataSize) {
                fd = table.readU8(fdAt + glyphId);
            }
        } else if (fdDataSize >= 2) {
            int ranges = table.readU16(fdAt);
            if (fdDataSize >= 2 + ranges * 3L + 2) {
                int rangesAt = fdAt + 2;
                for (int span = ranges; span != 0; span >>>= 1) {
                    int half = rangesAt + (span >>> 1) * 3;
                    int glyphEnd = table.readU16(half + 3);
                    if (glyphId >= glyphEnd) {
                        rangesAt = half + 3;
                        span--;
                        continue;
                    }
                    if (glyphId < table.readU16(half)) {
                        continue;
                    }
                    fd = table.readU8(half + 2);
                    break;
                }
            }
        }
        return fd;
    }

    boolean decode(int glyphId, Blend2DMatrix transform, Blend2DPath out) {
        try {
            return new CharStringRun(transform, out).run(glyphId);
        } catch (InvalidCff | IndexOutOfBoundsException invalid) {
            return false;
        }
    }

    private final class CharStringRun {

        private final double m00;

        private final double m01;

        private final double m10;

        private final double m11;

        private final Blend2DPath out;

        private final double[] values = new double[VALUE_STACK_SIZE + 1];

        private final int[] returnAt = new int[CALL_STACK_SIZE + 1];

        private final int[] returnEnd = new int[CALL_STACK_SIZE + 1];

        private final double[] storage = new double[STORAGE_SIZE + 1];

        private int storageMask;

        private int depth;

        private int count;

        private double px;

        private double py;

        private int ip;

        private int ipEnd;

        private long bytesProcessed;

        private int hintBits;

        private int flags;

        CharStringRun(Blend2DMatrix transform, Blend2DPath out) {
            this.m00 = transform.m00();
            this.m01 = transform.m01();
            this.m10 = transform.m10();
            this.m11 = transform.m11();
            this.out = out;
            this.px = transform.m20();
            this.py = transform.m21();
        }

        private double xByA(double x, double y) {
            return x * m00 + y * m10;
        }

        private double yByA(double x, double y) {
            return x * m01 + y * m11;
        }

        private void enter(IndexData index, int id) throws InvalidCff {
            int offsetSize = index.offsetSize();
            int payloadSize = index.payloadSize();
            int base = index.offset();
            int entryAt = base + index.headerSize() + id * offsetSize;
            int first = readOffset(table, entryAt, offsetSize) - OFFSET_ADJUSTMENT;
            int second = readOffset(table, entryAt + offsetSize, offsetSize) - OFFSET_ADJUSTMENT;
            if (Integer.compareUnsigned(first, second) >= 0 || Integer.compareUnsigned(second, payloadSize) > 0) {
                throw new InvalidCff();
            }
            int payloadAt = base + index.payloadOffset();
            ip = payloadAt + first;
            ipEnd = payloadAt + second;
            long programSize = (second & 0xFFFFFFFFL) - (first & 0xFFFFFFFFL);
            if (PROGRAM_LIMIT - bytesProcessed < programSize) {
                throw new InvalidCff();
            }
            bytesProcessed += programSize;
        }

        private void push(double value) throws InvalidCff {
            if (++count > VALUE_STACK_SIZE) {
                throw new InvalidCff();
            }
            values[count - 1] = value;
        }

        private void moveTo() {
            out.addRaw(Blend2DPath.MOVE, px, py);
        }

        private void lineTo() {
            out.addRaw(Blend2DPath.LINE, px, py);
        }

        private void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
            out.addRaw(Blend2DPath.CUBIC, x1, y1);
            out.addRaw(Blend2DPath.CUBIC, x2, y2);
            out.addRaw(Blend2DPath.LINE, x3, y3);
        }

        private void closeIfOpen() {
            if ((flags & PATH_OPEN) != 0) {
                out.close();
            }
        }

        boolean run(int glyphId) throws InvalidCff {
            if (Integer.compareUnsigned(glyphId, charStrings.entryCount()) >= 0) {
                return false;
            }
            IndexData local = localSubroutines;
            if (fdSelectOffset != 0) {
                int fd = findFontDict(glyphId);
                if (fd >= 0 && fd < fdSubroutines.size()) {
                    local = fdSubroutines.get(fd);
                }
            }
            enter(charStrings, glyphId);
            while (true) {
                if (ip >= ipEnd) {
                    if (depth > 0) {
                        returnFromSubroutine();
                        continue;
                    }
                    break;
                }
                int b0 = table.readU8(ip++);
                if (b0 >= 32) {
                    if (!readNumbers(b0)) {
                        continue;
                    }
                    b0 = table.readU8(ip - 1);
                }
                int result = runOperator(b0, local);
                if (result == END_CHAR) {
                    break;
                }
            }
            if ((flags & PATH_OPEN) != 0) {
                out.close();
            }
            return true;
        }

        private static final int CONTINUE = 0;

        private static final int END_CHAR = 1;

        private boolean readNumbers(int first) throws InvalidCff {
            int b0 = first;
            if (++count > VALUE_STACK_SIZE) {
                throw new InvalidCff();
            }
            if (ip < ipEnd) {
                if (b0 <= 246) {
                    values[count - 1] = b0 - 139;
                    b0 = table.readU8(ip++);
                    if (b0 < 32) {
                        return true;
                    }
                    if (++count > VALUE_STACK_SIZE) {
                        throw new InvalidCff();
                    }
                    if (b0 <= 246) {
                        values[count - 1] = b0 - 139;
                        return false;
                    }
                    if (ip == ipEnd) {
                        throw new InvalidCff();
                    }
                }
                if (b0 <= 254) {
                    int b1 = table.readU8(ip++);
                    values[count - 1] = b0 <= 250 ? (108 - 247 * 256) + (b0 * 256 + b1)
                            : (251 * 256 - 108) - (b0 * 256 + b1);
                } else {
                    ip += 4;
                    if (ip > ipEnd) {
                        throw new InvalidCff();
                    }
                    values[count - 1] = table.readI32(ip - 4) * FROM_F16DOT16;
                }
                return false;
            }
            if (b0 > 246) {
                throw new InvalidCff();
            }
            values[count - 1] = b0 - 139;
            return false;
        }

        private void returnFromSubroutine() {
            depth--;
            ip = returnAt[depth];
            ipEnd = returnEnd[depth];
        }

        private void call(IndexData index) throws InvalidCff {
            returnAt[depth] = ip;
            returnEnd[depth] = ipEnd;
            if (++depth >= CALL_STACK_SIZE) {
                throw new InvalidCff();
            }
            int id = asSigned32(values[--count]) + index.bias();
            if (Integer.compareUnsigned(id, index.entryCount()) >= 0) {
                throw new InvalidCff();
            }
            enter(index, id);
        }

        private int runOperator(int b0, IndexData local) throws InvalidCff {
            int needed = BASE_OPERANDS[b0];
            if (count < needed) {
                if (needed != UNKNOWN) {
                    throw new InvalidCff();
                }
                count = 0;
                return CONTINUE;
            }
            switch (b0) {
                case 0x1C -> {
                    ip += 2;
                    if (ip > ipEnd || ++count > VALUE_STACK_SIZE) {
                        throw new InvalidCff();
                    }
                    values[count - 1] = table.readI16(ip - 2);
                }
                case 0x15 -> {
                    closeIfOpen();
                    px += xByA(values[count - 2], values[count - 1]);
                    py += yByA(values[count - 2], values[count - 1]);
                    moveTo();
                    count = 0;
                    flags |= HAS_WIDTH | PATH_OPEN;
                }
                case 0x16 -> {
                    closeIfOpen();
                    px += values[count - 1] * m00;
                    py += values[count - 1] * m01;
                    moveTo();
                    count = 0;
                    flags |= HAS_WIDTH | PATH_OPEN;
                }
                case 0x04 -> {
                    closeIfOpen();
                    px += values[count - 1] * m10;
                    py += values[count - 1] * m11;
                    moveTo();
                    count = 0;
                    flags |= HAS_WIDTH | PATH_OPEN;
                }
                case 0x05 -> lineToPairs();
                case 0x06, 0x07 -> alternatingLines(b0 == 0x07);
                case 0x08 -> curvesFrom(0, count);
                case 0x1E, 0x1F -> alternatingCurves(b0 == 0x1E);
                case 0x1B -> parallelCurves(true);
                case 0x1A -> parallelCurves(false);
                case 0x18 -> curvesThenLine();
                case 0x19 -> linesThenCurve();
                case 0x01, 0x03, 0x12, 0x17 -> {
                    hintBits += count / 2;
                    count = 0;
                }
                case 0x13, 0x14 -> {
                    hintBits += count / 2;
                    int hintBytes = (hintBits + 7) / 8;
                    if (ipEnd - ip < hintBytes) {
                        throw new InvalidCff();
                    }
                    ip += hintBytes;
                    count = 0;
                    flags |= HAS_WIDTH;
                }
                case 0x0F, 0x10 -> count = 0;
                case 0x0A -> call(local);
                case 0x1D -> call(globalSubroutines);
                case 0x0B -> {
                    if (depth == 0) {
                        throw new InvalidCff();
                    }
                    returnFromSubroutine();
                }
                case 0x0E -> {
                    return END_CHAR;
                }
                case 0x0C -> runEscaped();
                default -> count = 0;
            }
            return CONTINUE;
        }

        private void lineToPairs() {
            int at = 0;
            while ((at += 2) <= count) {
                px += xByA(values[at - 2], values[at - 1]);
                py += yByA(values[at - 2], values[at - 1]);
                lineTo();
            }
            if ((count & 1) != 0) {
                px += values[count - 1] * m00;
                py += values[count - 1] * m01;
                lineTo();
            }
            count = 0;
        }

        private void alternatingLines(boolean startsVertical) {
            boolean isVertical = startsVertical;
            for (int at = 0; at < count; at++) {
                if (isVertical) {
                    px += values[at] * m10;
                    py += values[at] * m11;
                } else {
                    px += values[at] * m00;
                    py += values[at] * m01;
                }
                lineTo();
                isVertical = !isVertical;
            }
            count = 0;
        }

        private void curvesFrom(int start, int end) {
            int at = start;
            while ((at += 6) <= end) {
                double x1 = px + xByA(values[at - 6], values[at - 5]);
                double y1 = py + yByA(values[at - 6], values[at - 5]);
                double x2 = x1 + xByA(values[at - 4], values[at - 3]);
                double y2 = y1 + yByA(values[at - 4], values[at - 3]);
                px = x2 + xByA(values[at - 2], values[at - 1]);
                py = y2 + yByA(values[at - 2], values[at - 1]);
                cubicTo(x1, y1, x2, y2, px, py);
            }
            if (end == count) {
                count = 0;
            }
        }

        private void alternatingCurves(boolean startsVertical) {
            int at = 0;
            boolean isVertical = startsVertical;
            while ((at += 4) <= count) {
                double first = values[at - 4];
                double x1 = px + (isVertical ? first * m10 : first * m00);
                double y1 = py + (isVertical ? first * m11 : first * m01);
                double x2 = x1 + xByA(values[at - 3], values[at - 2]);
                double y2 = y1 + yByA(values[at - 3], values[at - 2]);
                double last = values[at - 1];
                px = x2 + (isVertical ? last * m00 : last * m10);
                py = y2 + (isVertical ? last * m01 : last * m11);
                if (count - at == 1) {
                    double extra = values[at];
                    px += isVertical ? extra * m10 : extra * m00;
                    py += isVertical ? extra * m11 : extra * m01;
                }
                cubicTo(x1, y1, x2, y2, px, py);
                isVertical = !isVertical;
            }
            count = 0;
        }

        private void parallelCurves(boolean isHorizontal) {
            int at = 0;
            if ((count & 1) != 0) {
                px += isHorizontal ? values[0] * m10 : values[0] * m00;
                py += isHorizontal ? values[0] * m11 : values[0] * m01;
                at++;
            }
            while ((at += 4) <= count) {
                double first = values[at - 4];
                double x1 = px + (isHorizontal ? first * m00 : first * m10);
                double y1 = py + (isHorizontal ? first * m01 : first * m11);
                double x2 = x1 + xByA(values[at - 3], values[at - 2]);
                double y2 = y1 + yByA(values[at - 3], values[at - 2]);
                double last = values[at - 1];
                px = x2 + (isHorizontal ? last * m00 : last * m10);
                py = y2 + (isHorizontal ? last * m01 : last * m11);
                cubicTo(x1, y1, x2, y2, px, py);
            }
            count = 0;
        }

        private void curvesThenLine() {
            count -= 2;
            int at = 0;
            while ((at += 6) <= count) {
                double x1 = px + xByA(values[at - 6], values[at - 5]);
                double y1 = py + yByA(values[at - 6], values[at - 5]);
                double x2 = x1 + xByA(values[at - 4], values[at - 3]);
                double y2 = y1 + yByA(values[at - 4], values[at - 3]);
                px = x2 + xByA(values[at - 2], values[at - 1]);
                py = y2 + yByA(values[at - 2], values[at - 1]);
                cubicTo(x1, y1, x2, y2, px, py);
            }
            px += xByA(values[count], values[count + 1]);
            py += yByA(values[count], values[count + 1]);
            lineTo();
            count = 0;
        }

        private void linesThenCurve() {
            count -= 6;
            int at = 0;
            while ((at += 2) <= count) {
                px += xByA(values[at - 2], values[at - 1]);
                py += yByA(values[at - 2], values[at - 1]);
                lineTo();
            }
            double x1 = px + xByA(values[count], values[count + 1]);
            double y1 = py + yByA(values[count], values[count + 1]);
            double x2 = x1 + xByA(values[count + 2], values[count + 3]);
            double y2 = y1 + yByA(values[count + 2], values[count + 3]);
            px = x2 + xByA(values[count + 4], values[count + 5]);
            py = y2 + yByA(values[count + 4], values[count + 5]);
            cubicTo(x1, y1, x2, y2, px, py);
            count = 0;
        }

        private void runEscaped() throws InvalidCff {
            if (ip >= ipEnd) {
                throw new InvalidCff();
            }
            int b0 = table.readU8(ip++);
            if (b0 >= ESCAPED_OPERANDS.length) {
                count = 0;
                return;
            }
            int needed = ESCAPED_OPERANDS[b0];
            if (count < needed) {
                if (needed != UNKNOWN) {
                    throw new InvalidCff();
                }
                count = 0;
                return;
            }
            double[] v = values;
            switch (b0) {
                case 0x23 -> {
                    double x1 = px + xByA(v[0], v[1]);
                    double y1 = py + yByA(v[0], v[1]);
                    double x2 = x1 + xByA(v[2], v[3]);
                    double y2 = y1 + yByA(v[2], v[3]);
                    px = x2 + xByA(v[4], v[5]);
                    py = y2 + yByA(v[4], v[5]);
                    cubicTo(x1, y1, x2, y2, px, py);
                    x1 = px + xByA(v[6], v[7]);
                    y1 = py + yByA(v[6], v[7]);
                    x2 = x1 + xByA(v[8], v[9]);
                    y2 = y1 + yByA(v[8], v[9]);
                    px = x2 + xByA(v[10], v[11]);
                    py = y2 + yByA(v[10], v[11]);
                    cubicTo(x1, y1, x2, y2, px, py);
                    count = 0;
                }
                case 0x25 -> {
                    double x1 = px + xByA(v[0], v[1]);
                    double y1 = py + yByA(v[0], v[1]);
                    double x2 = x1 + xByA(v[2], v[3]);
                    double y2 = y1 + yByA(v[2], v[3]);
                    double x3 = x2 + xByA(v[4], v[5]);
                    double y3 = y2 + yByA(v[4], v[5]);
                    cubicTo(x1, y1, x2, y2, x3, y3);
                    double x4 = x3 + xByA(v[6], v[7]);
                    double y4 = y3 + yByA(v[6], v[7]);
                    double x5 = x4 + xByA(v[8], v[9]);
                    double y5 = y4 + yByA(v[8], v[9]);
                    double dx = Math.abs(v[0] + v[2] + v[4] + v[6] + v[8]);
                    double dy = Math.abs(v[1] + v[3] + v[5] + v[7] + v[9]);
                    if (dx > dy) {
                        px = x5 + v[10] * m00;
                        py = y5 + v[10] * m01;
                    } else {
                        px = x5 + v[10] * m10;
                        py = y5 + v[10] * m11;
                    }
                    cubicTo(x4, y4, x5, y5, px, py);
                    count = 0;
                }
                case 0x22 -> {
                    double x1 = px + v[0] * m00;
                    double y1 = py + v[0] * m01;
                    double x2 = x1 + xByA(v[1], v[2]);
                    double y2 = y1 + yByA(v[1], v[2]);
                    double x3 = x2 + v[3] * m00;
                    double y3 = y2 + v[3] * m01;
                    cubicTo(x1, y1, x2, y2, x3, y3);
                    double x4 = x3 + v[4] * m00;
                    double y4 = y3 + v[4] * m01;
                    double x5 = x4 + xByA(v[5], -v[2]);
                    double y5 = y4 + yByA(v[5], -v[2]);
                    px = x5 + v[6] * m00;
                    py = y5 + v[6] * m01;
                    cubicTo(x4, y4, x5, y5, px, py);
                    count = 0;
                }
                case 0x24 -> {
                    double x1 = px + xByA(v[0], v[1]);
                    double y1 = py + yByA(v[0], v[1]);
                    double x2 = x1 + xByA(v[2], v[3]);
                    double y2 = y1 + yByA(v[2], v[3]);
                    double x3 = x2 + v[4] * m00;
                    double y3 = y2 + v[4] * m01;
                    cubicTo(x1, y1, x2, y2, x3, y3);
                    double x4 = x3 + v[5] * m00;
                    double y4 = y3 + v[5] * m01;
                    double x5 = x4 + xByA(v[6], v[7]);
                    double y5 = y4 + yByA(v[6], v[7]);
                    px = x5 + v[8] * m00;
                    py = y5 + v[8] * m01;
                    cubicTo(x4, y4, x5, y5, px, py);
                    count = 0;
                }
                case 0x03 -> binary((a, b) -> (a != 0.0) & (b != 0.0) ? 1.0 : 0.0, false);
                case 0x04 -> binary((a, b) -> (a != 0.0) | (b != 0.0) ? 1.0 : 0.0, false);
                case 0x0F -> binary((a, b) -> a == b ? 1.0 : 0.0, false);
                case 0x16 -> {
                    v[count - 4] = v[count - 4 + (v[count - 2] <= v[count - 1] ? 1 : 0)];
                    count -= 3;
                }
                case 0x05 -> v[count - 1] = v[count - 1] == 0.0 ? 1.0 : 0.0;
                case 0x0E -> v[count - 1] = -v[count - 1];
                case 0x09 -> v[count - 1] = Math.abs(v[count - 1]);
                case 0x1A -> v[count - 1] = Math.sqrt(Math.max(v[count - 1], 0.0));
                case 0x0A -> binary((a, b) -> a + b, true);
                case 0x0B -> binary((a, b) -> a - b, true);
                case 0x18 -> binary((a, b) -> a * b, true);
                case 0x0C -> binary((a, b) -> a / b, true);
                case 0x17 -> push(0.5);
                case 0x1B -> {
                    if (++count > VALUE_STACK_SIZE) {
                        throw new InvalidCff();
                    }
                    v[count - 1] = v[count - 2];
                }
                case 0x12 -> {
                    if (count == 0) {
                        throw new InvalidCff();
                    }
                    count--;
                }
                case 0x1C -> {
                    double swap = v[count - 2];
                    v[count - 2] = v[count - 1];
                    v[count - 1] = swap;
                }
                case 0x1D -> {
                    double index = v[count - 1];
                    double pushed = 0.0;
                    if (index < 0.0) {
                        pushed = v[count - 2];
                    } else {
                        long read = (count - 1) - (asUnsigned32(index) & 0xFFFFFFFFL);
                        if (read >= 0 && read < count - 1) {
                            pushed = v[(int) read];
                        }
                    }
                    v[count - 1] = pushed;
                }
                case 0x1E -> roll();
                case 0x14 -> {
                    int slot = asSigned32(v[count - 1]);
                    if (Integer.compareUnsigned(slot, STORAGE_SIZE) < 0) {
                        storage[slot] = v[count - 2];
                        storageMask |= 1 << slot;
                    }
                    count -= 2;
                }
                case 0x15 -> {
                    int slot = asSigned32(v[count - 1]);
                    if (Integer.compareUnsigned(slot, STORAGE_SIZE) >= 0 || (storageMask & 1 << slot) == 0) {
                        slot = STORAGE_SIZE;
                    }
                    v[count - 1] = storage[slot];
                }
                default -> count = 0;
            }
        }

        private interface Operation {
            double apply(double a, double b);
        }

        private void binary(Operation operation, boolean mustBeFinite) {
            double result = operation.apply(values[count - 2], values[count - 1]);
            values[count - 2] = mustBeFinite && !Double.isFinite(result) ? 0.0 : result;
            count--;
        }

        private void roll() {
            int shift = asSigned32(values[--count]);
            int amount = asSigned32(values[--count]);
            if (Integer.compareUnsigned(amount, count) > 0) {
                amount = count;
            }
            if (Integer.compareUnsigned(amount, 2) < 0) {
                return;
            }
            if (shift < 0) {
                shift = -Integer.remainderUnsigned(-shift, amount) + amount;
            } else {
                shift = Integer.remainderUnsigned(shift, amount);
            }
            if (shift == 0) {
                return;
            }
            double last = 0;
            int current = -1;
            int base = current;
            for (int step = 0; step < amount; step++) {
                if (current == base) {
                    last = values[++current];
                    base = current;
                }
                current += shift;
                if (Integer.compareUnsigned(current, amount) >= 0) {
                    current -= amount;
                }
                double swap = values[current];
                values[current] = last;
                last = swap;
            }
        }
    }
}
