package org.jebol.domain.eval.compression.lz4;

import static org.jebol.domain.eval.compression.lz4.Lz4Block.FARTHEST_OFFSET;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.MINIMUM_MATCH;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.PREFIX_INDEX;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.SHORTEST_COMPRESSED_INPUT;

final class Lz4MidCompressor {

    private static final int HASH_LOG = 14;
    private static final int HASHED_LENGTH = 8;
    private static final long SEVEN_OCTET_PRIME = 58295818150454627L;
    private static final int FOUR_OCTET_PRIME = 0x9E3779B1;
    private static final int SKIP_STRENGTH = 9;

    private final Lz4Block block;
    private final byte[] input;
    private final int[] fourOctetTable = new int[1 << HASH_LOG];
    private final int[] eightOctetTable = new int[1 << HASH_LOG];
    private int matchLength;
    private int matchDistance;

    private Lz4MidCompressor(byte[] input) {
        block = new Lz4Block(input);
        this.input = input;
    }

    static byte[] compress(byte[] input) {
        return new Lz4MidCompressor(input).compress();
    }

    private byte[] compress() {
        if (input.length < SHORTEST_COMPRESSED_INPUT) {
            return block.finishWithLastLiterals();
        }
        int hashLimitIndex = block.indexOf(block.inputEnd - HASHED_LENGTH);
        while (block.at <= block.matchFindLimit) {
            int index = block.indexOf(block.at);
            if (findLongMatch(index) || findShortMatch(index)) {
                encodeMatchFoundAt(index, hashLimitIndex);
            } else {
                block.at += 1 + ((block.at - block.anchor) >> SKIP_STRENGTH);
            }
        }
        return block.finishWithLastLiterals();
    }

    private boolean findLongMatch(int index) {
        int hash = hashEight(block.at);
        int candidate = eightOctetTable[hash];
        eightOctetTable[hash] = index;
        if (index - candidate > FARTHEST_OFFSET || candidate < PREFIX_INDEX) {
            return false;
        }
        matchLength = Lz4Octets.countMatching(input, block.at, candidate - PREFIX_INDEX, block.matchLimit);
        matchDistance = index - candidate;
        return matchLength >= MINIMUM_MATCH;
    }

    private boolean findShortMatch(int index) {
        int hash = hashFour(block.at);
        int candidate = fourOctetTable[hash];
        fourOctetTable[hash] = index;
        if (index - candidate > FARTHEST_OFFSET || candidate < PREFIX_INDEX) {
            return false;
        }
        matchLength = Lz4Octets.countMatching(input, block.at, candidate - PREFIX_INDEX, block.matchLimit);
        if (matchLength < MINIMUM_MATCH) {
            return false;
        }
        matchDistance = index - candidate;
        preferLongerMatchOneOctetOn(index);
        return true;
    }

    private void preferLongerMatchOneOctetOn(int index) {
        int hash = hashEight(block.at + 1);
        int candidate = eightOctetTable[hash];
        int distance = index + 1 - candidate;
        if (distance > FARTHEST_OFFSET || candidate < PREFIX_INDEX || block.at >= block.matchFindLimit) {
            return;
        }
        int longer = Lz4Octets.countMatching(input, block.at + 1, candidate - PREFIX_INDEX, block.matchLimit);
        if (longer > matchLength) {
            eightOctetTable[hash] = index + 1;
            block.at++;
            matchLength = longer;
            matchDistance = distance;
        }
    }

    private void encodeMatchFoundAt(int index, int hashLimitIndex) {
        while (block.at > block.anchor && block.at > matchDistance
                && input[block.at - 1] == input[block.at - matchDistance - 1]) {
            block.at--;
            matchLength++;
        }
        addEight(block.at + 1, index + 1);
        addEight(block.at + 2, index + 2);
        addFour(block.at + 1, index + 1);
        block.writeSequence(matchLength, matchDistance);
        int endIndex = block.indexOf(block.at);
        if (endIndex - 2 < hashLimitIndex) {
            if (block.at > 5) {
                addEight(block.at - 5, endIndex - 5);
            }
            addEight(block.at - 3, endIndex - 3);
            addEight(block.at - 2, endIndex - 2);
            addFour(block.at - 2, endIndex - 2);
            addFour(block.at - 1, endIndex - 1);
        }
    }

    private void addEight(int position, int index) {
        eightOctetTable[hashEight(position)] = index;
    }

    private void addFour(int position, int index) {
        fourOctetTable[hashFour(position)] = index;
    }

    private int hashFour(int position) {
        return (Lz4Octets.read32(input, position) * FOUR_OCTET_PRIME) >>> (32 - HASH_LOG);
    }

    private int hashEight(int position) {
        return (int) (((Lz4Octets.read64(input, position) << 8) * SEVEN_OCTET_PRIME) >>> (64 - HASH_LOG));
    }
}
