package org.jebol.domain.eval.compression.lz4;

import static org.jebol.domain.eval.compression.lz4.Lz4Block.FARTHEST_OFFSET;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.MINIMUM_MATCH;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.PREFIX_INDEX;

final class Lz4HashChain {

    private static final int HASH_LOG = 15;
    private static final int HASH_PRIME = 0x9E3779B1;
    private static final int CHAIN_MASK = 0xFFFF;
    private static final int CHAIN_SWAP_TRIGGER = 4;

    private final byte[] input;
    private final int[] heads = new int[1 << HASH_LOG];
    private final char[] chain = new char[CHAIN_MASK + 1];
    private int nextToUpdate = PREFIX_INDEX;

    Lz4HashChain(byte[] input) {
        this.input = input;
    }

    Lz4Match findBestMatch(int at, int highLimit, int attempts, boolean analysePatterns) {
        return findWiderMatch(at, at, highLimit, MINIMUM_MATCH - 1, attempts, analysePatterns, false);
    }

    Lz4Match findLongerMatch(int at, int highLimit, int shortest, int attempts) {
        Lz4Match found = findWiderMatch(at, at, highLimit, shortest, attempts, true, true);
        return found.length() <= shortest ? Lz4Match.NONE : found;
    }

    Lz4Match findWiderMatch(int at, int lowLimit, int highLimit, int longestSoFar,
                            int attempts, boolean analysePatterns, boolean swapChains) {
        return new Search(at, lowLimit, highLimit, longestSoFar, attempts, analysePatterns, swapChains).run();
    }

    private void insertUpTo(int at) {
        int target = at + PREFIX_INDEX;
        for (int index = nextToUpdate; index < target; index++) {
            int hash = hashAt(index - PREFIX_INDEX);
            chain[index & CHAIN_MASK] = (char) Math.min(index - heads[hash], FARTHEST_OFFSET);
            heads[hash] = index;
        }
        nextToUpdate = target;
    }

    private int hashAt(int position) {
        return (Lz4Octets.read32(input, position) * HASH_PRIME) >>> (32 - HASH_LOG);
    }

    private int chainAt(int index) {
        return chain[index & CHAIN_MASK];
    }

    private static boolean isClearOfPrefixEnd(int index) {
        return Integer.compareUnsigned(PREFIX_INDEX - 1 - index, 3) >= 0;
    }

    private enum Repeat { UNTESTED, NOT, CONFIRMED }

    private final class Search {

        private final int at;
        private final int lowLimit;
        private final int highLimit;
        private final int index;
        private final int lowestIndex;
        private final int lookBack;
        private final int pattern;
        private final boolean analysePatterns;
        private final boolean swapChains;
        private int attempts;
        private int longest;
        private int offset;
        private int back;
        private int chainPosition;
        private Repeat repeat = Repeat.UNTESTED;
        private int sourcePatternLength;
        private int candidate;

        Search(int at, int lowLimit, int highLimit, int longestSoFar,
               int attempts, boolean analysePatterns, boolean swapChains) {
            this.at = at;
            this.lowLimit = lowLimit;
            this.highLimit = highLimit;
            this.longest = longestSoFar;
            this.attempts = attempts;
            this.analysePatterns = analysePatterns;
            this.swapChains = swapChains;
            index = at + PREFIX_INDEX;
            boolean withinStartDistance = PREFIX_INDEX + FARTHEST_OFFSET + 1 > index;
            lowestIndex = withinStartDistance ? PREFIX_INDEX : index - FARTHEST_OFFSET;
            lookBack = at - lowLimit;
            pattern = Lz4Octets.read32(input, at);
        }

        Lz4Match run() {
            insertUpTo(at);
            candidate = heads[hashAt(at)];
            while (candidate >= lowestIndex && attempts > 0) {
                attempts--;
                int matchLength = measureCandidate();
                if (swapChains && matchLength == longest && candidate + longest <= index) {
                    int distance = farthestNextInChain();
                    if (distance > 1) {
                        if (distance > candidate) {
                            break;
                        }
                        candidate -= distance;
                        continue;
                    }
                }
                Step step = analysePatterns && chainAt(candidate) == 1 && chainPosition == 0
                        ? followRepeatedPattern()
                        : Step.FOLLOW_CHAIN;
                if (step == Step.STOP) {
                    break;
                }
                if (step == Step.FOLLOW_CHAIN) {
                    candidate -= chainAt(candidate + chainPosition);
                }
            }
            return new Lz4Match(longest, offset, back);
        }

        private int measureCandidate() {
            int match = candidate - PREFIX_INDEX;
            if (Lz4Octets.read16(input, lowLimit + longest - 1)
                    != Lz4Octets.read16(input, match - lookBack + longest - 1)
                    || Lz4Octets.read32(input, match) != pattern) {
                return 0;
            }
            int foundBack = lookBack != 0 ? Lz4Octets.countBack(input, at, match, lowLimit, 0) : 0;
            int matchLength = MINIMUM_MATCH
                    + Lz4Octets.countMatching(input, at + MINIMUM_MATCH, match + MINIMUM_MATCH, highLimit)
                    - foundBack;
            if (matchLength > longest) {
                longest = matchLength;
                offset = index - candidate;
                back = foundBack;
            }
            return matchLength;
        }

        private int farthestNextInChain() {
            int distanceToNext = 1;
            int end = longest - MINIMUM_MATCH + 1;
            int step;
            int acceleration = 1 << CHAIN_SWAP_TRIGGER;
            for (int position = 0; position < end; position += step) {
                int distance = chainAt(candidate + position);
                step = acceleration++ >> CHAIN_SWAP_TRIGGER;
                if (distance > distanceToNext) {
                    distanceToNext = distance;
                    chainPosition = position;
                    acceleration = 1 << CHAIN_SWAP_TRIGGER;
                }
            }
            return distanceToNext;
        }

        private Step followRepeatedPattern() {
            int previous = candidate - 1;
            if (repeat == Repeat.UNTESTED) {
                testForRepetition();
            }
            if (repeat != Repeat.CONFIRMED || previous < lowestIndex || !isClearOfPrefixEnd(previous)) {
                return Step.FOLLOW_CHAIN;
            }
            int match = previous - PREFIX_INDEX;
            if (Lz4Octets.read32(input, match) != pattern) {
                return Step.FOLLOW_CHAIN;
            }
            int forward = Lz4Octets.countPattern(input, match + 4, highLimit, pattern) + 4;
            int backward = Lz4Octets.countPatternBackwards(input, match, 0, pattern);
            backward = previous - Math.max(previous - backward, lowestIndex);
            int segment = backward + forward;
            if (segment >= sourcePatternLength && forward <= sourcePatternLength) {
                int moved = previous + forward - sourcePatternLength;
                candidate = isClearOfPrefixEnd(moved) ? moved : PREFIX_INDEX;
                return Step.ALREADY_MOVED;
            }
            int moved = previous - backward;
            if (!isClearOfPrefixEnd(moved)) {
                candidate = PREFIX_INDEX;
                return Step.ALREADY_MOVED;
            }
            candidate = moved;
            if (lookBack != 0) {
                return Step.ALREADY_MOVED;
            }
            int longestPossible = Math.min(segment, sourcePatternLength);
            if (longest < longestPossible) {
                if (index - candidate > FARTHEST_OFFSET) {
                    return Step.STOP;
                }
                longest = longestPossible;
                offset = index - candidate;
            }
            int distanceToNextPattern = chainAt(candidate);
            if (distanceToNextPattern > candidate) {
                return Step.STOP;
            }
            candidate -= distanceToNextPattern;
            return Step.ALREADY_MOVED;
        }

        private void testForRepetition() {
            boolean repeats = (pattern & 0xFFFF) == (pattern >>> 16) && (pattern & 0xFF) == (pattern >>> 24);
            if (repeats) {
                repeat = Repeat.CONFIRMED;
                sourcePatternLength = Lz4Octets.countPattern(input, at + 4, highLimit, pattern) + 4;
            } else {
                repeat = Repeat.NOT;
            }
        }
    }

    private enum Step { FOLLOW_CHAIN, ALREADY_MOVED, STOP }
}
