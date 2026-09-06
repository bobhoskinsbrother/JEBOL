package org.jebol.domain.eval;

/**
 * Brotli qualities two to nine.
 *
 * <p>{@code EncodeData} in {@code encode.c}. The input is taken in blocks, each
 * block searched for repeats, and the commands that come out kept until enough
 * have gathered to be worth writing as one meta-block. Waiting pays: a
 * meta-block writes its own codes, so the more symbols share them the less the
 * codes cost each.
 *
 * <p>Three things end the wait. The end of the input, a meta-block that has
 * grown as large as the format allows, and -- for the two qualities that do not
 * split blocks at all -- simply having enough symbols to be going on with.
 *
 * <p>What is written then depends on the quality. Two and three write one code
 * per alphabet. Four and up divide each alphabet into stretches with a code
 * apiece. And if the compressed form comes out longer than the bytes it
 * replaced, all of it is thrown away and the bytes are written as they stand.
 */
final class BrotliGenericEncoder {

    private static final int WINDOW_BITS = 22;
    private static final int LOWEST_QUALITY_HERE = 2;
    private static final int HIGHEST_QUALITY_HERE = 11;
    private static final int LOWEST_QUALITY_THAT_PRICES_EVERYTHING = 10;
    private static final int QUALITY_THAT_FIRST_SPLITS_BLOCKS = 4;
    private static final int QUALITY_THAT_FIRST_MEASURES_ITS_CODES = 3;
    private static final int MOST_SYMBOLS_HELD_BACK_WHEN_NOT_SPLITTING = 0x2FFF;
    private static final int INPUT_LARGE_ENOUGH_FOR_THE_WIDER_HASHES = 1 << 20;
    private static final int MOST_BITS_A_META_BLOCK_MAY_SPAN = 24;

    private final byte[] source;
    private final int quality;
    private final int blockBits;
    private final int howMuchAMetaBlockMayHold;
    private final BrotliRingBuffer ringBuffer;
    private final FindsTheCopies finder;
    private final BrotliBits writer;
    private final BrotliCommand commands = new BrotliCommand(64);
    /**
     * How distances are split between code and extra bits while the copies are
     * being found.
     *
     * <p>Always the plainest setting. The two top levels choose a better one
     * per meta-block and re-code that meta-block's commands under it, but the
     * choice is made on a copy of the settings and does not carry to the next
     * meta-block -- so the search always starts from here.
     */
    private final BrotliDistances distances = BrotliDistances.PLAINEST;

    private final int[] recentDistances = {4, 11, 15, 16, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0};
    private final int[] savedRecentDistances = {4, 11, 15, 16};

    private int inputAt;
    private int lastProcessedAt;
    private int lastFlushAt;
    private int insertLengthNotYetSpokenFor;
    private int howManyLiterals;
    private int previousByte;
    private int theByteBeforeThat;
    private boolean anythingWrittenToTheRingBuffer;
    private boolean theHasherIsReady;

    static byte[] encoded(byte[] source, int quality) {
        if (quality < LOWEST_QUALITY_HERE || quality > HIGHEST_QUALITY_HERE) {
            throw new IllegalArgumentException(
                    "this encoder serves qualities two to nine, not " + quality);
        }
        return new BrotliGenericEncoder(source, quality).run();
    }

    private BrotliGenericEncoder(byte[] source, int quality) {
        this.source = source;
        this.quality = quality;
        this.blockBits = howManyBytesAreReadAtOnce(quality);
        int ringBits = 1 + Math.max(WINDOW_BITS, blockBits);
        this.howMuchAMetaBlockMayHold =
                1 << Math.min(ringBits, MOST_BITS_A_META_BLOCK_MAY_SPAN);
        this.ringBuffer = new BrotliRingBuffer(ringBits, blockBits);
        this.finder = quality >= LOWEST_QUALITY_THAT_PRICES_EVERYTHING
                ? new PriceEverything(quality, source.length)
                : new TakeTheBestMatch(quality,
                        source.length >= INPUT_LARGE_ENOUGH_FOR_THE_WIDER_HASHES);
        this.writer = new BrotliBits(source.length / 2 + 64);
    }

    /**
     * How much input one pass of the reference search takes.
     *
     * <p>{@code ComputeLgBlock}. The two qualities that never split blocks read
     * sixteen kilobytes at a time; quality nine, which splits hardest, reads
     * two hundred and fifty six.
     */
    private static int howManyBytesAreReadAtOnce(int quality) {
        if (quality < QUALITY_THAT_FIRST_SPLITS_BLOCKS) {
            return 14;
        }
        return quality >= 9 ? Math.min(18, WINDOW_BITS) : 16;
    }

    private byte[] run() {
        writer.write(4, ((WINDOW_BITS - 17) << 1) | 1);
        int taken = 0;
        while (true) {
            int roomLeftInTheBlock =
                    (1 << blockBits) - (inputAt - lastProcessedAt);
            int left = source.length - taken;
            if (roomLeftInTheBlock != 0 && left != 0) {
                int howMany = Math.min(roomLeftInTheBlock, left);
                ringBuffer.write(source, taken, howMany);
                inputAt += howMany;
                ringBuffer.clearWhatTheHashesWouldReadPastTheEnd();
                anythingWrittenToTheRingBuffer = true;
                taken += howMany;
                continue;
            }
            boolean isLast = taken == source.length;
            encodeOneBlock(isLast);
            if (isLast) {
                return writer.written();
            }
        }
    }

    private void encodeOneBlock(boolean isLast) {
        int bytes = inputAt - lastProcessedAt;
        if (bytes == 0 && !anythingWrittenToTheRingBuffer) {
            if (isLast) {
                BrotliMetaBlockWriter.writeAnEmptyLastMetaBlock(writer);
            }
            return;
        }

        byte[] data = ringBuffer.data();
        int mask = ringBuffer.mask();
        if (!theHasherIsReady) {
            finder.getReady(data, bytes, lastProcessedAt == 0 && isLast);
            theHasherIsReady = true;
        }
        finder.stitchToPreviousBlock(data, mask, bytes, lastProcessedAt);

        int startedAt = lastProcessedAt;
        if (commands.count() != 0 && insertLengthNotYetSpokenFor == 0) {
            startedAt = extendTheLastCommand(data, mask, startedAt);
            bytes = inputAt - startedAt;
        }

        int[] carried = {insertLengthNotYetSpokenFor};
        long[] literals = {howManyLiterals};
        finder.find(data, mask, startedAt, bytes, recentDistances, carried,
                distances, commands, literals);
        insertLengthNotYetSpokenFor = carried[0];
        howManyLiterals = (int) literals[0];

        if (!isLast && worthWaitingForMoreInput()) {
            lastProcessedAt = inputAt;
            return;
        }

        if (insertLengthNotYetSpokenFor > 0) {
            commands.addInsertOnly(insertLengthNotYetSpokenFor);
            howManyLiterals += insertLengthNotYetSpokenFor;
            insertLengthNotYetSpokenFor = 0;
        }
        if (!isLast && inputAt == lastFlushAt) {
            return;
        }
        writeOneMetaBlock(data, mask, isLast);
    }

    /**
     * The two ways of finding copies, which differ in more than degree.
     *
     * <p>Levels two to nine walk forward and take the best match they trip
     * over. Ten and eleven price every candidate at every position and choose
     * the cheapest run of commands overall. They keep different tables and are
     * asked different questions, so they are two things rather than one thing
     * with a setting.
     */
    private sealed interface FindsTheCopies {

        void getReady(byte[] data, int howMuchInput, boolean theWholeInputAtOnce);

        void stitchToPreviousBlock(byte[] data, int mask, int howManyBytes,
                int position);

        void find(byte[] data, int mask, int startedAt, int bytes,
                int[] recentDistances, int[] insertLengthCarried,
                BrotliDistances distances, BrotliCommand commands,
                long[] howManyLiterals);
    }

    private static final class TakeTheBestMatch implements FindsTheCopies {

        private final int quality;
        private final BrotliHasher hasher;

        TakeTheBestMatch(int quality, boolean theInputIsLarge) {
            this.quality = quality;
            this.hasher = quality < 5
                    ? BrotliQuickHasher.forQuality(quality, theInputIsLarge)
                    : BrotliFullHasher.forQuality(quality, theInputIsLarge);
        }

        @Override
        public void getReady(byte[] data, int howMuchInput,
                boolean theWholeInputAtOnce) {

            hasher.prepareFor(data, howMuchInput, theWholeInputAtOnce);
        }

        @Override
        public void stitchToPreviousBlock(byte[] data, int mask,
                int howManyBytes, int position) {

            hasher.stitchToPreviousBlock(data, mask, howManyBytes, position);
        }

        @Override
        public void find(byte[] data, int mask, int startedAt, int bytes,
                int[] recentDistances, int[] insertLengthCarried,
                BrotliDistances distances, BrotliCommand commands,
                long[] howManyLiterals) {

            BrotliBackwardReferences.Found found =
                    BrotliBackwardReferences.findAll(data, mask, startedAt,
                            bytes, quality, WINDOW_BITS, hasher, recentDistances,
                            insertLengthCarried[0], commands);
            insertLengthCarried[0] = found.insertLengthLeftOver();
            howManyLiterals[0] += found.literalsWrittenIntoCommands();
        }
    }

    private static final class PriceEverything implements FindsTheCopies {

        private final int quality;
        private final BrotliBinaryTreeHasher tree;

        PriceEverything(int quality, int howMuchInput) {
            this.quality = quality;
            this.tree = new BrotliBinaryTreeHasher(WINDOW_BITS, howMuchInput, true);
        }

        @Override
        public void getReady(byte[] data, int howMuchInput,
                boolean theWholeInputAtOnce) {
        }

        @Override
        public void stitchToPreviousBlock(byte[] data, int mask,
                int howManyBytes, int position) {

            tree.stitchToPreviousBlock(data, mask, howManyBytes, position);
        }

        @Override
        public void find(byte[] data, int mask, int startedAt, int bytes,
                int[] recentDistances, int[] insertLengthCarried,
                BrotliDistances distances, BrotliCommand commands,
                long[] howManyLiterals) {

            if (quality == LOWEST_QUALITY_THAT_PRICES_EVERYTHING) {
                BrotliPricedParse.findAllForTen(data, mask, startedAt, bytes,
                        WINDOW_BITS, tree, recentDistances, insertLengthCarried,
                        distances, commands, howManyLiterals);
            } else {
                BrotliPricedParse.findAllForEleven(data, mask, startedAt, bytes,
                        WINDOW_BITS, tree, recentDistances, insertLengthCarried,
                        distances, commands, howManyLiterals);
            }
        }
    }

    private boolean worthWaitingForMoreInput() {
        int gatheredSoFar = inputAt - lastFlushAt;
        boolean theNextBlockWouldStillFit =
                gatheredSoFar + (1 << blockBits) <= howMuchAMetaBlockMayHold;
        boolean enoughToBeGoingOnWith =
                quality < QUALITY_THAT_FIRST_SPLITS_BLOCKS
                        && howManyLiterals + commands.count()
                                >= MOST_SYMBOLS_HELD_BACK_WHEN_NOT_SPLITTING;
        return !enoughToBeGoingOnWith
                && theNextBlockWouldStillFit
                && howManyLiterals < howMuchAMetaBlockMayHold / 8
                && commands.count() < howMuchAMetaBlockMayHold / 8;
    }

    private void writeOneMetaBlock(byte[] data, int mask, boolean isLast) {
        int length = inputAt - lastFlushAt;
        int startedAt = writer.at();
        writeTheMetaBlockOneWayOrAnother(data, mask, length, isLast, startedAt);

        lastFlushAt = inputAt;
        lastProcessedAt = inputAt;
        if (lastFlushAt > 0) {
            previousByte = data[(lastFlushAt - 1) & mask] & 0xFF;
        }
        if (lastFlushAt > 1) {
            theByteBeforeThat = data[(lastFlushAt - 2) & mask] & 0xFF;
        }
        commands.clear();
        howManyLiterals = 0;
        System.arraycopy(recentDistances, 0, savedRecentDistances, 0, 4);
    }

    private void writeTheMetaBlockOneWayOrAnother(byte[] data, int mask,
            int length, boolean isLast, int startedAt) {

        if (length == 0) {
            writer.write(2, 3);
            writer.jumpToByteBoundary();
            return;
        }
        if (!worthCompressing(data, mask, length)) {
            System.arraycopy(savedRecentDistances, 0, recentDistances, 0, 4);
            BrotliMetaBlockWriter.writeUncompressed(data, mask, lastFlushAt,
                    length, isLast, writer);
            return;
        }
        writeTheCompressedForm(data, mask, length, isLast);
        if (theCompressedFormOutgrewTheInput(length, startedAt)) {
            System.arraycopy(savedRecentDistances, 0, recentDistances, 0, 4);
            writer.rewindTo(startedAt);
            BrotliMetaBlockWriter.writeUncompressed(data, mask, lastFlushAt,
                    length, isLast, writer);
        }
    }

    /**
     * Whether to throw away what was just written and store the bytes plainly.
     *
     * <p>The C writes each meta-block into a buffer of its own, starting at
     * whatever few bits were left over from the one before, and weighs the input
     * against how many whole bytes that buffer holds. So the leftover bits count
     * toward this meta-block's size even though they belong to the last one, and
     * a meta-block four bytes larger than its input is kept while one five bytes
     * larger is not.
     */
    private boolean theCompressedFormOutgrewTheInput(int length, int startedAt) {
        int bitsWrittenIntoTheBuffer = writer.at() - startedAt + (startedAt & 7);
        return length + 4 < bitsWrittenIntoTheBuffer >> 3;
    }

    private void writeTheCompressedForm(byte[] data, int mask, int length,
            boolean isLast) {

        if (quality < QUALITY_THAT_FIRST_MEASURES_ITS_CODES) {
            BrotliMetaBlockWriter.writeWithMostlyFixedCodes(data, mask,
                    lastFlushAt, length, isLast, distances, commands, writer);
            return;
        }
        if (quality < QUALITY_THAT_FIRST_SPLITS_BLOCKS) {
            BrotliMetaBlockWriter.writeWithOneCodePerAlphabet(data, mask,
                    lastFlushAt, length, isLast, distances, commands, writer);
            return;
        }
        if (quality >= LOWEST_QUALITY_THAT_PRICES_EVERYTHING) {
            writeTheClusteredForm(data, mask, length, isLast);
            return;
        }
        int[] contextMap = BrotliMetaBlock.contextMapFor(data, mask, lastFlushAt,
                length, quality, source.length);
        BrotliMetaBlockSplit split = BrotliMetaBlock.builtGreedily(data, mask,
                lastFlushAt, previousByte, theByteBeforeThat, contextMap,
                commands, distances);
        BrotliMetaBlockWriter.writeTheFullThing(data, mask, lastFlushAt, length,
                previousByte, theByteBeforeThat, isLast, distances, commands,
                split, contextModeFor(data, mask, length), writer);
    }

    /**
     * How literals are conditioned on the two bytes before them.
     *
     * <p>Below quality ten it is always the UTF-8 table; the top two levels may
     * choose to condition on how big the previous bytes were instead, which
     * suits data that is not text at all.
     */
    private int contextModeFor(byte[] data, int mask, int length) {
        if (quality < LOWEST_QUALITY_THAT_PRICES_EVERYTHING
                || BrotliLiteralCosts.mostlyUtf8(data, lastFlushAt, mask, length)) {
            return BrotliContext.UTF8;
        }
        return BrotliContext.SIGNED;
    }

    private void writeTheClusteredForm(byte[] data, int mask, int length,
            boolean isLast) {

        int contextMode = contextModeFor(data, mask, length);
        BrotliClusteredMetaBlock.Built built = BrotliClusteredMetaBlock.build(
                data, mask, lastFlushAt, previousByte, theByteBeforeThat,
                contextMode, quality, commands, distances);
        BrotliDistances chosenForThisOne = built.distances();
        BrotliClusteredMetaBlock.smoothForRuns(built.split(),
                chosenForThisOne.alphabetSize());
        BrotliMetaBlockWriter.writeTheFullThing(data, mask, lastFlushAt, length,
                previousByte, theByteBeforeThat, isLast, chosenForThisOne,
                commands, built.split(), contextMode, writer);
    }

    /**
     * Whether the bytes look compressible enough to be worth trying.
     *
     * <p>{@code ShouldCompress}. Almost no copies found and almost everything a
     * literal is the shape of incompressible data, and if a sample of one byte
     * in thirteen is close to eight bits of entropy then it really is, so the
     * work of building codes is skipped.
     */
    private boolean worthCompressing(byte[] data, int mask, int length) {
        if (length <= 2) {
            return false;
        }
        if (commands.count() >= (length >> 8) + 2) {
            return true;
        }
        if ((double) howManyLiterals <= 0.99 * (double) length) {
            return true;
        }
        int everyNth = 13;
        int[] sample = new int[256];
        int howManySampled = (length + everyNth - 1) / everyNth;
        int at = lastFlushAt;
        for (int taken = 0; taken < howManySampled; taken++) {
            sample[data[at & mask] & 0xFF]++;
            at += everyNth;
        }
        double asMuchEntropyAsIsWorthIt =
                (double) length * 7.92 / (double) everyNth;
        return BrotliCodes.bitsEntropy(sample, 256) <= asMuchEntropyAsIsWorthIt;
    }

    /**
     * Grows the previous command's copy if the bytes just read carry on where it
     * left off.
     *
     * <p>{@code ExtendLastCommand}. A copy that ran up against the end of a block
     * can only be continued once the next block has arrived, and continuing it is
     * much cheaper than starting a fresh command.
     */
    private int extendTheLastCommand(byte[] data, int mask, int startedAt) {
        int which = commands.count() - 1;
        int copyLength = commands.copyLengthAt(which);
        long furthestBack = BrotliBackwardReferences.furthestBack(WINDOW_BITS);
        long asFarAsThisCopyCouldReach =
                Math.min(lastProcessedAt - (long) copyLength, furthestBack);
        long distance = Integer.toUnsignedLong(recentDistances[0]);
        int distanceCode = commands.restoredDistanceCodeAt(which,
                distances.directCodes(), distances.postfixBits());

        boolean thisCopyUsedThatDistance =
                distanceCode < BrotliCommand.DISTANCE_SHORT_CODES
                        || distanceCode - (BrotliCommand.DISTANCE_SHORT_CODES - 1)
                                == distance;
        if (!thisCopyUsedThatDistance || distance > asFarAsThisCopyCouldReach) {
            return startedAt;
        }
        int at = startedAt;
        while (at < inputAt
                && data[at & mask] == data[(int) ((at - distance) & mask)]) {
            commands.copyLengthGrows(which);
            at++;
        }
        commands.commandPrefixIs(which, BrotliCommand.lengthCode(
                commands.insertLengthAt(which),
                commands.copyLengthPlusItsPlainModifier(which),
                (commands.distancePrefixAt(which) & 0x3FF) == 0));
        return at;
    }
}
