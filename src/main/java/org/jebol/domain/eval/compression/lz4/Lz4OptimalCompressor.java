package org.jebol.domain.eval.compression.lz4;

import static org.jebol.domain.eval.compression.lz4.Lz4Block.MINIMUM_MATCH;

final class Lz4OptimalCompressor {

    private static final int POSITIONS = 1 << 12;
    private static final int TRAILING_LITERALS = 3;
    private static final int RUN_MASK = 15;
    private static final int MATCH_LENGTH_MASK = 15;
    private static final int LITERAL = 1;

    private final Lz4Block block;
    private final Lz4HashChain chain;
    private final int attempts;
    private final int sufficientLength;
    private final boolean updatesFully;
    private final int[] price = new int[POSITIONS + TRAILING_LITERALS];
    private final int[] offset = new int[POSITIONS + TRAILING_LITERALS];
    private final int[] matchLength = new int[POSITIONS + TRAILING_LITERALS];
    private final int[] literalLength = new int[POSITIONS + TRAILING_LITERALS];
    private int lastMatchPosition;

    private Lz4OptimalCompressor(byte[] input, int attempts, int targetLength, boolean updatesFully) {
        block = new Lz4Block(input);
        chain = new Lz4HashChain(input);
        this.attempts = attempts;
        sufficientLength = Math.min(targetLength, POSITIONS - 1);
        this.updatesFully = updatesFully;
    }

    static byte[] compress(byte[] input, int attempts, int targetLength, boolean updatesFully) {
        return new Lz4OptimalCompressor(input, attempts, targetLength, updatesFully).compress();
    }

    private byte[] compress() {
        while (block.at <= block.matchFindLimit) {
            Lz4Match first = chain.findLongerMatch(block.at, block.matchLimit, MINIMUM_MATCH - 1, attempts);
            if (first.length() == 0) {
                block.at++;
            } else if (first.length() > sufficientLength) {
                block.writeSequence(first.length(), first.offset());
            } else {
                parseFrom(first);
            }
        }
        return block.finishWithLastLiterals();
    }

    private void parseFrom(Lz4Match first) {
        int literals = block.at - block.anchor;
        for (int position = 0; position < MINIMUM_MATCH; position++) {
            record(position, LITERAL, 0, literals + position, literalsPrice(literals + position));
        }
        for (int length = MINIMUM_MATCH; length <= first.length(); length++) {
            record(length, length, first.offset(), literals, sequencePrice(literals, length));
        }
        lastMatchPosition = first.length();
        completeWithTrailingLiterals();
        for (int current = 1; current < lastMatchPosition; current++) {
            int at = block.at + current;
            if (at > block.matchFindLimit) {
                break;
            }
            if (isNotWorthSearchingAt(current)) {
                continue;
            }
            int shortest = updatesFully ? MINIMUM_MATCH - 1 : lastMatchPosition - current;
            Lz4Match found = chain.findLongerMatch(at, block.matchLimit, shortest, attempts);
            if (found.length() == 0) {
                continue;
            }
            if (found.length() > sufficientLength || found.length() + current >= POSITIONS) {
                lastMatchPosition = current + 1;
                encodeChosenPath(current, found.length(), found.offset());
                return;
            }
            offerLiteralsAt(current);
            offerMatchAt(current, found);
            completeWithTrailingLiterals();
        }
        int bestLength = matchLength[lastMatchPosition];
        encodeChosenPath(lastMatchPosition - bestLength, bestLength, offset[lastMatchPosition]);
    }

    private boolean isNotWorthSearchingAt(int current) {
        boolean nextIsNoDearer = price[current + 1] <= price[current];
        if (updatesFully) {
            return nextIsNoDearer && price[current + MINIMUM_MATCH] < price[current] + 3;
        }
        return nextIsNoDearer;
    }

    private void offerLiteralsAt(int current) {
        int baseLiterals = literalLength[current];
        for (int added = 1; added < MINIMUM_MATCH; added++) {
            int cost = price[current] - literalsPrice(baseLiterals) + literalsPrice(baseLiterals + added);
            int position = current + added;
            if (cost < price[position]) {
                record(position, LITERAL, 0, baseLiterals + added, cost);
            }
        }
    }

    private void offerMatchAt(int current, Lz4Match found) {
        for (int length = MINIMUM_MATCH; length <= found.length(); length++) {
            int position = current + length;
            int literals;
            int cost;
            if (matchLength[current] == LITERAL) {
                literals = literalLength[current];
                cost = (current > literals ? price[current - literals] : 0) + sequencePrice(literals, length);
            } else {
                literals = 0;
                cost = price[current] + sequencePrice(0, length);
            }
            if (position > lastMatchPosition + TRAILING_LITERALS || cost <= price[position]) {
                if (length == found.length() && lastMatchPosition < position) {
                    lastMatchPosition = position;
                }
                record(position, length, found.offset(), literals, cost);
            }
        }
    }

    private void completeWithTrailingLiterals() {
        for (int added = 1; added <= TRAILING_LITERALS; added++) {
            record(lastMatchPosition + added, LITERAL, 0, added,
                    price[lastMatchPosition] + literalsPrice(added));
        }
    }

    private void record(int position, int length, int distance, int literals, int cost) {
        matchLength[position] = length;
        offset[position] = distance;
        literalLength[position] = literals;
        price[position] = cost;
    }

    private void encodeChosenPath(int current, int bestLength, int bestOffset) {
        int candidate = current;
        int selectedLength = bestLength;
        int selectedOffset = bestOffset;
        while (true) {
            int nextLength = matchLength[candidate];
            int nextOffset = offset[candidate];
            matchLength[candidate] = selectedLength;
            offset[candidate] = selectedOffset;
            selectedLength = nextLength;
            selectedOffset = nextOffset;
            if (nextLength > candidate) {
                break;
            }
            candidate -= nextLength;
        }
        int position = 0;
        while (position < lastMatchPosition) {
            int length = matchLength[position];
            if (length == LITERAL) {
                block.at++;
                position++;
                continue;
            }
            position += length;
            block.writeSequence(length, offset[position - length]);
        }
    }

    private static int literalsPrice(int literals) {
        return literals >= RUN_MASK ? literals + 1 + (literals - RUN_MASK) / 255 : literals;
    }

    private static int sequencePrice(int literals, int length) {
        int cost = 3 + literalsPrice(literals);
        if (length >= MATCH_LENGTH_MASK + MINIMUM_MATCH) {
            cost += 1 + (length - (MATCH_LENGTH_MASK + MINIMUM_MATCH)) / 255;
        }
        return cost;
    }
}
