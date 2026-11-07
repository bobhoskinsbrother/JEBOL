package org.jebol.domain.eval.compression.brotli;

import java.util.Arrays;

final class BrotliDecoder {

    private BrotliDecoder() {
    }

    private static final int LITERAL_SYMBOLS = 256;
    private static final int COMMAND_SYMBOLS = 704;
    private static final int BLOCK_LENGTH_SYMBOLS = 26;
    private static final int DISTANCE_SHORT_CODES = 16;
    private static final int MAX_DISTANCE_BITS = 24;
    private static final int LITERAL_CONTEXT_BITS = 6;
    private static final int DISTANCE_CONTEXT_BITS = 2;
    private static final int MAX_CODE_LENGTH = 15;
    private static final int CODE_LENGTH_CODES = 18;
    private static final int REPEAT_PREVIOUS_CODE_LENGTH = 16;
    private static final int INITIAL_REPEATED_CODE_LENGTH = 8;
    private static final int MAX_ALLOWED_DISTANCE = 0x7FFFFFFC;
    private static final int WINDOW_GAP = 16;

    private static final int[] CODE_LENGTH_ORDER =
            {1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final int[] CODE_LENGTH_PREFIX_LENGTH =
            {2, 2, 2, 3, 2, 2, 2, 4, 2, 2, 2, 3, 2, 2, 2, 4};
    private static final int[] CODE_LENGTH_PREFIX_VALUE =
            {0, 4, 3, 2, 0, 4, 3, 1, 0, 4, 3, 2, 0, 4, 3, 5};

    private static final int[] BLOCK_LENGTH_OFFSET = {
            1, 5, 9, 13, 17, 25, 33, 41, 49, 65, 81, 97, 113,
            145, 177, 209, 241, 305, 369, 497, 753, 1265, 2289, 4337, 8433, 16625,
    };
    private static final int[] BLOCK_LENGTH_EXTRA_BITS = {
            2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5,
            5, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 13, 24,
    };

    private static final int[] INSERT_LENGTH_EXTRA_BITS =
            {0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 24};
    private static final int[] COPY_LENGTH_EXTRA_BITS =
            {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24};
    private static final int[] CELL_POSITION = {0, 1, 0, 1, 8, 9, 2, 16, 10, 17, 18};

    private static final int[] INSERT_LENGTH_OFFSET = lengthOffsets(0, INSERT_LENGTH_EXTRA_BITS);
    private static final int[] COPY_LENGTH_OFFSET = lengthOffsets(2, COPY_LENGTH_EXTRA_BITS);

    private static int[] lengthOffsets(int first, int[] extraBits) {
        int[] offsets = new int[extraBits.length];
        offsets[0] = first;
        for (int each = 0; each + 1 < offsets.length; each++) {
            offsets[each + 1] = offsets[each] + (1 << extraBits[each]);
        }
        return offsets;
    }

    private record Command(
            int insertExtraBits,
            int copyExtraBits,
            boolean distanceIsWritten,
            int distanceContext,
            int insertOffset,
            int copyOffset) {
    }

    private static final Command[] COMMANDS = makeCommandTable();

    private static Command[] makeCommandTable() {
        Command[] table = new Command[COMMAND_SYMBOLS];
        for (int symbol = 0; symbol < COMMAND_SYMBOLS; symbol++) {
            int cell = symbol >> 6;
            int position = CELL_POSITION[cell];
            int copyCode = ((position << 3) & 0x18) + (symbol & 0x7);
            int copyOffset = COPY_LENGTH_OFFSET[copyCode];
            int insertCode = (position & 0x18) + ((symbol >> 3) & 0x7);
            table[symbol] = new Command(
                    INSERT_LENGTH_EXTRA_BITS[insertCode],
                    COPY_LENGTH_EXTRA_BITS[copyCode],
                    cell >= 2,
                    copyOffset > 4 ? 3 : copyOffset - 2,
                    INSERT_LENGTH_OFFSET[insertCode],
                    copyOffset);
        }
        return table;
    }


    static final int NO_LIMIT = -1;

    enum Progress { NEEDS_MORE_INPUT, FINISHED, STOPPED_AT_LIMIT }

    private enum Phase { STREAM_HEADER, META_BLOCK_START, UNCOMPRESSED, METADATA, COMMANDS, PADDING, DONE }

    private enum CommandStep { COMMAND, LITERALS, DISTANCE }

    private static final class RanOut extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private RanOut() {
            super(null, null, false, false);
        }
    }

    private static final RanOut RAN_OUT = new RanOut();
    private static final int BLOCK_SIZE_CAP = 1 << 24;

    static byte[] decodeUpToLimit(byte[] input, int limit) {
        Stream stream = new Stream(input, limit);
        if (stream.run() == Progress.NEEDS_MORE_INPUT) {
            throw new IllegalArgumentException("Brotli data ends part way through");
        }
        return stream.wholeOutput();
    }

    static final class Stream {

        private final BitsLeastSignificantOfEachByteFirst bits;
        private final int limit;
        private byte[] out = new byte[1 << 12];
        private int outAt;
        private int givenUpTo;
        private long dropped;

        private Phase phase = Phase.STREAM_HEADER;
        private long unitStart;
        private boolean lastMetaBlock;
        private int remaining;

        private int windowBits;
        private int maxBackwardDistance;

        private final int[] recentDistances = {16, 15, 11, 4};
        private int recentDistanceAt = 0;

        private BlockSwitcher literals;
        private BlockSwitcher commands;
        private BlockSwitcher distances;
        private int[] contextModes;
        private ContextMap literalMap;
        private ContextMap distanceMap;
        private Huffman[] literalCodes;
        private Huffman[] commandCodes;
        private Huffman[] distanceCodes;
        private int[] distanceExtraBits;
        private int[] distanceOffset;
        private int postfixBits;
        private int contextMode;
        private int literalContextBase;
        private int distanceContextBase;
        private Huffman commandCode;

        private CommandStep commandStep = CommandStep.COMMAND;
        private Command command;
        private int insertLeft;
        private int copyLength;

        private Stream(byte[] input, int limit) {
            this.bits = new BitsLeastSignificantOfEachByteFirst(input);
            this.limit = limit;
        }

        Stream() {
            this(new byte[0], NO_LIMIT);
        }

        void give(byte[] input) {
            bits.append(input);
        }

        boolean isFinished() {
            return phase == Phase.DONE;
        }

        byte[] wholeOutput() {
            return outAt == out.length ? out : Arrays.copyOf(out, outAt);
        }

        byte[] takeNewOutput() {
            byte[] fresh = Arrays.copyOfRange(out, givenUpTo, outAt);
            givenUpTo = outAt;
            dropOutputPastWindow();
            return fresh;
        }

        private void dropOutputPastWindow() {
            int window = 1 << windowBits;
            if (outAt <= 2 * window) {
                return;
            }
            int dropping = outAt - window;
            System.arraycopy(out, dropping, out, 0, window);
            outAt = window;
            givenUpTo = window;
            dropped += dropping;
        }

        Progress run() {
            try {
                while (true) {
                    switch (phase) {
                        case STREAM_HEADER -> readStreamHeader();
                        case META_BLOCK_START -> {
                            if (enoughHasBeenMade()) {
                                return Progress.STOPPED_AT_LIMIT;
                            }
                            readMetaBlockHeader();
                        }
                        case UNCOMPRESSED -> copyUncompressedBytes();
                        case METADATA -> skipMetadataBytes();
                        case COMMANDS -> decodeCommands();
                        case PADDING -> {
                            markUnitStart();
                            bits.jumpToByteBoundary();
                            phase = Phase.DONE;
                        }
                        case DONE -> {
                            return Progress.FINISHED;
                        }
                    }
                }
            } catch (RanOut ranOut) {
                bits.resetTo(unitStart);
                return Progress.NEEDS_MORE_INPUT;
            }
        }

        private void markUnitStart() {
            unitStart = bits.position();
        }

        private void readStreamHeader() {
            markUnitStart();
            readWindowBitsRefusingLargeWindowAsRebolDoes();
            maxBackwardDistance = (1 << windowBits) - WINDOW_GAP;
            phase = Phase.META_BLOCK_START;
        }

        private void endMetaBlock() {
            phase = lastMetaBlock ? Phase.PADDING : Phase.META_BLOCK_START;
        }

        private boolean enoughHasBeenMade() {
            return limit != NO_LIMIT && outAt >= limit;
        }

        private void readWindowBitsRefusingLargeWindowAsRebolDoes() {
            if (bits.take(1) == 0) {
                windowBits = 16;
                return;
            }
            int more = bits.take(3);
            if (more != 0) {
                windowBits = 17 + more;
                return;
            }
            more = bits.take(3);
            if (more == 1) {
                throw new IllegalArgumentException(
                        "this build does not read large-window Brotli");
            }
            windowBits = more != 0 ? 8 + more : 17;
        }

        private void makeRoom(int howMany) {
            if (outAt + howMany <= out.length) {
                return;
            }
            int wanted = Math.max(outAt + howMany, out.length * 2);
            out = Arrays.copyOf(out, wanted);
        }

        private void readMetaBlockHeader() {
            markUnitStart();
            boolean isLast = bits.take(1) != 0;
            if (isLast && bits.take(1) != 0) {
                lastMetaBlock = true;
                phase = Phase.PADDING;
                return;
            }
            int nibbles = bits.take(2) + 4;
            if (nibbles == 7) {
                remaining = readMetadataLengthThenJumpToByteBoundary();
                lastMetaBlock = isLast;
                phase = Phase.METADATA;
                return;
            }
            remaining = 0;
            for (int each = 0; each < nibbles; each++) {
                int nibble = bits.take(4);
                if (each + 1 == nibbles && nibbles > 4 && nibble == 0) {
                    throw new IllegalArgumentException(
                            "Brotli meta-block length has a wasted nibble");
                }
                remaining |= nibble << (each * 4);
            }
            remaining++;
            if (!isLast && bits.take(1) != 0) {
                bits.jumpToByteBoundary();
                lastMetaBlock = false;
                phase = Phase.UNCOMPRESSED;
                return;
            }
            readCompressedMetaBlockHeader();
            lastMetaBlock = isLast;
            commandStep = CommandStep.COMMAND;
            phase = Phase.COMMANDS;
        }

        private void copyUncompressedBytes() {
            while (remaining > 0) {
                markUnitStart();
                int octet = bits.takeByte();
                makeRoom(1);
                out[outAt++] = (byte) octet;
                remaining--;
            }
            endMetaBlock();
        }

        private void skipMetadataBytes() {
            while (remaining > 0) {
                markUnitStart();
                bits.takeByte();
                remaining--;
            }
            endMetaBlock();
        }

        private int readMetadataLengthThenJumpToByteBoundary() {
            if (bits.take(1) != 0) {
                throw new IllegalArgumentException(
                        "Brotli metadata sets a reserved bit");
            }
            int byteCount = bits.take(2);
            int remaining = 0;
            for (int each = 0; each < byteCount; each++) {
                int octet = bits.take(8);
                if (each + 1 == byteCount && byteCount > 1 && octet == 0) {
                    throw new IllegalArgumentException(
                            "Brotli metadata length has a wasted byte");
                }
                remaining |= octet << (each * 8);
            }
            if (byteCount != 0) {
                remaining++;
            }
            bits.jumpToByteBoundary();
            return remaining;
        }

        private void readCompressedMetaBlockHeader() {
            literals = readBlockSwitcher();
            commands = readBlockSwitcher();
            distances = readBlockSwitcher();
            int blockTypeCount0 = literals.typeCount;
            int blockTypeCount1 = commands.typeCount;
            int blockTypeCount2 = distances.typeCount;

            int packed = bits.take(6);
            postfixBits = packed & 3;
            int directCodes = (packed >> 2) << postfixBits;

            contextModes = new int[blockTypeCount0];
            for (int each = 0; each < blockTypeCount0; each++) {
                contextModes[each] = bits.take(2);
            }

            literalMap = readContextMap(blockTypeCount0 << LITERAL_CONTEXT_BITS);
            distanceMap = readContextMap(blockTypeCount2 << DISTANCE_CONTEXT_BITS);

            int distanceAlphabet = DISTANCE_SHORT_CODES + directCodes
                    + (MAX_DISTANCE_BITS << (postfixBits + 1));

            literalCodes = readTreeGroup(literalMap.treeCount, LITERAL_SYMBOLS);
            commandCodes = readTreeGroup(blockTypeCount1, COMMAND_SYMBOLS);
            distanceCodes = readTreeGroup(distanceMap.treeCount, distanceAlphabet);

            distanceExtraBits = new int[distanceAlphabet];
            distanceOffset = new int[distanceAlphabet];
            fillDistanceTable(distanceExtraBits, distanceOffset,
                    postfixBits, directCodes);

            contextMode = contextModes[literals.type()] & 3;
            literalContextBase = literals.type() << LITERAL_CONTEXT_BITS;
            distanceContextBase = distances.type() << DISTANCE_CONTEXT_BITS;
            commandCode = commandCodes[commands.type()];
        }

        private void decodeCommands() {
            while (true) {
                switch (commandStep) {
                    case COMMAND -> {
                        if (remaining <= 0) {
                            endMetaBlock();
                            return;
                        }
                        readCommand();
                    }
                    case LITERALS -> {
                        decodeLiterals();
                        if (remaining <= 0) {
                            commandStep = CommandStep.COMMAND;
                            endMetaBlock();
                            return;
                        }
                        commandStep = CommandStep.DISTANCE;
                    }
                    case DISTANCE -> copyFromDistance();
                }
            }
        }

        private void refuseOverrun() {
            if (remaining < 0) {
                throw new IllegalArgumentException(
                        "Brotli commands run past the end of their meta-block");
            }
        }

        private void readCommand() {
            if (commands.exhausted()) {
                markUnitStart();
                commands.next();
                commandCode = commandCodes[commands.type()];
            }
            markUnitStart();
            Command read = COMMANDS[commandCode.read(bits)];
            int insertLength = read.insertOffset() + bits.take(read.insertExtraBits());
            int copied = read.copyOffset() + bits.take(read.copyExtraBits());
            commands.spendOne();
            command = read;
            insertLeft = insertLength;
            copyLength = copied;
            remaining -= insertLength;
            refuseOverrun();
            commandStep = CommandStep.LITERALS;
        }

        private void decodeLiterals() {
            while (insertLeft > 0) {
                if (literals.exhausted()) {
                    markUnitStart();
                    literals.next();
                    contextMode = contextModes[literals.type()] & 3;
                    literalContextBase = literals.type() << LITERAL_CONTEXT_BITS;
                }
                markUnitStart();
                int previous = outAt > 0 ? out[outAt - 1] & 0xFF : 0;
                int beforeThat = outAt > 1 ? out[outAt - 2] & 0xFF : 0;
                int context = BrotliContext.of(contextMode, previous, beforeThat);
                Huffman code = literalCodes[literalMap.at(literalContextBase + context)];
                int literal = code.read(bits);
                literals.spendOne();
                makeRoom(1);
                out[outAt++] = (byte) literal;
                insertLeft--;
            }
        }

        private void copyFromDistance() {
            int distance;
            if (!command.distanceIsWritten()) {
                howFarRecentDistanceCursorWasRolledBack = 1;
                recentDistanceAt--;
                distance = recentDistances[recentDistanceAt & 3];
            } else {
                if (distances.exhausted()) {
                    markUnitStart();
                    distances.next();
                    distanceContextBase = distances.type() << DISTANCE_CONTEXT_BITS;
                }
                markUnitStart();
                Huffman code = distanceCodes[distanceMap.at(
                        distanceContextBase + command.distanceContext())];
                int symbol = code.read(bits);
                int extra = symbol < DISTANCE_SHORT_CODES ? 0 : bits.take(distanceExtraBits[symbol]);
                distances.spendOne();
                howFarRecentDistanceCursorWasRolledBack = 0;
                if (symbol < DISTANCE_SHORT_CODES) {
                    distance = readDistanceFromRecentOnes(symbol);
                } else {
                    distance = distanceOffset[symbol] + (extra << postfixBits);
                }
            }

            int reachable = (int) Math.min(dropped + outAt, maxBackwardDistance);
            if (distance > reachable) {
                if (distance > MAX_ALLOWED_DISTANCE) {
                    throw new IllegalArgumentException(
                            "Brotli distance is larger than any distance can be");
                }
                remaining -= copyFromDictionaryAnsweringHowManyBytesItCameTo(
                        distance - reachable - 1, copyLength, distance);
            } else {
                recentDistances[recentDistanceAt & 3] = distance;
                recentDistanceAt++;
                makeRoom(copyLength);
                int fromHere = outAt - distance;
                for (int each = 0; each < copyLength; each++) {
                    out[outAt++] = out[fromHere + each];
                }
                remaining -= copyLength;
            }
            refuseOverrun();
            commandStep = CommandStep.COMMAND;
        }

        private int howFarRecentDistanceCursorWasRolledBack;

        private int readDistanceFromRecentOnes(int code) {
            if (code <= 3) {
                int distance = recentDistances[(recentDistanceAt - (code - 3)) & 3];
                howFarRecentDistanceCursorWasRolledBack = 1 >> code;
                recentDistanceAt -= howFarRecentDistanceCursorWasRolledBack;
                return distance;
            }
            int step = 3;
            int base = code - 10;
            if (code < 10) {
                base = code - 4;
            } else {
                step = 2;
            }
            int adjustment = ((0x605142 >> (4 * base)) & 0xF) - 3;
            int distance = recentDistances[(recentDistanceAt + step) & 3] + adjustment;
            return distance <= 0 ? Integer.MAX_VALUE : distance;
        }

        private int copyFromDictionaryAnsweringHowManyBytesItCameTo(
                int address, int wordLength, int distance) {
            if (wordLength < BrotliDictionary.MIN_WORD_LENGTH
                    || wordLength > BrotliDictionary.LONGEST_LENGTH_WITH_SLOT) {
                throw new IllegalArgumentException(
                        "Brotli distance reaches past the start of the answer");
            }
            int shift = BrotliDictionary.readSizeBitsFor(wordLength);
            if (shift == 0) {
                throw new IllegalArgumentException(
                        "Brotli asks for a dictionary word of a length there are none of");
            }
            int wordIndex = address & ((1 << shift) - 1);
            int transform = address >>> shift;
            if (transform >= BrotliDictionary.TRANSFORM_COUNT) {
                throw new IllegalArgumentException(
                        "Brotli names a transform that is not there");
            }
            recentDistanceAt += howFarRecentDistanceCursorWasRolledBack;
            int wordAt = BrotliDictionary.offsetFor(wordLength) + wordIndex * wordLength;
            makeRoom(LONGEST_TRANSFORMED_WORD);
            int written = BrotliDictionary.writeTransformedWord(
                    out, outAt, wordAt, wordLength, transform);
            if (written == 0 && distance <= 120) {
                throw new IllegalArgumentException(
                        "Brotli transform left nothing of its dictionary word");
            }
            outAt += written;
            return written;
        }

        private static final int WIDEST_PREFIX_OR_SUFFIX_MAY_BE = 255;
        private static final int WIDEST_DICTIONARY_WORD_MAY_BE = 32;
        private static final int LONGEST_TRANSFORMED_WORD =
                WIDEST_PREFIX_OR_SUFFIX_MAY_BE + WIDEST_DICTIONARY_WORD_MAY_BE
                        + WIDEST_PREFIX_OR_SUFFIX_MAY_BE;

        private BlockSwitcher readBlockSwitcher() {
            int typeCount = readVariableLengthCount() + 1;
            if (typeCount < 2) {
                return new BlockSwitcher(typeCount, null, null);
            }
            Huffman types = readHuffmanCode(typeCount + 2);
            Huffman lengths = readHuffmanCode(BLOCK_LENGTH_SYMBOLS);
            BlockSwitcher switcher = new BlockSwitcher(typeCount, types, lengths);
            switcher.readFirstLength();
            return switcher;
        }

        private int readVariableLengthCount() {
            if (bits.take(1) == 0) {
                return 0;
            }
            int width = bits.take(3);
            if (width == 0) {
                return 1;
            }
            return (1 << width) + bits.take(width);
        }

        private final class BlockSwitcher {

            private final int typeCount;
            private final Huffman types;
            private final Huffman lengths;
            private final int[] lastTwoTypes = {1, 0};
            private int remaining = BLOCK_SIZE_CAP;

            private BlockSwitcher(int typeCount, Huffman types, Huffman lengths) {
                this.typeCount = typeCount;
                this.types = types;
                this.lengths = lengths;
            }

            private void readFirstLength() {
                remaining = readBlockLength(lengths);
            }

            int type() {
                return lastTwoTypes[1];
            }

            boolean exhausted() {
                return remaining == 0;
            }

            void spendOne() {
                remaining--;
            }

            void next() {
                if (types == null) {
                    throw new IllegalArgumentException(
                            "Brotli asks for a block switch where there is one block type");
                }
                int symbol = types.read(bits);
                int length = readBlockLength(lengths);
                int chosen;
                if (symbol == 1) {
                    chosen = lastTwoTypes[1] + 1;
                } else if (symbol == 0) {
                    chosen = lastTwoTypes[0];
                } else {
                    chosen = symbol - 2;
                }
                if (chosen >= typeCount) {
                    chosen -= typeCount;
                }
                lastTwoTypes[0] = lastTwoTypes[1];
                lastTwoTypes[1] = chosen;
                remaining = length;
            }
        }

        private int readBlockLength(Huffman lengths) {
            int symbol = lengths.read(bits);
            return BLOCK_LENGTH_OFFSET[symbol]
                    + bits.take(BLOCK_LENGTH_EXTRA_BITS[symbol]);
        }

        private record ContextMap(int treeCount, byte[] entries) {

            int at(int index) {
                return entries[index] & 0xFF;
            }
        }

        private ContextMap readContextMap(int size) {
            int treeCount = readVariableLengthCount() + 1;
            byte[] entries = new byte[size];
            if (treeCount <= 1) {
                return new ContextMap(treeCount, entries);
            }
            int maxRunLengthPrefix = 0;
            if (bits.take(1) != 0) {
                maxRunLengthPrefix = bits.take(4) + 1;
            }
            Huffman code = readHuffmanCode(treeCount + maxRunLengthPrefix);
            int at = 0;
            while (at < size) {
                int symbol = code.read(bits);
                if (symbol == 0) {
                    entries[at++] = 0;
                } else if (symbol > maxRunLengthPrefix) {
                    entries[at++] = (byte) (symbol - maxRunLengthPrefix);
                } else {
                    int repeats = (1 << symbol) + bits.take(symbol);
                    if (at + repeats > size) {
                        throw new IllegalArgumentException(
                                "Brotli context map repeats past its end");
                    }
                    for (int each = 0; each < repeats; each++) {
                        entries[at++] = 0;
                    }
                }
            }
            if (bits.take(1) != 0) {
                undoMoveToFront(entries);
            }
            return new ContextMap(treeCount, entries);
        }

        private static void undoMoveToFront(byte[] entries) {
            int[] order = new int[256];
            for (int each = 0; each < 256; each++) {
                order[each] = each;
            }
            for (int at = 0; at < entries.length; at++) {
                int index = entries[at] & 0xFF;
                int value = order[index];
                entries[at] = (byte) value;
                System.arraycopy(order, 0, order, 1, index);
                order[0] = value;
            }
        }

        private Huffman[] readTreeGroup(int howMany, int alphabetSize) {
            Huffman[] group = new Huffman[howMany];
            for (int each = 0; each < howMany; each++) {
                group[each] = readHuffmanCode(alphabetSize);
            }
            return group;
        }

        private Huffman readHuffmanCode(int alphabetSize) {
            int kind = bits.take(2);
            if (kind == 1) {
                return readSimpleCode(alphabetSize);
            }
            return readComplexCode(alphabetSize, kind);
        }

        private Huffman readSimpleCode(int alphabetSize) {
            int maxBits = 32 - Integer.numberOfLeadingZeros(alphabetSize - 1);
            int howMany = bits.take(2) + 1;
            int[] symbols = new int[4];
            for (int each = 0; each < howMany; each++) {
                int symbol = bits.take(maxBits);
                if (symbol >= alphabetSize) {
                    throw new IllegalArgumentException(
                            "Brotli names a symbol outside its alphabet");
                }
                symbols[each] = symbol;
            }
            for (int each = 0; each < howMany; each++) {
                for (int other = each + 1; other < howMany; other++) {
                    if (symbols[each] == symbols[other]) {
                        throw new IllegalArgumentException(
                                "Brotli names the same symbol twice");
                    }
                }
            }
            boolean unbalanced = howMany == 4 && bits.take(1) != 0;
            int[] lengths = new int[alphabetSize];
            switch (howMany) {
                case 1 -> {
                    return Huffman.ofOneSymbol(symbols[0]);
                }
                case 2 -> {
                    lengths[symbols[0]] = 1;
                    lengths[symbols[1]] = 1;
                }
                case 3 -> {
                    lengths[symbols[0]] = 1;
                    lengths[symbols[1]] = 2;
                    lengths[symbols[2]] = 2;
                }
                default -> {
                    if (unbalanced) {
                        lengths[symbols[0]] = 1;
                        lengths[symbols[1]] = 2;
                        lengths[symbols[2]] = 3;
                        lengths[symbols[3]] = 3;
                    } else {
                        for (int each = 0; each < 4; each++) {
                            lengths[symbols[each]] = 2;
                        }
                    }
                }
            }
            return Huffman.ofLengths(lengths);
        }

        private static int findOnlyOneNamed(int[] codeLengthLengths) {
            for (int each = 0; each < codeLengthLengths.length; each++) {
                if (codeLengthLengths[each] != 0) {
                    return each;
                }
            }
            throw new IllegalArgumentException(
                    "Brotli code-length code names nothing at all");
        }

        private Huffman readComplexCode(int alphabetSize, int skip) {
            int[] codeLengthLengths = new int[CODE_LENGTH_CODES];
            int space = 32;
            int symbolsUsed = 0;
            for (int each = skip; each < CODE_LENGTH_CODES; each++) {
                int peeked = bits.peekFourBits();
                int width = CODE_LENGTH_PREFIX_LENGTH[peeked];
                bits.drop(width);
                int value = CODE_LENGTH_PREFIX_VALUE[peeked];
                codeLengthLengths[CODE_LENGTH_ORDER[each]] = value;
                if (value != 0) {
                    space -= 32 >> value;
                    symbolsUsed++;
                    if (space <= 0) {
                        break;
                    }
                }
            }
            if (symbolsUsed != 1 && space != 0) {
                throw new IllegalArgumentException(
                        "Brotli code-length code is not a prefix code");
            }
            Huffman lengthCode = symbolsUsed == 1
                    ? Huffman.ofOneSymbol(findOnlyOneNamed(codeLengthLengths))
                    : Huffman.ofLengths(codeLengthLengths);

            int[] lengths = new int[alphabetSize];
            int symbol = 0;
            int previousLength = INITIAL_REPEATED_CODE_LENGTH;
            int repeat = 0;
            int repeatLength = 0;
            int remainingSpace = 32768;
            while (symbol < alphabetSize && remainingSpace > 0) {
                int codeLength = lengthCode.read(bits);
                if (codeLength < REPEAT_PREVIOUS_CODE_LENGTH) {
                    repeat = 0;
                    if (codeLength != 0) {
                        lengths[symbol] = codeLength;
                        previousLength = codeLength;
                        remainingSpace -= 32768 >> codeLength;
                    }
                    symbol++;
                    continue;
                }
                int extraBits = codeLength == REPEAT_PREVIOUS_CODE_LENGTH ? 2 : 3;
                int newLength =
                        codeLength == REPEAT_PREVIOUS_CODE_LENGTH ? previousLength : 0;
                if (repeatLength != newLength) {
                    repeat = 0;
                    repeatLength = newLength;
                }
                int before = repeat;
                if (repeat > 0) {
                    repeat -= 2;
                    repeat <<= extraBits;
                }
                repeat += bits.take(extraBits) + 3;
                int howMany = repeat - before;
                if (symbol + howMany > alphabetSize) {
                    throw new IllegalArgumentException(
                            "Brotli code lengths repeat past the alphabet");
                }
                if (repeatLength != 0) {
                    for (int each = 0; each < howMany; each++) {
                        lengths[symbol + each] = repeatLength;
                    }
                    remainingSpace -= howMany << (15 - repeatLength);
                }
                symbol += howMany;
            }
            if (remainingSpace != 0) {
                throw new IllegalArgumentException(
                        "Brotli prefix code does not fill its space");
            }
            return Huffman.ofLengths(lengths);
        }

        private void fillDistanceTable(int[] extraBits, int[] offset,
                int postfixBits, int directCodes) {

            int postfix = 1 << postfixBits;
            int at = DISTANCE_SHORT_CODES;
            for (int each = 0; each < directCodes; each++) {
                extraBits[at] = 0;
                offset[at] = each + 1;
                at++;
            }
            int width = 1;
            int half = 0;
            while (at < extraBits.length) {
                int base = directCodes + ((((2 + half) << width) - 4) << postfixBits) + 1;
                for (int each = 0; each < postfix && at < extraBits.length; each++) {
                    extraBits[at] = width;
                    offset[at] = base + each;
                    at++;
                }
                width += half;
                half ^= 1;
            }
        }
    }

    private record Huffman(int[] countPerLength, int[] symbolsInOrder, int onlySymbol) {

        static Huffman ofOneSymbol(int symbol) {
                return new Huffman(null, null, symbol);
            }

            static Huffman ofLengths(int[] lengths) {
                int[] counts = new int[MAX_CODE_LENGTH + 1];
                int total = 0;
                for (int length : lengths) {
                    counts[length]++;
                    if (length != 0) {
                        total++;
                    }
                }
                counts[0] = 0;
                int[] starts = new int[MAX_CODE_LENGTH + 2];
                for (int length = 1; length <= MAX_CODE_LENGTH; length++) {
                    starts[length + 1] = starts[length] + counts[length];
                }
                int[] symbols = new int[total];
                int[] filled = new int[MAX_CODE_LENGTH + 2];
                for (int symbol = 0; symbol < lengths.length; symbol++) {
                    int length = lengths[symbol];
                    if (length != 0) {
                        symbols[starts[length + 1] - counts[length] + filled[length]] = symbol;
                        filled[length]++;
                    }
                }
                return new Huffman(counts, symbols, -1);
            }

            int read(BitsLeastSignificantOfEachByteFirst bits) {
                if (onlySymbol >= 0) {
                    return onlySymbol;
                }
                int code = 0;
                int first = 0;
                int index = 0;
                for (int length = 1; length <= MAX_CODE_LENGTH; length++) {
                    code |= bits.take(1);
                    int howMany = countPerLength[length];
                    if (code - first < howMany) {
                        return symbolsInOrder[index + code - first];
                    }
                    index += howMany;
                    first = (first + howMany) << 1;
                    code <<= 1;
                }
                throw new IllegalArgumentException("Brotli symbol is longer than any code");
            }
        }

    private static final class BitsLeastSignificantOfEachByteFirst {

        private byte[] data;
        private int length;
        private long position;

        private BitsLeastSignificantOfEachByteFirst(byte[] data) {
            this.data = data;
            this.length = data.length;
        }

        long position() {
            return position;
        }

        void resetTo(long earlier) {
            position = earlier;
        }

        void append(byte[] more) {
            int consumed = (int) (position >>> 3);
            int kept = length - consumed;
            byte[] grown = data.length >= kept + more.length && consumed == 0
                    ? data
                    : new byte[Math.max(kept + more.length, 2 * kept)];
            System.arraycopy(data, consumed, grown, 0, kept);
            System.arraycopy(more, 0, grown, kept, more.length);
            data = grown;
            length = kept + more.length;
            position -= (long) consumed << 3;
        }

        private long bitsLeft() {
            return ((long) length << 3) - position;
        }

        int take(int howMany) {
            if (howMany == 0) {
                return 0;
            }
            if (howMany > bitsLeft()) {
                throw RAN_OUT;
            }
            int byteAt = (int) (position >>> 3);
            int shift = (int) (position & 7);
            long gathered = 0;
            for (int each = 0; each << 3 < shift + howMany; each++) {
                gathered |= (long) (data[byteAt + each] & 0xFF) << (each << 3);
            }
            position += howMany;
            return (int) ((gathered >>> shift) & ((1L << howMany) - 1));
        }

        int peekFourBits() {
            int available = (int) Math.min(4, bitsLeft());
            long before = position;
            int peeked = take(available);
            position = before;
            return peeked;
        }

        void drop(int howMany) {
            take(howMany);
        }

        int takeByte() {
            return take(8);
        }

        void jumpToByteBoundary() {
            int spare = (int) (-position & 7);
            if (spare != 0 && take(spare) != 0) {
                throw new IllegalArgumentException(
                        "Brotli padding up to a byte boundary is not zero");
            }
        }
    }
}
