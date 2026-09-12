package org.jebol.domain.eval;

final class Qoi {

    private Qoi() {
    }

    private static final byte[] MAGIC = {'q', 'o', 'i', 'f'};
    private static final int HEADER_LENGTH = 14;
    private static final byte[] END_MARKER = {0, 0, 0, 0, 0, 0, 0, 1};

    private static final int OP_INDEX = 0x00;
    private static final int OP_DIFF = 0x40;
    private static final int OP_LUMA = 0x80;
    private static final int OP_RUN = 0xC0;
    private static final int OP_RGB = 0xFE;
    private static final int OP_RGBA = 0xFF;
    private static final int TAG_MASK = 0xC0;

    private static final int LONGEST_RUN_BEFORE_A_COUNT_WOULD_SPELL_A_TAG = 62;

    private static final int TABLE_SIZE = 64;

    private static final int CHANNELS = 4;
    private static final int SRGB = 0;

    private record Colour(int first, int second, int third, int alpha) {

        static final Colour TO_BEGIN_WITH = new Colour(0, 0, 0, 255);

        int whereItSitsInTheTable() {
            return (first * 3 + second * 5 + third * 7 + alpha * 11) % TABLE_SIZE;
        }
    }

    static boolean identifies(byte[] bytes) {
        if (bytes.length < MAGIC.length) {
            return false;
        }
        for (int at = 0; at < MAGIC.length; at++) {
            if (bytes[at] != MAGIC[at]) {
                return false;
            }
        }
        return true;
    }

    static Decoded decoded(byte[] bytes) {
        if (!identifies(bytes) || aHeaderWithNothingAfterItIsRefused(bytes)) {
            return null;
        }
        int wide = wholeNumberAt(bytes, 4);
        int high = wholeNumberAt(bytes, 8);
        if (wide <= 0 || high <= 0) {
            return null;
        }
        return new Decoded(wide, high, pixelsIn(bytes, wide * high));
    }

    record Decoded(int wide, int high, byte[] pixels) {
    }

    private static boolean aHeaderWithNothingAfterItIsRefused(byte[] bytes) {
        return bytes.length < HEADER_LENGTH + END_MARKER.length;
    }

    private static byte[] pixelsIn(byte[] bytes, int howMany) {
        byte[] pixels = whatAStreamThatRunsOutEarlyLeavesBehind(howMany);
        Colour[] seen = new Colour[TABLE_SIZE];
        java.util.Arrays.fill(seen, new Colour(0, 0, 0, 0));
        Colour here = Colour.TO_BEGIN_WITH;
        int at = HEADER_LENGTH;
        int run = 0;
        for (int pixel = 0; pixel < howMany; pixel++) {
            if (run > 0) {
                run--;
            } else if (at < bytes.length) {
                int chunk = bytes[at++] & 0xFF;
                if (chunk == OP_RGB && at + 2 < bytes.length) {
                    here = new Colour(bytes[at++] & 0xFF, bytes[at++] & 0xFF,
                            bytes[at++] & 0xFF, here.alpha());
                } else if (chunk == OP_RGBA && at + 3 < bytes.length) {
                    here = new Colour(bytes[at++] & 0xFF, bytes[at++] & 0xFF,
                            bytes[at++] & 0xFF, bytes[at++] & 0xFF);
                } else if ((chunk & TAG_MASK) == OP_INDEX) {
                    here = seen[chunk & 0x3F];
                } else if ((chunk & TAG_MASK) == OP_DIFF) {
                    here = new Colour(
                            (here.first() + ((chunk >> 4) & 0x03) - 2) & 0xFF,
                            (here.second() + ((chunk >> 2) & 0x03) - 2) & 0xFF,
                            (here.third() + (chunk & 0x03) - 2) & 0xFF,
                            here.alpha());
                } else if ((chunk & TAG_MASK) == OP_LUMA && at < bytes.length) {
                    int middle = (chunk & 0x3F) - 32;
                    int both = bytes[at++] & 0xFF;
                    here = new Colour(
                            (here.first() + middle - 8 + ((both >> 4) & 0x0F)) & 0xFF,
                            (here.second() + middle) & 0xFF,
                            (here.third() + middle - 8 + (both & 0x0F)) & 0xFF,
                            here.alpha());
                } else if ((chunk & TAG_MASK) == OP_RUN) {
                    run = chunk & 0x3F;
                }
                seen[here.whereItSitsInTheTable()] = here;
            }
            int into = pixel * CHANNELS;
            pixels[into] = (byte) here.first();
            pixels[into + 1] = (byte) here.second();
            pixels[into + 2] = (byte) here.third();
            pixels[into + 3] = (byte) here.alpha();
        }
        return pixels;
    }

    private static int wholeNumberAt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16
                | (bytes[at + 2] & 0xFF) << 8 | (bytes[at + 3] & 0xFF);
    }

    private static byte[] whatAStreamThatRunsOutEarlyLeavesBehind(int howMany) {
        byte[] opaqueWhite = new byte[howMany * CHANNELS];
        java.util.Arrays.fill(opaqueWhite, (byte) 0xFF);
        return opaqueWhite;
    }

    static byte[] encoded(int wide, int high, byte[] pixels) {
        Written written = new Written(HEADER_LENGTH + wide * high * (CHANNELS + 1)
                + END_MARKER.length);
        written.add(MAGIC);
        written.addWholeNumber(wide);
        written.addWholeNumber(high);
        written.add(CHANNELS);
        written.add(SRGB);
        layTheChunksIn(written, wide * high, pixels);
        written.add(END_MARKER);
        return written.taken();
    }

    private static void layTheChunksIn(Written written, int howMany, byte[] pixels) {
        Colour[] seen = new Colour[TABLE_SIZE];
        java.util.Arrays.fill(seen, new Colour(0, 0, 0, 0));
        Colour before = Colour.TO_BEGIN_WITH;
        int run = 0;
        for (int pixel = 0; pixel < howMany; pixel++) {
            int at = pixel * CHANNELS;
            Colour here = new Colour(pixels[at] & 0xFF, pixels[at + 1] & 0xFF,
                    pixels[at + 2] & 0xFF, pixels[at + 3] & 0xFF);
            if (here.equals(before)) {
                run++;
                if (run == LONGEST_RUN_BEFORE_A_COUNT_WOULD_SPELL_A_TAG
                        || pixel == howMany - 1) {
                    written.add(OP_RUN | (run - 1));
                    run = 0;
                }
                continue;
            }
            if (run > 0) {
                written.add(OP_RUN | (run - 1));
                run = 0;
            }
            int where = here.whereItSitsInTheTable();
            if (seen[where].equals(here)) {
                written.add(OP_INDEX | where);
            } else {
                seen[where] = here;
                writeTheSmallestChunkThatCarriesTheStep(written, before, here);
            }
            before = here;
        }
    }

    private static void writeTheSmallestChunkThatCarriesTheStep(
            Written written, Colour before, Colour here) {

        if (here.alpha() != before.alpha()) {
            written.add(OP_RGBA);
            written.add(here.first());
            written.add(here.second());
            written.add(here.third());
            written.add(here.alpha());
            return;
        }
        int firstStep = signedByteWrappingAsTheFormatStates(
                here.first() - before.first());
        int middleStep = signedByteWrappingAsTheFormatStates(
                here.second() - before.second());
        int thirdStep = signedByteWrappingAsTheFormatStates(
                here.third() - before.third());
        if (fitsInTwoBits(firstStep) && fitsInTwoBits(middleStep)
                && fitsInTwoBits(thirdStep)) {
            written.add(OP_DIFF | (firstStep + 2) << 4
                    | (middleStep + 2) << 2 | (thirdStep + 2));
            return;
        }
        int firstApart = signedByteWrappingAsTheFormatStates(
                firstStep - middleStep);
        int thirdApart = signedByteWrappingAsTheFormatStates(
                thirdStep - middleStep);
        if (middleStep > -33 && middleStep < 32
                && firstApart > -9 && firstApart < 8
                && thirdApart > -9 && thirdApart < 8) {
            written.add(OP_LUMA | (middleStep + 32));
            written.add((firstApart + 8) << 4 | (thirdApart + 8));
            return;
        }
        written.add(OP_RGB);
        written.add(here.first());
        written.add(here.second());
        written.add(here.third());
    }

    private static int signedByteWrappingAsTheFormatStates(int step) {
        int wrapped = step & 0xFF;
        return wrapped > 127 ? wrapped - 256 : wrapped;
    }

    private static boolean fitsInTwoBits(int step) {
        return step > -3 && step < 2;
    }

    private static final class Written {

        private final byte[] held;
        private int used;

        private Written(int room) {
            this.held = new byte[room];
        }

        void add(int octet) {
            held[used++] = (byte) octet;
        }

        void add(byte[] more) {
            System.arraycopy(more, 0, held, used, more.length);
            used += more.length;
        }

        void addWholeNumber(int number) {
            add(number >> 24);
            add(number >> 16);
            add(number >> 8);
            add(number);
        }

        byte[] taken() {
            return java.util.Arrays.copyOf(held, used);
        }
    }
}
