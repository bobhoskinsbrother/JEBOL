package org.jebol.domain.eval.blend2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class OpenTypeKerning {

    private static final int TABLE_SIZE = 4;

    private static final int WINDOWS_GROUP_HEADER = 6;

    private static final int MAC_GROUP_HEADER = 8;

    private static final int FORMAT_0_SIZE = 8;

    private static final int PAIR_SIZE = 6;

    private static final int PACKED_FLAG_BITS = 0x0F;

    private static final int FORMAT_3_SIZE = 6;

    private static final int[] SMALLEST_SUB_TABLE = {FORMAT_0_SIZE, 10, 8 + 6 + 2, FORMAT_3_SIZE};

    private static final int COVERAGE_HORIZONTAL = 0x01;

    private static final int COVERAGE_RESERVED = 0xF0;

    private static final int MAC_VERTICAL = 0x80;

    private static final int MAC_CROSS_STREAM = 0x40;

    private static final int CROSS_STREAM = 0x04;

    private static final int FLAG_MINIMUM = 0x02;

    private static final int FLAG_OVERRIDE = 0x08;

    private static final int GROUP_FLAGS = FLAG_MINIMUM | CROSS_STREAM | FLAG_OVERRIDE;

    private static final int MOST_GROUPS = 8;

    private static final int FEWEST_PAIRS_IN_RUN = 32;

    private static final int MASK_OVERRIDE = 0x0;

    private static final int MASK_MINIMUM = 0x1;

    private static final int MASK_COMBINE = -1;

    private record Group(int format, int flags, int offset, int size, long[] pairs, int[] values) {
    }

    private final OpenTypeTable table;

    private final List<Group> horizontal;

    private OpenTypeKerning(OpenTypeTable table, List<Group> horizontal) {
        this.table = table;
        this.horizontal = horizontal;
    }

    boolean isAvailable() {
        return !horizontal.isEmpty();
    }

    static Optional<OpenTypeKerning> read(OpenTypeFace face, OpenTypeTable kern) {
        OpenTypeKerning none = new OpenTypeKerning(kern, List.of());
        if (kern.isEmpty()) {
            return Optional.of(none);
        }
        if (!kern.fits(TABLE_SIZE)) {
            face.diagFlags |= OpenTypeFace.DIAG_WRONG_KERN_DATA;
            return Optional.of(none);
        }
        int majorVersion = kern.readU16(0);
        boolean isWindows;
        int headerSize;
        long groupCount;
        int cursor;
        if (majorVersion == 0) {
            isWindows = true;
            headerSize = WINDOWS_GROUP_HEADER;
            groupCount = kern.readU16(2);
            if (groupCount == 0) {
                return Optional.of(none);
            }
            cursor = 4;
        } else if (majorVersion == 1) {
            if (kern.readU16(2) != 0 || kern.size() < 8) {
                face.diagFlags |= OpenTypeFace.DIAG_WRONG_KERN_DATA;
                return Optional.of(none);
            }
            isWindows = false;
            headerSize = MAC_GROUP_HEADER;
            groupCount = kern.readU32(4);
            if (groupCount == 0) {
                return Optional.of(none);
            }
            cursor = 8;
        } else {
            face.diagFlags |= OpenTypeFace.DIAG_WRONG_KERN_DATA;
            return Optional.of(none);
        }
        List<Group> horizontal = new ArrayList<>();
        long groupIndex = 0;
        do {
            int remaining = kern.size() - cursor;
            if (remaining < headerSize) {
                break;
            }
            int length;
            int format;
            int coverage = 0;
            if (isWindows) {
                format = kern.readU8(cursor + 4);
                length = kern.readU16(cursor + 2);
                if (length == kern.size() && groupCount == 1) {
                    length = remaining;
                }
                if (length != remaining && groupIndex == groupCount - 1) {
                    length = remaining;
                }
                coverage = kern.readU8(cursor + 5) & ~COVERAGE_RESERVED;
            } else {
                length = (int) kern.readU32(cursor);
                int macCoverage = kern.readU8(cursor + 4);
                format = kern.readU8(cursor + 5);
                if ((macCoverage & MAC_VERTICAL) == 0) {
                    coverage |= COVERAGE_HORIZONTAL;
                }
                if ((macCoverage & MAC_CROSS_STREAM) != 0) {
                    coverage |= CROSS_STREAM;
                }
            }
            if (Integer.compareUnsigned(length, headerSize) < 0 || Integer.compareUnsigned(length, remaining) > 0) {
                face.diagFlags |= OpenTypeFace.DIAG_WRONG_KERN_DATA;
                return Optional.of(none);
            }
            cursor += headerSize;
            length -= headerSize;
            int offset = cursor;
            boolean isHorizontal = (coverage & COVERAGE_HORIZONTAL) != 0;
            int groupFlags = coverage & GROUP_FLAGS;
            List<Group> collection = isHorizontal ? horizontal : new ArrayList<>();
            if (format < SMALLEST_SUB_TABLE.length && length >= SMALLEST_SUB_TABLE[format]) {
                switch (format) {
                    case 0 -> readFormat0(face, kern, collection, offset, length, groupFlags);
                    case 2 -> readFormat2(kern, collection, offset, length, headerSize, groupFlags);
                    case 3 -> readFormat3(kern, collection, offset, length, headerSize, groupFlags);
                    default -> face.diagFlags |= OpenTypeFace.DIAG_WRONG_KERN_DATA;
                }
            }
            cursor += length;
        } while (++groupIndex < groupCount);
        if (horizontal.isEmpty()) {
            return Optional.of(none);
        }
        face.faceFlags |= OpenTypeFace.FLAG_HORIZONTAL_KERNING;
        return Optional.of(new OpenTypeKerning(kern, horizontal));
    }

    private static long combinedAt(OpenTypeTable kern, int at) {
        return kern.readU32(at);
    }

    private static int checkPairs(OpenTypeTable kern, int pairsAt, int pairCount, int start) {
        if (start >= pairCount) {
            return pairCount;
        }
        long previous = combinedAt(kern, pairsAt + start * PAIR_SIZE);
        int at;
        for (at = start; at < pairCount; at++) {
            long pair = combinedAt(kern, pairsAt + at * PAIR_SIZE);
            if (previous > pair) {
                break;
            }
            previous = pair;
        }
        return at;
    }

    private static void readFormat0(OpenTypeFace face, OpenTypeTable kern, List<Group> collection, int offset,
                                    int length, int groupFlags) {
        int pairCount = kern.readU16(offset);
        if (pairCount == 0) {
            return;
        }
        int pairsAt = offset + FORMAT_0_SIZE;
        if (pairCount * PAIR_SIZE + FORMAT_0_SIZE > length) {
            face.diagFlags |= OpenTypeFace.DIAG_FIXED_KERN_DATA;
            pairCount = (length - FORMAT_0_SIZE) / PAIR_SIZE;
        }
        int unsorted = checkPairs(kern, pairsAt, pairCount, 0);
        if (unsorted == pairCount) {
            collection.add(new Group(0, groupFlags, pairsAt, pairCount, null, null));
            return;
        }
        fixUnsortedPairs(kern, collection, pairsAt, pairCount, unsorted, groupFlags);
        face.diagFlags |= OpenTypeFace.DIAG_FIXED_KERN_DATA;
    }

    private static void fixUnsortedPairs(OpenTypeTable kern, List<Group> collection, int pairsAt, int pairCount,
                                         int current, int groupFlags) {
        int rangeStart = 0;
        int unsortedStart = 0;
        int threshold = Math.max(pairCount / MOST_GROUPS, FEWEST_PAIRS_IN_RUN);
        List<int[]> unsortedRanges = new ArrayList<>();
        int unsortedSum = 0;
        int index = current;
        while (true) {
            int rangeLength = index - rangeStart;
            if (rangeLength >= threshold) {
                if (rangeStart != unsortedStart) {
                    unsortedRanges.add(new int[] {unsortedStart, rangeStart});
                    unsortedSum += rangeStart - unsortedStart;
                }
                unsortedStart = index;
                collection.add(new Group(0, groupFlags, pairsAt + rangeStart * PAIR_SIZE, rangeLength, null, null));
            }
            rangeStart = index;
            if (index == pairCount) {
                break;
            }
            index = checkPairs(kern, pairsAt, pairCount, index);
        }
        if (unsortedStart != pairCount) {
            unsortedRanges.add(new int[] {unsortedStart, rangeStart});
            unsortedSum += pairCount - unsortedStart;
        }
        if (unsortedSum == 0) {
            return;
        }
        long[][] synthesized = new long[unsortedSum][];
        int written = 0;
        for (int[] range : unsortedRanges) {
            for (int at = range[0]; at < range[1]; at++) {
                int pairAt = pairsAt + at * PAIR_SIZE;
                synthesized[written++] = new long[] {combinedAt(kern, pairAt), kern.readI16(pairAt + 4)};
            }
        }
        Arrays.sort(synthesized, 0, written, (a, b) -> Long.compare(a[0], b[0]));
        long[] pairs = new long[unsortedSum];
        int[] values = new int[unsortedSum];
        for (int at = 0; at < written; at++) {
            pairs[at] = synthesized[at][0];
            values[at] = (int) synthesized[at][1];
        }
        collection.add(new Group(0, groupFlags, 0, unsortedSum, pairs, values));
    }

    private static void readFormat2(OpenTypeTable kern, List<Group> collection, int offset, int length,
                                    int headerSize, int groupFlags) {
        int subTableAt = offset - headerSize;
        int subTableSize = length + headerSize;
        int leftClassAt = kern.readU16(offset + 2);
        int rightClassAt = kern.readU16(offset + 4);
        int kerningArrayAt = kern.readU16(offset + 6);
        if (leftClassAt > subTableSize - 6 || rightClassAt > subTableSize - 6 || kerningArrayAt > subTableSize - 2) {
            return;
        }
        int leftCount = kern.readU16(subTableAt + leftClassAt + 2);
        int rightCount = kern.readU16(subTableAt + rightClassAt + 2);
        if (leftClassAt + 4 + leftCount * 2 > subTableSize || rightClassAt + 4 + rightCount * 2 > subTableSize) {
            return;
        }
        collection.add(new Group(2, groupFlags, subTableAt, subTableSize, null, null));
    }

    private static void readFormat3(OpenTypeTable kern, List<Group> collection, int offset, int length,
                                    int headerSize, int groupFlags) {
        int subTableSize = length + headerSize;
        int glyphCount = kern.readU16(offset);
        int valueCount = kern.readU8(offset + 2);
        int leftClassCount = kern.readU8(offset + 3);
        int rightClassCount = kern.readU8(offset + 4);
        int required = headerSize + FORMAT_3_SIZE + valueCount * 2 + glyphCount * 2 + leftClassCount * rightClassCount;
        if (required > subTableSize) {
            return;
        }
        collection.add(new Group(3, groupFlags, offset - headerSize, subTableSize, null, null));
    }

    private static int maskOf(int flags) {
        if ((flags & FLAG_OVERRIDE) != 0) {
            return MASK_OVERRIDE;
        }
        return (flags & FLAG_MINIMUM) != 0 ? MASK_MINIMUM : MASK_COMBINE;
    }

    private static int combine(int original, int value, int mask) {
        return mask == MASK_MINIMUM ? Math.min(original, value) : (original & mask) + value;
    }

    void apply(int[] glyphs, int[][] placements) {
        if (glyphs.length < 2) {
            return;
        }
        int allCombined = 0;
        for (Group group : horizontal) {
            allCombined |= applyAsPairs(group, glyphs, placements, maskOf(packFlags(group)));
        }
        if (allCombined != 0) {
            for (int at = 1; at < glyphs.length; at++) {
                placements[at - 1][2] += placements[at][0];
                placements[at - 1][3] += placements[at][1];
                placements[at][0] = 0;
                placements[at][1] = 0;
            }
        }
    }

    private long pairAt(Group group, int index) {
        return group.pairs() != null ? group.pairs()[index] : combinedAt(table, group.offset() + index * PAIR_SIZE);
    }

    private int valueAt(Group group, int index) {
        return group.values() != null ? group.values()[index] : table.readI16(group.offset() + index * PAIR_SIZE + 4);
    }

    private int findPair(Group group, long pair) {
        int size = group.size();
        if (size == 0) {
            return -1;
        }
        int base = 0;
        for (int half = size / 2; half != 0; half = size / 2) {
            int middle = base + half;
            size -= half;
            if (pairAt(group, middle) <= pair) {
                base = middle;
            }
        }
        return pairAt(group, base) == pair ? base : -1;
    }

    private static int packFlags(Group group) {
        return (group.flags() | group.format() << 2) & PACKED_FLAG_BITS;
    }

    private int applyAsPairs(Group group, int[] glyphs, int[][] placements, int mask) {
        int allCombined = 0;
        long pair = (glyphs[0] & 0xFFFFL) << 16;
        for (int at = 1; at < glyphs.length; at++, pair = (pair << 16) & 0xFFFFFFFFL) {
            pair = (pair | glyphs[at]) & 0xFFFFFFFFL;
            int index = findPair(group, pair);
            if (index < 0) {
                continue;
            }
            int combined = combine(placements[at][0], valueAt(group, index), mask);
            placements[at][0] = combined;
            allCombined |= combined;
        }
        return allCombined;
    }

}
