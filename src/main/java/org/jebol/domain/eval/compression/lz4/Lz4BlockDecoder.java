package org.jebol.domain.eval.compression.lz4;

import java.util.Arrays;

final class Lz4BlockDecoder {

    static final int REFUSED = -1;

    private static final int RUN_MASK = 15;
    private static final int MATCH_LENGTH_MASK = 15;
    private static final int MINIMUM_MATCH = 4;
    private static final int LAST_LITERALS = 5;
    private static final int MATCH_FIND_LIMIT = 12;
    private static final int FAST_LOOP_SAFE_DISTANCE = 64;
    private static final int SHORTCUT_LITERALS = 14;
    private static final int SHORTCUT_MATCH = 18;
    private static final int OFFSET_LENGTH = 2;
    private static final int FIRST_ALLOCATION = 64 * 1024;
    private static final long LARGEST_ROOM = Integer.MAX_VALUE;

    private final byte[] input;
    private final int inputEnd;
    private byte[] output;
    private final long outputEnd;
    private int at;
    private int written;
    private int token;
    private long length;
    private int offset;

    private Lz4BlockDecoder(byte[] input, long room) {
        this.input = input;
        inputEnd = input.length;
        outputEnd = room;
        output = new byte[(int) Math.min(room, FIRST_ALLOCATION)];
    }

    static byte[] decode(byte[] input, long room) {
        Lz4BlockDecoder decoder = new Lz4BlockDecoder(input, room);
        int made = decoder.decode();
        return made <= 0 ? null : Arrays.copyOf(decoder.output, made);
    }

    private enum Step { FAST_TOKEN, SAFE_TOKEN, SAFE_LITERAL_COPY, COPY_MATCH, SAFE_MATCH_COPY, FINISHED }

    private int decode() {
        if (outputEnd > LARGEST_ROOM) {
            return REFUSED;
        }
        if (outputEnd == 0) {
            return inputEnd == 1 && input[0] == 0 ? 0 : REFUSED;
        }
        if (inputEnd == 0) {
            return REFUSED;
        }
        Step step = outputEnd < FAST_LOOP_SAFE_DISTANCE ? Step.SAFE_TOKEN : Step.FAST_TOKEN;
        while (step != Step.FINISHED) {
            step = switch (step) {
                case FAST_TOKEN -> decodeFast();
                case SAFE_TOKEN -> decodeSafeToken();
                case SAFE_LITERAL_COPY -> copyLiteralsSafely();
                case COPY_MATCH -> readMatchLength();
                default -> copyMatchSafely();
            };
            if (written < 0) {
                return REFUSED;
            }
        }
        return written;
    }

    private Step refuse() {
        written = REFUSED;
        return Step.FINISHED;
    }

    private Step decodeFast() {
        token = input[at++] & 0xFF;
        length = token >>> 4;
        if (length == RUN_MASK) {
            long more = readVariableLength(inputEnd - RUN_MASK, true);
            if (more < 0) {
                return refuse();
            }
            length += more;
            if (written + length > outputEnd - 32 || at + length > inputEnd - 32) {
                return Step.SAFE_LITERAL_COPY;
            }
            copyLiterals();
        } else if (at <= inputEnd - (16 + 1)) {
            copyLiterals();
        } else {
            return Step.SAFE_LITERAL_COPY;
        }
        readOffset();
        length = token & MATCH_LENGTH_MASK;
        if (length == MATCH_LENGTH_MASK) {
            long more = readVariableLength(inputEnd - LAST_LITERALS + 1, false);
            if (more < 0) {
                return refuse();
            }
            length += more + MINIMUM_MATCH;
            if (written + length >= outputEnd - FAST_LOOP_SAFE_DISTANCE) {
                return Step.SAFE_MATCH_COPY;
            }
        } else {
            length += MINIMUM_MATCH;
            if (written + length >= outputEnd - FAST_LOOP_SAFE_DISTANCE) {
                return Step.SAFE_MATCH_COPY;
            }
        }
        if (offset > written) {
            return refuse();
        }
        copyMatch();
        return Step.FAST_TOKEN;
    }

    private Step decodeSafeToken() {
        token = input[at++] & 0xFF;
        length = token >>> 4;
        if (length != RUN_MASK
                && at < inputEnd - SHORTCUT_LITERALS - OFFSET_LENGTH
                && written <= outputEnd - SHORTCUT_LITERALS - SHORTCUT_MATCH) {
            copyLiterals();
            length = token & MATCH_LENGTH_MASK;
            readOffset();
            if (length != MATCH_LENGTH_MASK && offset >= 8 && offset <= written) {
                length += MINIMUM_MATCH;
                copyMatch();
                return Step.SAFE_TOKEN;
            }
            return Step.COPY_MATCH;
        }
        if (length == RUN_MASK) {
            long more = readVariableLength(inputEnd - RUN_MASK, true);
            if (more < 0) {
                return refuse();
            }
            length += more;
        }
        return Step.SAFE_LITERAL_COPY;
    }

    private Step copyLiteralsSafely() {
        long end = written + length;
        if (end > outputEnd - MATCH_FIND_LIMIT || at + length > inputEnd - (OFFSET_LENGTH + 1 + LAST_LITERALS)) {
            if (at + length != inputEnd || end > outputEnd) {
                return refuse();
            }
            copyLiterals();
            return Step.FINISHED;
        }
        copyLiterals();
        readOffset();
        length = token & MATCH_LENGTH_MASK;
        return Step.COPY_MATCH;
    }

    private Step readMatchLength() {
        if (length == MATCH_LENGTH_MASK) {
            long more = readVariableLength(inputEnd - LAST_LITERALS + 1, false);
            if (more < 0) {
                return refuse();
            }
            length += more;
        }
        length += MINIMUM_MATCH;
        return Step.SAFE_MATCH_COPY;
    }

    private Step copyMatchSafely() {
        if (offset > written) {
            return refuse();
        }
        if (written + length > outputEnd - LAST_LITERALS) {
            return refuse();
        }
        copyMatch();
        return Step.SAFE_TOKEN;
    }

    private long readVariableLength(int limit, boolean checkFirst) {
        if (checkFirst && at >= limit) {
            return REFUSED;
        }
        int octet = input[at++] & 0xFF;
        long total = octet;
        if (at > limit) {
            return REFUSED;
        }
        while (octet == 255) {
            octet = input[at++] & 0xFF;
            total += octet;
            if (at > limit) {
                return REFUSED;
            }
        }
        return total;
    }

    private void readOffset() {
        offset = Lz4Octets.read16(input, at);
        at += OFFSET_LENGTH;
    }

    private void copyLiterals() {
        int count = (int) length;
        makeRoomFor(count);
        System.arraycopy(input, at, output, written, count);
        at += count;
        written += count;
    }

    private void copyMatch() {
        int count = (int) length;
        makeRoomFor(count);
        if (offset == 0) {
            Arrays.fill(output, written, written + count, (byte) 0);
        } else {
            for (int each = 0; each < count; each++) {
                output[written + each] = output[written - offset + each];
            }
        }
        written += count;
    }

    private void makeRoomFor(int count) {
        long needed = (long) written + count;
        if (needed > output.length) {
            long grown = Math.max(needed, 2L * output.length);
            output = Arrays.copyOf(output, (int) Math.min(grown, Math.min(outputEnd, Integer.MAX_VALUE - 8)));
        }
    }
}
