package org.jebol.domain.eval.brotli;

final class BrotliBackwardReferences {

    private static final long WORST_MATCH_WORTH_TAKING = 30 * 8L * 8L + 100;
    private static final int HOW_MUCH_BETTER_THE_NEXT_BYTE_MUST_BE = 175;
    private static final int MOST_BYTES_THAT_MAY_BE_PUT_OFF = 4;

    private BrotliBackwardReferences() {
    }

    static int furthestBack(int windowBits) {
        return (1 << windowBits) - SHORT_OF_THE_WINDOW_SO_THE_TWO_CANNOT_BE_CONFUSED;
    }

    private static final int SHORT_OF_THE_WINDOW_SO_THE_TWO_CANNOT_BE_CONFUSED = 16;

    private static int howFarApartCopiesMayBeBeforeSearchingSparsely(int quality) {
        return quality < 9 ? 64 : 512;
    }

    private static boolean searchesAheadFromNothing(int quality) {
        return quality >= 5;
    }

    record Found(int insertLengthLeftOver, int literalsWrittenIntoCommands) {
    }

    static Found findAll(byte[] ringBuffer, int mask, int position,
            int howManyBytes, int quality, int windowBits, BrotliHasher hasher,
            int[] recentDistances, int insertLengthCarriedIn,
            BrotliCommand commands) {

        int furthestBack = furthestBack(windowBits);
        int insertLength = insertLengthCarriedIn;
        int end = position + howManyBytes;
        int lookahead = hasher.howManyBytesItLooksAhead();
        int rememberUntil = howManyBytes >= lookahead
                ? position + howManyBytes - lookahead + 1
                : position;

        int searchSparselyAfter = howFarApartCopiesMayBeBeforeSearchingSparsely(quality);
        int giveUpLookingAfter = position + searchSparselyAfter;

        BrotliMatch best = new BrotliMatch();
        BrotliMatch ahead = new BrotliMatch();
        hasher.prepareDistanceCache(recentDistances);

        int literalsWrittenIntoCommands = 0;
        int at = position;
        while (at + lookahead < end) {
            int maxLength = end - at;
            int maxBackward = Math.min(at, furthestBack);
            int dictionaryDistance = Math.min(at, furthestBack);
            best.forget(WORST_MATCH_WORTH_TAKING);
            hasher.findLongestMatch(ringBuffer, mask, recentDistances, at,
                    maxLength, maxBackward, dictionaryDistance,
                    BrotliDistances.FURTHEST, best);

            if (best.score <= WORST_MATCH_WORTH_TAKING) {
                insertLength++;
                at++;
                if (at > giveUpLookingAfter) {
                    int stride = at > giveUpLookingAfter + 4 * searchSparselyAfter ? 4 : 2;
                    int margin = Math.max(lookahead - 1, stride == 4 ? 4 : 2);
                    int jumpTo = Math.min(at + (stride == 4 ? 16 : 8), end - margin);
                    for (; at < jumpTo; at += stride) {
                        hasher.remember(ringBuffer, mask, at);
                        insertLength += stride;
                    }
                }
                continue;
            }

            int putOff = 0;
            for (int lengthLeft = maxLength - 1; ; lengthLeft--) {
                ahead.forget(WORST_MATCH_WORTH_TAKING);
                ahead.length = searchesAheadFromNothing(quality)
                        ? 0
                        : Math.min(best.length - 1, lengthLeft);
                int backwardAhead = Math.min(at + 1, furthestBack);
                hasher.findLongestMatch(ringBuffer, mask, recentDistances, at + 1,
                        lengthLeft, backwardAhead, backwardAhead,
                        BrotliDistances.FURTHEST, ahead);
                if (ahead.score < best.score + HOW_MUCH_BETTER_THE_NEXT_BYTE_MUST_BE) {
                    break;
                }
                at++;
                insertLength++;
                best.length = ahead.length;
                best.distance = ahead.distance;
                best.score = ahead.score;
                best.lengthCodeDelta = ahead.lengthCodeDelta;
                putOff++;
                if (putOff >= MOST_BYTES_THAT_MAY_BE_PUT_OFF
                        || at + lookahead >= end) {
                    break;
                }
            }

            giveUpLookingAfter = (int) (at + 2 * best.length + searchSparselyAfter);
            int reachOfTheDictionary = Math.min(at, furthestBack);
            int distanceCode = distanceCodeFor(best.distance, reachOfTheDictionary,
                    recentDistances);
            if (best.distance <= reachOfTheDictionary && distanceCode > 0) {
                recentDistances[3] = recentDistances[2];
                recentDistances[2] = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = (int) best.distance;
                hasher.prepareDistanceCache(recentDistances);
            }
            commands.add(insertLength, (int) best.length, best.lengthCodeDelta,
                    distanceCode, 0, 0);
            literalsWrittenIntoCommands += insertLength;
            insertLength = 0;

            rememberTheInsideOfTheCopyButNotAllOfIt(
                    ringBuffer, mask, hasher, at, best,
                    rememberUntil);
            at += (int) best.length;
        }
        return new Found(insertLength + (end - at), literalsWrittenIntoCommands);
    }

    private static void rememberTheInsideOfTheCopyButNotAllOfIt(byte[] ringBuffer, int mask,
            BrotliHasher hasher, int at, BrotliMatch best, int rememberUntil) {

        int from = at + 2;
        int until = (int) Math.min(at + best.length, rememberUntil);
        if (best.distance < (best.length >> 2)) {
            from = (int) Math.min(until,
                    Math.max(from, at + best.length - (best.distance << 2)));
        }
        hasher.rememberRange(ringBuffer, mask, from, until);
    }

    static int distanceCodeFor(long distance, long furthestBack,
            int[] recentDistances) {

        if (distance <= furthestBack) {
            long distancePlusThree = distance + 3;
            long offsetFromLast = distancePlusThree
                    - Integer.toUnsignedLong(recentDistances[0]);
            long offsetFromTheOneBefore = distancePlusThree
                    - Integer.toUnsignedLong(recentDistances[1]);
            if (distance == Integer.toUnsignedLong(recentDistances[0])) {
                return 0;
            }
            if (distance == Integer.toUnsignedLong(recentDistances[1])) {
                return 1;
            }
            if (offsetFromLast >= 0 && offsetFromLast < 7) {
                return (int) ((0x9750468L >> (4 * offsetFromLast)) & 0xF);
            }
            if (offsetFromTheOneBefore >= 0 && offsetFromTheOneBefore < 7) {
                return (int) ((0xFDB1ACEL >> (4 * offsetFromTheOneBefore)) & 0xF);
            }
            if (distance == Integer.toUnsignedLong(recentDistances[2])) {
                return 2;
            }
            if (distance == Integer.toUnsignedLong(recentDistances[3])) {
                return 3;
            }
        }
        return (int) (distance + BrotliCommand.DISTANCE_SHORT_CODES - 1);
    }
}
