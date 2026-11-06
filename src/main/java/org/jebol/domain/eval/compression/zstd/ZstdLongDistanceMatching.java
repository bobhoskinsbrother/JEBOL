package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.compression.CodecTrace;
import org.jebol.domain.eval.hashing.RunningXxHash64;

final class ZstdLongDistanceMatching {

    private static final int BUCKET_SIZE_LOG = 4;
    private static final int MIN_MATCH_LENGTH = 64;
    private static final int BUCKET_SIZE_LOG_MAX = 8;
    private static final int BATCH_SIZE = 64;
    private static final int MAX_CHUNK_SIZE = 1 << 20;
    private static final long[] GEAR_TABLE = {
            0xf5b8f72c5f77775cL, 0x84935f266b7ac412L, 0xb647ada9ca730cccL, 0xb065bb4b114fb1deL,
            0x34584e7e8c3a9fd0L, 0x4e97e17c6ae26b05L, 0x3a03d743bc99a604L, 0xcecd042422c4044fL,
            0x76de76c58524259eL, 0x9c8528f65badeacaL, 0x86563706e2097529L, 0x2902475fa375d889L,
            0xafb32a9739a5ebe6L, 0xce2714da3883e639L, 0x21eaf821722e69eL, 0x37b628620b628L,
            0x49a8d455d88caf5L, 0x8556d711e6958140L, 0x4f7ae74fc605c1fL, 0x829f0c3468bd3a20L,
            0x4ffdc885c625179eL, 0x8473de048a3daf1bL, 0x51008822b05646b2L, 0x69d75d12b2d1cc5fL,
            0x8c9d4a19159154bcL, 0xc3cc10f4abbd4003L, 0xd06ddc1cecb97391L, 0xbe48e6e7ed80302eL,
            0x3481db31cee03547L, 0xacc3f67cdaa1d210L, 0x65cb771d8c7f96ccL, 0x8eb27177055723ddL,
            0xc789950d44cd94beL, 0x934feadc3700b12bL, 0x5e485f11edbdf182L, 0x1e2e2a46fd64767aL,
            0x2969ca71d82efa7cL, 0x9d46e9935ebbba2eL, 0xe056b67e05e6822bL, 0x94d73f55739d03a0L,
            0xcd7010bdb69b5a03L, 0x455ef9fcd79b82f4L, 0x869cb54a8749c161L, 0x38d1a4fa6185d225L,
            0xb475166f94bbe9bbL, 0xa4143548720959f1L, 0x7aed4780ba6b26baL, 0xd0ce264439e02312L,
            0x84366d746078d508L, 0xa8ce973c72ed17beL, 0x21c323a29a430b01L, 0x9962d617e3af80eeL,
            0xab0ce91d9c8cf75bL, 0x530e8ee6d19a4dbcL, 0x2ef68c0cf53f5d72L, 0xc03a681640a85506L,
            0x496e4e9f9c310967L, 0x78580472b59b14a0L, 0x273824c23b388577L, 0x66bf923ad45cb553L,
            0x47ae1a5a2492ba86L, 0x35e304569e229659L, 0x4765182a46870b6fL, 0x6cbab625e9099412L,
            0xddac9a2e598522c1L, 0x7172086e666624f2L, 0xdf5003ca503b7837L, 0x88c0c1db78563d09L,
            0x58d51865acfc289dL, 0x177671aec65224f1L, 0xfb79d8a241e967d7L, 0x2be1e101cad9a49aL,
            0x6625682f6e29186bL, 0x399553457ac06e50L, 0x35dffb4c23abb74L, 0x429db2591f54aadeL,
            0xc52802a8037d1009L, 0x6acb27381f0b25f3L, 0xf45e2551ee4f823bL, 0x8b0ea2d99580c2f7L,
            0x3bed519cbcb4e1e1L, 0xff452823dbb010aL, 0x9d42ed614f3dd267L, 0x5b9313c06257c57bL,
            0xa114b8008b5e1442L, 0xc1fe311c11c13d4bL, 0x66e8763ea34c5568L, 0x8b982af1c262f05dL,
            0xee8876faaa75fbb7L, 0x8a62a4d0d172bb2aL, 0xc13d94a3b7449a97L, 0x6dbbba9dc15d037cL,
            0xc786101f1d92e0f1L, 0xd78681a907a0b79bL, 0xf61aaf2962c9abb9L, 0x2cfd16fcd3cb7ad9L,
            0x868c5b6744624d21L, 0x25e650899c74ddd7L, 0xba042af4a7c37463L, 0x4eb1a539465a3ecaL,
            0xbe09dbf03b05d5caL, 0x774e5a362b5472baL, 0x47a1221229d183cdL, 0x504b0ca18ef5a2dfL,
            0xdffbdfbde2456eb9L, 0x46cd2b2fbee34634L, 0xf2aef8fe819d98c3L, 0x357f5276d4599d61L,
            0x24a5483879c453e3L, 0x88026889192b4b9L, 0x28da96671782dbecL, 0x4ef37c40588e9aaaL,
            0x8837b90651bc9fb3L, 0xc164f741d3f0e5d6L, 0xbc135a0a704b70baL, 0x69cd868f7622adaL,
            0xbc37ba89e0b9c0abL, 0x47c14a01323552f6L, 0x4f00794bacee98bbL, 0x7107de7d637a69d5L,
            0x88af793bb6f2255eL, 0xf3c6466b8799b598L, 0xc288c616aa7f3b59L, 0x81ca63cf42fca3fdL,
            0x88d85ace36a2674bL, 0xd056bd3792389e7L, 0xe55c396c4e9dd32dL, 0xbefb504571e6c0a6L,
            0x96ab32115e91e8ccL, 0xbf8acb18de8f38d1L, 0x66dae58801672606L, 0x833b6017872317fbL,
            0xb87c16f2d1c92864L, 0xdb766a74e58b669cL, 0x89659f85c61417beL, 0xc8daad856011ea0cL,
            0x76a4b565b6fe7eaeL, 0xa469d085f6237312L, 0xaaf0365683a3e96cL, 0x4dbb746f8424f7b8L,
            0x638755af4e4acc1L, 0x3d7807f5bde64486L, 0x17be6d8f5bbb7639L, 0x903f0cd44dc35dcL,
            0x67b672eafdf1196cL, 0xa676ff93ed4c82f1L, 0x521d1004c5053d9dL, 0x37ba9ad09ccc9202L,
            0x84e54d297aacfb51L, 0xa0b4b776a143445L, 0x820d471e20b348eL, 0x1874383cb83d46dcL,
            0x97edeec7a1efe11cL, 0xb330e50b1bdc42aaL, 0x1dd91955ce70e032L, 0xa514cdb88f2939d5L,
            0x2791233fd90db9d3L, 0x7b670a4cc50f7a9bL, 0x77c07d2a05c6dfa5L, 0xe3778b6646d0a6faL,
            0xb39c8eda47b56749L, 0x933ed448addbef28L, 0xaf846af6ab7d0bf4L, 0xe5af208eb666e49L,
            0x5e6622f73534cd6aL, 0x297daeca42ef5b6eL, 0x862daef3d35539a6L, 0xe68722498f8e1ea9L,
            0x981c53093dc0d572L, 0xfa09b0bfbf86fbf5L, 0x30b1e96166219f15L, 0x70e7d466bdc4fb83L,
            0x5a66736e35f2a8e9L, 0xcddb59d2b7c1baefL, 0xd6c7d247d26d8996L, 0xea4e39eac8de1ba3L,
            0x539c8bb19fa3aff2L, 0x9f90e4c5fd508d8L, 0xa34e5956fbaf3385L, 0x2e2f8e151d3ef375L,
            0x173691e9b83faec1L, 0xb85a8d56bf016379L, 0x8382381267408ae3L, 0xb90f901bbdc0096dL,
            0x7c6ad32933bcec65L, 0x76bb5e2f2c8ad595L, 0x390f851a6cf46d28L, 0xc3e6064da1c2da72L,
            0xc52a0c101cfa5389L, 0xd78eaf84a3fbc530L, 0x3781b9e2288b997eL, 0x73c2f6dea83d05c4L,
            0x4228e364c5b5ed7L, 0x9d7a3edf0da43911L, 0x8edcfeda24686756L, 0x5e7667a7b7a9b3a1L,
            0x4c4f389fa143791dL, 0xb08bc1023da7cddcL, 0x7ab4be3ae529b1ccL, 0x754e6132dbe74ff9L,
            0x71635442a839df45L, 0x2f6fb1643fbe52deL, 0x961e0a42cf7a8177L, 0xf3b45d83d89ef2eaL,
            0xee3de4cf4a6e3e9bL, 0xcd6848542c3295e7L, 0xe4cee1664c78662fL, 0x9947548b474c68c4L,
            0x25d73777a5ed8b0bL, 0xc915b1d636b7fcL, 0x21c2ba75d9b0d2daL, 0x5f6b5dcf608a64a1L,
            0xdcf333255ff9570cL, 0x633b922418ced4eeL, 0xc136dde0b004b34aL, 0x58cc83b05d4b2f5aL,
            0x5eb424dda28e42d2L, 0x62df47369739cd98L, 0xb4e0b42485e4ce17L, 0x16e1f0c1f9a8d1e7L,
            0x8ec3916707560ebfL, 0x62ba6e2df2cc9db3L, 0xcbf9f4ff77d83a16L, 0x78d9d7d07d2bbcc4L,
            0xef554ce1e02c41f4L, 0x8d7581127eccf94dL, 0xa9b53336cb3c8a05L, 0x38c42c0bf45c4f91L,
            0x640893cdf4488863L, 0x80ec34bc575ea568L, 0x39f324f5b48eaa40L, 0xe9d9ed1f8eff527fL,
            0x9224fc058cc5a214L, 0xbaba00b04cfe7741L, 0x309a9f120fcf52afL, 0xa558f3ec65626212L,
            0x424bec8b7adabe2fL, 0x41622513a6aea433L, 0xb88da2d5324ca798L, 0xd287733b245528a4L,
            0x9a44697e6d68aec3L, 0x7b1093be2f49bb28L, 0x50bbec632e3d8aadL, 0x6cd90723e1ea8283L,
            0x897b9e7431b02bf3L, 0x219efdcb338a7047L, 0x3b0311f0a27c0656L, 0xdb17bf91c0db96e7L,
            0x8cd4fd6b4e85a5b2L, 0xfab071054ba6409dL, 0x40d6fe831fa9dfd9L, 0xaf358debad7d791eL,
            0xeb8d0e25a65e3e58L, 0xbbcbd3df14e08580L, 0xcf751f27ecdab2bL, 0x2b4da14f2613d8f4L};

    private int windowLog;
    private int hashRateLog;
    private int hashLog;
    private int minMatchLength;
    private int bucketSizeLog;

    private final ZstdMatchState window = new ZstdMatchState();
    private final ZstdTableRegion entryOffsets = new ZstdTableRegion();
    private final ZstdTableRegion entryChecksums = new ZstdTableRegion();
    private byte[] bucketOffsets = new byte[0];
    private ZstdRawSequenceStore sequences = new ZstdRawSequenceStore(0);
    private final RunningXxHash64 xxhash = new RunningXxHash64();
    private final long[] splits = new long[BATCH_SIZE];
    private final int[] candidateSplits = new int[BATCH_SIZE];
    private final int[] candidateHashes = new int[BATCH_SIZE];
    private final int[] candidateChecksums = new int[BATCH_SIZE];
    private long rolling;
    private long stopMask;

    void adjustParameters(ZstdCompressionParameters parameters) {
        windowLog = parameters.windowLog;
        hashRateLog = 7 - parameters.strategy / 3;
        hashLog = Math.max(ZstdCompressionParameters.HASH_LOG_MIN,
                Math.min(windowLog - hashRateLog, ZstdCompressionParameters.HASH_LOG_MAX));
        minMatchLength = MIN_MATCH_LENGTH;
        if (parameters.strategy >= ZstdCompressionParameters.BTULTRA) {
            minMatchLength /= 2;
        }
        bucketSizeLog = Math.max(BUCKET_SIZE_LOG, Math.min(parameters.strategy, BUCKET_SIZE_LOG_MAX));
        bucketSizeLog = Math.min(bucketSizeLog, hashLog);
    }

    long tableSize() {
        long entries = 1L << hashLog;
        int bucketLog = Math.min(bucketSizeLog, hashLog);
        long buckets = 1L << (hashLog - bucketLog);
        return buckets + entries * 8;
    }

    int minMatchLength() {
        return minMatchLength;
    }

    void reset(int blockSize) {
        entryOffsets.forget();
        entryChecksums.forget();
        int capacity = blockSize / minMatchLength;
        if (sequences.capacity != capacity) {
            sequences = new ZstdRawSequenceStore(capacity);
        }
        window.initWindow();
        window.loadedDictionaryEnd = 0;
        int bucketCount = 1 << (hashLog - bucketSizeLog);
        if (bucketOffsets.length != bucketCount) {
            bucketOffsets = new byte[bucketCount];
        } else {
            java.util.Arrays.fill(bucketOffsets, (byte) 0);
        }
    }

    void updateWindow(byte[] source, int from, int size) {
        window.updateWindow(source, from, size, false);
    }

    private void initRolling() {
        int maxBitsInMask = Math.min(minMatchLength, 64);
        rolling = 0xFFFFFFFFL;
        if (hashRateLog > 0 && hashRateLog <= maxBitsInMask) {
            stopMask = ((1L << hashRateLog) - 1) << (maxBitsInMask - hashRateLog);
        } else {
            stopMask = (1L << hashRateLog) - 1;
        }
    }

    private int feed(byte[] data, int from, int size, int[] splitCount) {
        long hash = rolling;
        long mask = stopMask;
        int at = 0;
        while (at < size) {
            hash = (hash << 1) + GEAR_TABLE[data[from + at] & 0xFF];
            at++;
            if ((hash & mask) == 0) {
                splits[splitCount[0]] = at;
                splitCount[0]++;
                if (splitCount[0] == BATCH_SIZE) {
                    break;
                }
            }
        }
        rolling = hash;
        return at;
    }

    private void insertEntry(int hash, long offset, int checksum) {
        int bucketOffset = bucketOffsets[hash] & 0xFF;
        int at = (hash << bucketSizeLog) + bucketOffset;
        entryOffsets.set(at, (int) offset);
        entryChecksums.set(at, checksum);
        bucketOffsets[hash] = (byte) ((bucketOffset + 1) & ((1 << bucketSizeLog) - 1));
    }

    private static int countBackwards(byte[] inputArray, int input, int anchor, byte[] matchArray, int match,
                                      int matchBase) {
        int length = 0;
        int in = input;
        int at = match;
        while (in > anchor && at > matchBase && inputArray[in - 1] == matchArray[at - 1]) {
            in--;
            at--;
            length++;
        }
        return length;
    }

    private long generateChunk(byte[] source, int from, int size) {
        boolean isExtended = window.hasExtendedDictionary();
        int entriesPerBucket = 1 << bucketSizeLog;
        int hashBits = hashLog - bucketSizeLog;
        long dictionaryLimit = window.dictionaryLimit;
        long lowestIndex = isExtended ? window.lowLimit : dictionaryLimit;
        byte[] dictionary = window.dictionaryArray;
        int dictionaryStart = isExtended ? window.dictionaryPositionOf(lowestIndex) : 0;
        int dictionaryEnd = isExtended ? window.dictionaryPositionOf(dictionaryLimit) : 0;
        int lowPrefix = window.positionOf(dictionaryLimit);
        int end = from + size;
        int limit = end - ZstdMatchState.HASH_READ_SIZE;
        int anchor = from;
        int ip = from;
        int[] splitCount = new int[1];
        if (size < minMatchLength) {
            return end - anchor;
        }
        initRolling();
        ip += minMatchLength;
        while (ip < limit) {
            splitCount[0] = 0;
            int hashed = feed(source, ip, limit - ip, splitCount);
            for (int at = 0; at < splitCount[0]; at++) {
                int split = (int) (ip + splits[at] - minMatchLength);
                xxhash.reset();
                xxhash.update(source, split, minMatchLength);
                long digest = xxhash.digest();
                candidateSplits[at] = split;
                candidateHashes[at] = (int) (digest & ((1L << hashBits) - 1));
                candidateChecksums[at] = (int) (digest >>> 32);
            }
            for (int at = 0; at < splitCount[0]; at++) {
                long forwardLength = 0;
                long backwardLength = 0;
                long bestLength = 0;
                int split = candidateSplits[at];
                int checksum = candidateChecksums[at];
                int hash = candidateHashes[at];
                int bucket = hash << bucketSizeLog;
                long newOffset = window.indexOf(split) & 0xFFFFFFFFL;
                long bestOffset = -1;
                if (split < anchor) {
                    insertEntry(hash, newOffset, checksum);
                    continue;
                }
                for (int entry = bucket; entry < bucket + entriesPerBucket; entry++) {
                    long entryOffset = entryOffsets.get(entry) & 0xFFFFFFFFL;
                    if (entryChecksums.get(entry) != checksum || entryOffset <= lowestIndex) {
                        continue;
                    }
                    long currentForward;
                    long currentBackward;
                    if (isExtended) {
                        boolean isInDictionary = entryOffset < dictionaryLimit;
                        byte[] matchArray = isInDictionary ? dictionary : source;
                        int match = isInDictionary ? window.dictionaryPositionOf(entryOffset)
                                : window.positionOf(entryOffset);
                        int matchEnd = isInDictionary ? dictionaryEnd : end;
                        int lowMatch = isInDictionary ? dictionaryStart : lowPrefix;
                        currentForward = ZstdMatchState.countTwoSegments(source, split, matchArray, match, end,
                                matchEnd, lowPrefix);
                        if (currentForward < minMatchLength) {
                            continue;
                        }
                        currentBackward = countBackwards(source, split, anchor, matchArray, match, lowMatch);
                        if (match - currentBackward == lowMatch && !isInDictionary) {
                            currentBackward += countBackwards(source, (int) (split - currentBackward), anchor,
                                    dictionary, dictionaryEnd, dictionaryStart);
                        }
                    } else {
                        int match = window.positionOf(entryOffset);
                        currentForward = ZstdMatchState.count(source, split, match, end);
                        if (currentForward < minMatchLength) {
                            continue;
                        }
                        currentBackward = countBackwards(source, split, anchor, source, match, lowPrefix);
                    }
                    long total = currentForward + currentBackward;
                    if (total > bestLength) {
                        bestLength = total;
                        forwardLength = currentForward;
                        backwardLength = currentBackward;
                        bestOffset = entryOffset;
                    }
                }
                if (bestOffset < 0) {
                    insertEntry(hash, newOffset, checksum);
                    continue;
                }
                long offset = (newOffset - bestOffset) & 0xFFFFFFFFL;
                long matchLength = forwardLength + backwardLength;
                if (sequences.size == sequences.capacity) {
                    throw ZstdError.DST_SIZE_TOO_SMALL.failure();
                }
                sequences.literalLengths[sequences.size] = (int) (split - backwardLength - anchor);
                sequences.matchLengths[sequences.size] = (int) matchLength;
                sequences.offsets[sequences.size] = (int) offset;
                sequences.size++;
                insertEntry(hash, newOffset, checksum);
                anchor = (int) (split + forwardLength);
                if (anchor > ip + hashed) {
                    ip = anchor - hashed;
                    break;
                }
            }
            ip += hashed;
        }
        return end - anchor;
    }

    private void generateSequences(byte[] source, int from, int size) {
        long maxDistance = 1L << windowLog;
        int end = from + size;
        int chunks = size / MAX_CHUNK_SIZE + (size % MAX_CHUNK_SIZE != 0 ? 1 : 0);
        long leftover = 0;
        for (int chunk = 0; chunk < chunks && sequences.size < sequences.capacity; chunk++) {
            int chunkStart = from + chunk * MAX_CHUNK_SIZE;
            int remaining = end - chunkStart;
            int chunkEnd = remaining < MAX_CHUNK_SIZE ? end : chunkStart + MAX_CHUNK_SIZE;
            int chunkSize = chunkEnd - chunkStart;
            int previousSize = sequences.size;
            if ((window.indexOf(chunkEnd) & 0xFFFFFFFFL) > ZstdMatchState.CURRENT_MAX) {
                correctOverflow(chunkStart, maxDistance);
            }
            long blockEndIndex = window.indexOf(chunkEnd) & 0xFFFFFFFFL;
            if (blockEndIndex > maxDistance + window.loadedDictionaryEnd) {
                long newLowLimit = blockEndIndex - maxDistance;
                if (window.lowLimit < newLowLimit) {
                    window.lowLimit = newLowLimit;
                }
                if (window.dictionaryLimit < window.lowLimit) {
                    window.dictionaryLimit = window.lowLimit;
                }
                window.loadedDictionaryEnd = 0;
            }
            long newLeftover = generateChunk(source, chunkStart, chunkSize);
            if (previousSize < sequences.size) {
                sequences.literalLengths[previousSize] += (int) leftover;
                leftover = newLeftover;
            } else {
                leftover += chunkSize;
            }
        }
    }

    private void correctOverflow(int at, long maxDistance) {
        long cycleSize = 1;
        long current = window.indexOf(at) & 0xFFFFFFFFL;
        long currentCycle = current & (cycleSize - 1);
        long correctionOfCycle = currentCycle < ZstdMatchState.WINDOW_START_INDEX
                ? Math.max(cycleSize, ZstdMatchState.WINDOW_START_INDEX) : 0;
        long newCurrent = currentCycle + correctionOfCycle + Math.max(maxDistance, cycleSize);
        long correction = (current - newCurrent) & 0xFFFFFFFFL;
        window.base += correction;
        window.dictionaryBase += correction;
        window.lowLimit = window.lowLimit < correction + ZstdMatchState.WINDOW_START_INDEX
                ? ZstdMatchState.WINDOW_START_INDEX : window.lowLimit - correction;
        window.dictionaryLimit = window.dictionaryLimit < correction + ZstdMatchState.WINDOW_START_INDEX
                ? ZstdMatchState.WINDOW_START_INDEX : window.dictionaryLimit - correction;
        window.overflowCorrections++;
        int size = 1 << hashLog;
        for (int entry = 0; entry < size; entry++) {
            long offset = entryOffsets.get(entry) & 0xFFFFFFFFL;
            entryOffsets.set(entry, offset < correction ? 0 : (int) (offset - correction));
        }
        window.loadedDictionaryEnd = 0;
    }

    private String describeSequences() {
        StringBuilder described = new StringBuilder("zstd ldm block " + sequences.size + " sequences");
        for (int at = 0; at < sequences.size; at++) {
            described.append("\n  ").append(sequences.literalLengths[at]).append(' ')
                    .append(sequences.matchLengths[at]).append(' ').append(sequences.offsets[at]);
        }
        return described.toString();
    }

    int compressBlock(ZstdCompressionContext context, byte[] source, int from, int size) {
        sequences.size = 0;
        sequences.position = 0;
        sequences.positionInSequence = 0;
        generateSequences(source, from, size);
        CodecTrace.current().note(this::describeSequences);
        ZstdMatchState state = context.state;
        state.longDistanceSequences = sequences;
        int lastLiterals = ZstdMatchers.compressBlock(state, context.store, context.nextBlock.repeats, from, size,
                context.isRowMatchFinderUsed);
        sequences.skipBytes(size);
        return lastLiterals;
    }
}
