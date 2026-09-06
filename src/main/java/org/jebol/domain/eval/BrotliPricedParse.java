package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * The cheapest way through the input, rather than the first good one.
 *
 * <p>{@code backward_references_hq.c}. Levels two to nine walk forward taking
 * matches as they find them. These two build a table of the cheapest way of
 * reaching every position, filling it left to right, and then walk back from
 * the end to read off the commands. A shorter match now can win if it leaves a
 * better one available afterwards, which is the whole difference.
 *
 * <p>The table would be far too slow filled honestly, so three things prune it.
 * Only the eight cheapest starting positions are kept, and only the first of
 * them is tried at level ten and the first five at eleven. A copy longer than a
 * hundred and fifty bytes -- three hundred and twenty five at level eleven --
 * is taken as read rather than compared against shorter ones. And a position
 * already reachable more cheaply than any command could manage is skipped.
 *
 * <p>Level eleven then does all of it twice: once with costs guessed from the
 * data, then again with costs measured from the commands the first pass wrote.
 */
final class BrotliPricedParse {

    private static final int LONG_ENOUGH_TO_STOP_COMPARING_AT_TEN = 150;
    private static final int LONG_ENOUGH_TO_STOP_COMPARING_AT_ELEVEN = 325;
    private static final int LONG_ENOUGH_TO_SKIP_AHEAD = 16384;
    private static final int HOW_MANY_STARTS_ARE_KEPT = 8;

    private static final int[] WHICH_RECENT_DISTANCE = {
            0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1,
    };
    private static final int[] HOW_FAR_OFF_IT_IS = {
            0, 0, 0, 0, -1, 1, -2, 2, -3, 3, -1, 1, -2, 2, -3, 3,
    };

    private final int howManyBytes;
    private final int[] length;
    private final int[] distance;
    private final int[] shortCodeAndInsertLength;

    /**
     * Cost while the position is still ahead, then the shortcut once it is
     * behind, then the step to the next command once the walk back has run.
     *
     * <p>The C keeps all three in one union and so does this, because the
     * overwriting is deliberate: a position's cost is finished with at the
     * moment its shortcut is worked out.
     */
    private final int[] costThenShortcutThenNext;

    private BrotliPricedParse(int howManyBytes) {
        this.howManyBytes = howManyBytes;
        this.length = new int[howManyBytes + 1];
        this.distance = new int[howManyBytes + 1];
        this.shortCodeAndInsertLength = new int[howManyBytes + 1];
        this.costThenShortcutThenNext = new int[howManyBytes + 1];
    }

    private void forgetEverything() {
        Arrays.fill(length, 1);
        Arrays.fill(distance, 0);
        Arrays.fill(shortCodeAndInsertLength, 0);
        Arrays.fill(costThenShortcutThenNext,
                Float.floatToRawIntBits(BrotliCostModel.UNREACHABLE));
    }

    private float costAt(int position) {
        return Float.intBitsToFloat(costThenShortcutThenNext[position]);
    }

    private void costIs(int position, float cost) {
        costThenShortcutThenNext[position] = Float.floatToRawIntBits(cost);
    }

    private int copyLengthAt(int position) {
        return length[position] & 0x1FFFFFF;
    }

    private int lengthCodeAt(int position) {
        return copyLengthAt(position) + 9 - (length[position] >>> 25);
    }

    private int insertLengthAt(int position) {
        return shortCodeAndInsertLength[position] & 0x7FFFFFF;
    }

    private int distanceCodeAt(int position) {
        int shortCode = shortCodeAndInsertLength[position] >>> 27;
        return shortCode == 0
                ? distance[position] + BrotliCommand.DISTANCE_SHORT_CODES - 1
                : shortCode - 1;
    }

    private int commandLengthAt(int position) {
        return copyLengthAt(position) + insertLengthAt(position);
    }

    private void record(int position, int startedAt, int copyLength,
            int lengthCode, long howFarBack, int shortCode, float cost) {

        int landing = position + copyLength;
        length[landing] = copyLength | ((copyLength + 9 - lengthCode) << 25);
        distance[landing] = (int) howFarBack;
        shortCodeAndInsertLength[landing] =
                (shortCode << 27) | (position - startedAt);
        costIs(landing, cost);
    }

    /**
     * The eight cheapest places a command could have started, cheapest first.
     *
     * <p>Cheapest means the least it costs to reach that position beyond what
     * writing everything up to it as literals would cost, so a position that is
     * expensive in absolute terms can still be a good place to start from.
     */
    private static final class Starts {

        private final int[] position = new int[HOW_MANY_STARTS_ARE_KEPT];
        private final int[][] recentDistances = new int[HOW_MANY_STARTS_ARE_KEPT][4];
        private final float[] savedAgainstLiterals = new float[HOW_MANY_STARTS_ARE_KEPT];
        private final float[] cost = new float[HOW_MANY_STARTS_ARE_KEPT];
        private int howManyPushed;

        int size() {
            return Math.min(howManyPushed, HOW_MANY_STARTS_ARE_KEPT);
        }

        int slotFor(int which) {
            return (which - howManyPushed) & 7;
        }

        void push(int where, float itsCost, float difference, int[] distances) {
            int at = ~(howManyPushed++) & 7;
            position[at] = where;
            cost[at] = itsCost;
            savedAgainstLiterals[at] = difference;
            System.arraycopy(distances, 0, recentDistances[at], 0, 4);
            int howMany = size();
            for (int step = 1; step < howMany; step++) {
                int here = at & 7;
                int next = (at + 1) & 7;
                if (savedAgainstLiterals[here] > savedAgainstLiterals[next]) {
                    swap(here, next);
                }
                at++;
            }
        }

        private void swap(int one, int other) {
            int wherePosition = position[one];
            position[one] = position[other];
            position[other] = wherePosition;
            int[] whereDistances = recentDistances[one];
            recentDistances[one] = recentDistances[other];
            recentDistances[other] = whereDistances;
            float whereDifference = savedAgainstLiterals[one];
            savedAgainstLiterals[one] = savedAgainstLiterals[other];
            savedAgainstLiterals[other] = whereDifference;
            float whereCost = cost[one];
            cost[one] = cost[other];
            cost[other] = whereCost;
        }
    }

    /**
     * Where the last four distances would stand if the cheapest path so far
     * were the one taken.
     *
     * <p>The shortcut chain skips straight back over commands that did not
     * change the recent distances -- a dictionary word, or a copy that reused
     * the last distance -- so following it four times gives the four that would
     * be in the reader's cache.
     */
    private void recentDistancesAt(int position, int[] startingFrom, int[] into) {
        int filled = 0;
        int walk = costThenShortcutThenNext[position];
        while (filled < 4 && walk > 0) {
            int insertLength = insertLengthAt(walk);
            int copyLength = copyLengthAt(walk);
            into[filled++] = distance[walk];
            walk = costThenShortcutThenNext[walk - copyLength - insertLength];
        }
        for (int each = 0; filled < 4; filled++, each++) {
            into[filled] = startingFrom[each];
        }
    }

    private int shortcutFor(int blockStart, int position, int furthestBack) {
        if (position == 0) {
            return 0;
        }
        int copyLength = copyLengthAt(position);
        int insertLength = insertLengthAt(position);
        long howFarBack = Integer.toUnsignedLong(distance[position]);
        if (howFarBack + copyLength <= (long) blockStart + position
                && howFarBack <= furthestBack
                && distanceCodeAt(position) > 0) {
            return position;
        }
        return costThenShortcutThenNext[position - copyLength - insertLength];
    }

    /**
     * Works out a position's shortcut and, if it is worth starting from, adds
     * it to the queue. Doing so destroys its cost, which is why the cost is
     * read out first.
     */
    private void evaluate(int blockStart, int position, int furthestBack,
            int[] startingDistances, BrotliCostModel model, Starts starts) {

        float itsCost = costAt(position);
        costThenShortcutThenNext[position] =
                shortcutFor(blockStart, position, furthestBack);
        if (itsCost <= model.literalCost(0, position)) {
            int[] distances = new int[4];
            recentDistancesAt(position, startingDistances, distances);
            starts.push(position, itsCost,
                    itsCost - model.literalCost(0, position), distances);
        }
    }

    /**
     * The shortest copy that could possibly improve on what is already known.
     *
     * <p>If everything within reach is already cheaper than the best a command
     * from here could manage, there is no point offering short copies at all.
     * The floor rises by a bit at each copy-length bracket, because a longer
     * copy needs another extra bit to write.
     */
    private int shortestCopyWorthTrying(float startCost, int position) {
        float floor = startCost;
        int copyLength = 2;
        int bracketWidth = 4;
        int nextBracket = 10;
        while (position + copyLength <= howManyBytes
                && costAt(position + copyLength) <= floor) {
            copyLength++;
            if (copyLength == nextBracket) {
                floor += 1.0f;
                nextBracket += bracketWidth;
                bracketWidth *= 2;
            }
        }
        return copyLength;
    }

    /**
     * Offers every match at this position to every starting position kept, and
     * records the ones that beat what was already known.
     *
     * <p>Answers the longest copy it recorded, which tells the caller how far it
     * may skip ahead.
     */
    private int offerEverythingAt(byte[] data, int mask, int blockStart,
            int position, int quality, int furthestBack, int[] startingDistances,
            int howManyMatches, BrotliMatches matches, BrotliDistances distances,
            BrotliCostModel model, Starts starts) {

        int here = blockStart + position;
        int hereMasked = here & mask;
        long maxDistance = Math.min(here, furthestBack);
        long dictionaryStart = Math.min(here, furthestBack);
        int mostLeft = howManyBytes - position;
        int longEnoughToTakeAsRead = quality <= 10
                ? LONG_ENOUGH_TO_STOP_COMPARING_AT_TEN
                : LONG_ENOUGH_TO_STOP_COMPARING_AT_ELEVEN;
        int howManyStartsToTry = quality <= 10 ? 1 : 5;
        int longest = 0;

        evaluate(blockStart, position, furthestBack, startingDistances, model, starts);

        int shortestWorthTrying;
        {
            int slot = starts.slotFor(0);
            float floor = starts.cost[slot] + model.cheapestCommand()
                    + model.literalCost(starts.position[slot], position);
            shortestWorthTrying = shortestCopyWorthTrying(floor, position);
        }

        for (int which = 0; which < howManyStartsToTry && which < starts.size();
                which++) {
            int slot = starts.slotFor(which);
            int start = starts.position[slot];
            int insertCode = BrotliCommand.insertLengthCode(position - start);
            float startDifference = starts.savedAgainstLiterals[slot];
            float baseCost = startDifference
                    + (float) BrotliCommand.insertExtra(insertCode)
                    + model.literalCost(0, position);

            int bestLength = shortestWorthTrying - 1;
            for (int code = 0; code < BrotliCommand.DISTANCE_SHORT_CODES
                    && bestLength < mostLeft; code++) {
                long backward = starts.recentDistances[slot][WHICH_RECENT_DISTANCE[code]]
                        + (long) HOW_FAR_OFF_IT_IS[code];
                if (hereMasked + bestLength > mask) {
                    break;
                }
                int continuation = data[hereMasked + bestLength] & 0xFF;
                if (backward <= 0 || backward > dictionaryStart
                        || backward > maxDistance || backward > here) {
                    continue;
                }
                int previous = (int) ((here - backward) & mask);
                if (previous + bestLength > mask
                        || continuation != (data[previous + bestLength] & 0xFF)) {
                    continue;
                }
                int length = BrotliMatch.matchingBytes(data, previous, hereMasked,
                        mostLeft);
                float distanceCost = baseCost + model.distanceCost(code);
                for (int copyLength = bestLength + 1; copyLength <= length;
                        copyLength++) {
                    int copyCode = BrotliCommand.copyLengthCode(copyLength);
                    int commandCode = BrotliCommand.combinedLengthCode(
                            insertCode, copyCode, code == 0);
                    float cost = (commandCode < 128 ? baseCost : distanceCost)
                            + (float) BrotliCommand.copyExtra(copyCode)
                            + model.commandCost(commandCode);
                    if (cost < costAt(position + copyLength)) {
                        record(position, start, copyLength, copyLength, backward,
                                code + 1, cost);
                        longest = Math.max(longest, copyLength);
                    }
                    bestLength = copyLength;
                }
            }

            if (which >= 2) {
                continue;
            }
            int copyLength = shortestWorthTrying;
            for (int match = 0; match < howManyMatches; match++) {
                long howFarBack = matches.distanceAt(match);
                boolean isADictionaryWord = howFarBack > dictionaryStart;
                long plainCode = howFarBack + BrotliCommand.DISTANCE_SHORT_CODES - 1;
                long encoded = BrotliCommand.encodedDistance((int) plainCode,
                        distances.directCodes(), distances.postfixBits());
                int distanceSymbol = (int) (encoded >>> 32);
                int howManyExtraBits = distanceSymbol >>> 10;
                float distanceCost = baseCost + (float) howManyExtraBits
                        + model.distanceCost(distanceSymbol & 0x3FF);

                int longestHere = matches.lengthAt(match);
                if (copyLength < longestHere
                        && (isADictionaryWord || longestHere > longEnoughToTakeAsRead)) {
                    copyLength = longestHere;
                }
                for (; copyLength <= longestHere; copyLength++) {
                    int lengthCode = isADictionaryWord
                            ? matches.lengthCodeAt(match) : copyLength;
                    int copyCode = BrotliCommand.copyLengthCode(lengthCode);
                    int commandCode = BrotliCommand.combinedLengthCode(
                            insertCode, copyCode, false);
                    float cost = distanceCost
                            + (float) BrotliCommand.copyExtra(copyCode)
                            + model.commandCost(commandCode);
                    if (cost < costAt(position + copyLength)) {
                        record(position, start, copyLength, lengthCode, howFarBack,
                                0, cost);
                        longest = Math.max(longest, copyLength);
                    }
                }
            }
        }
        return longest;
    }

    /**
     * Walks back from the end, turning the cheapest path into a chain of steps.
     *
     * <p>The C answers how many commands the chain has, so its caller can keep
     * a running total; here the commands are appended to a list that knows its
     * own length, so there is nothing to answer.
     */
    private void readTheAnswerBack() {
        int at = howManyBytes;
        while (insertLengthAt(at) == 0 && length[at] == 1) {
            at--;
        }
        costThenShortcutThenNext[at] = -1;
        while (at != 0) {
            int step = commandLengthAt(at);
            at -= step;
            costThenShortcutThenNext[at] = step;
        }
    }

    /**
     * Turns the chain into commands, updating the recent distances as it goes.
     *
     * <p>{@code BrotliZopfliCreateCommands}.
     */
    private void writeTheCommands(int blockStart, int windowBits,
            int[] recentDistances, int[] insertLengthCarried,
            BrotliDistances distances, BrotliCommand commands,
            long[] howManyLiterals) {

        int furthestBack = BrotliBackwardReferences.furthestBack(windowBits);
        int at = 0;
        int step = costThenShortcutThenNext[0];
        for (int which = 0; step != -1; which++) {
            int landing = at + step;
            int copyLength = copyLengthAt(landing);
            int insertLength = insertLengthAt(landing);
            at += insertLength;
            step = costThenShortcutThenNext[landing];
            if (which == 0) {
                insertLength += insertLengthCarried[0];
                insertLengthCarried[0] = 0;
            }
            long howFarBack = Integer.toUnsignedLong(distance[landing]);
            int lengthCode = lengthCodeAt(landing);
            long dictionaryStart = Math.min((long) blockStart + at, furthestBack);
            boolean isADictionaryWord = howFarBack > dictionaryStart;
            int distanceCode = distanceCodeAt(landing);
            commands.add(insertLength, copyLength, lengthCode - copyLength,
                    distanceCode, distances.directCodes(), distances.postfixBits());
            if (!isADictionaryWord && distanceCode > 0) {
                recentDistances[3] = recentDistances[2];
                recentDistances[2] = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = (int) howFarBack;
            }
            howManyLiterals[0] += insertLength;
            at += copyLength;
        }
        insertLengthCarried[0] += howManyBytes - at;
    }

    /**
     * Level ten: one pass, asking the hasher for matches as it goes.
     *
     * <p>{@code BrotliZopfliComputeShortestPath} followed by
     * {@code BrotliZopfliCreateCommands}.
     */
    static void findAllForTen(byte[] data, int mask, int blockStart,
            int howManyBytes, int windowBits, BrotliBinaryTreeHasher hasher,
            int[] recentDistances, int[] insertLengthCarried,
            BrotliDistances distances, BrotliCommand commands,
            long[] howManyLiterals) {

        BrotliPricedParse parse = new BrotliPricedParse(howManyBytes);
        parse.forgetEverything();
        parse.length[0] = 0;
        parse.costIs(0, 0.0f);

        BrotliCostModel model =
                new BrotliCostModel(howManyBytes, distances.alphabetSize());
        model.guessFromTheDataAlone(data, blockStart, mask);

        int furthestBack = BrotliBackwardReferences.furthestBack(windowBits);
        int rememberUntil = howManyBytes >= BrotliBinaryTreeHasher.LONGEST_COMPARED
                ? blockStart + howManyBytes - BrotliBinaryTreeHasher.LONGEST_COMPARED + 1
                : blockStart;
        Starts starts = new Starts();
        BrotliMatches matches = new BrotliMatches();

        for (int at = 0; at + 3 < howManyBytes; at++) {
            int here = blockStart + at;
            int maxDistance = Math.min(here, furthestBack);
            int howManyMatches = hasher.findAll(data, mask, here,
                    howManyBytes - at, maxDistance, maxDistance,
                    BrotliDistances.FURTHEST, false, matches);
            if (howManyMatches > 0
                    && matches.lengthAt(howManyMatches - 1)
                            > LONG_ENOUGH_TO_STOP_COMPARING_AT_TEN) {
                matches.keepOnly(howManyMatches - 1);
                howManyMatches = 1;
            }
            int skip = parse.offerEverythingAt(data, mask, blockStart, at, 10,
                    furthestBack, recentDistances, howManyMatches, matches,
                    distances, model, starts);
            if (skip < LONG_ENOUGH_TO_SKIP_AHEAD) {
                skip = 0;
            }
            if (howManyMatches == 1
                    && matches.lengthAt(0) > LONG_ENOUGH_TO_STOP_COMPARING_AT_TEN) {
                skip = Math.max(matches.lengthAt(0), skip);
            }
            if (skip > 1) {
                hasher.rememberRange(data, mask, here + 1,
                        Math.min(here + skip, rememberUntil));
                skip--;
                while (skip != 0) {
                    at++;
                    if (at + 3 >= howManyBytes) {
                        break;
                    }
                    parse.evaluate(blockStart, at, furthestBack, recentDistances,
                            model, starts);
                    skip--;
                }
            }
        }
        parse.readTheAnswerBack();
        parse.writeTheCommands(blockStart, windowBits, recentDistances,
                insertLengthCarried, distances, commands, howManyLiterals);
    }

    /**
     * Level eleven: gather every match first, then parse twice.
     *
     * <p>{@code BrotliCreateHqZopfliBackwardReferences}. The second parse uses
     * costs measured from what the first one wrote, so it knows which symbols
     * are actually common in this data rather than guessing.
     */
    static void findAllForEleven(byte[] data, int mask, int blockStart,
            int howManyBytes, int windowBits, BrotliBinaryTreeHasher hasher,
            int[] recentDistances, int[] insertLengthCarried,
            BrotliDistances distances, BrotliCommand commands,
            long[] howManyLiterals) {

        int furthestBack = BrotliBackwardReferences.furthestBack(windowBits);
        int rememberUntil = howManyBytes >= BrotliBinaryTreeHasher.LONGEST_COMPARED
                ? blockStart + howManyBytes - BrotliBinaryTreeHasher.LONGEST_COMPARED + 1
                : blockStart;
        int[] howManyMatchesAt = new int[howManyBytes];
        BrotliMatches allMatches = new BrotliMatches();
        BrotliMatches here = new BrotliMatches();
        int filled = 0;

        for (int at = 0; at + 3 < howManyBytes; at++) {
            int position = blockStart + at;
            int maxDistance = Math.min(position, furthestBack);
            int found = hasher.findAll(data, mask, position, howManyBytes - at,
                    maxDistance, maxDistance, BrotliDistances.FURTHEST, true, here);
            howManyMatchesAt[at] = found;
            if (found == 0) {
                continue;
            }
            int longest = here.lengthAt(found - 1);
            if (longest > LONG_ENOUGH_TO_STOP_COMPARING_AT_ELEVEN) {
                allMatches.copyOneOverFrom(filled++, here, found - 1);
                howManyMatchesAt[at] = 1;
                hasher.rememberRange(data, mask, position + 1,
                        Math.min(position + longest, rememberUntil));
                at += longest - 1;
                continue;
            }
            for (int which = 0; which < found; which++) {
                allMatches.copyOneOverFrom(filled++, here, which);
            }
        }

        int[] startingDistances = Arrays.copyOf(recentDistances, 16);
        long startingLiterals = howManyLiterals[0];
        int startingInsertLength = insertLengthCarried[0];
        int startingCommands = commands.count();

        BrotliCostModel model =
                new BrotliCostModel(howManyBytes, distances.alphabetSize());
        for (int round = 0; round < 2; round++) {
            if (round == 0) {
                model.guessFromTheDataAlone(data, blockStart, mask);
            } else {
                model.measureFromTheCommands(data, blockStart, mask, commands,
                        startingCommands, commands.count() - startingCommands,
                        startingInsertLength);
            }
            commands.keepOnly(startingCommands);
            howManyLiterals[0] = startingLiterals;
            insertLengthCarried[0] = startingInsertLength;
            System.arraycopy(startingDistances, 0, recentDistances, 0, 16);

            BrotliPricedParse parse = new BrotliPricedParse(howManyBytes);
            parse.forgetEverything();
            parse.length[0] = 0;
            parse.costIs(0, 0.0f);
            parse.iterate(data, mask, blockStart, howManyBytes, furthestBack,
                    recentDistances, howManyMatchesAt, allMatches, distances,
                    model);
            parse.writeTheCommands(blockStart, windowBits, recentDistances,
                    insertLengthCarried, distances, commands, howManyLiterals);
        }
    }

    /**
     * One pass over matches that were gathered beforehand.
     *
     * <p>{@code ZopfliIterate}. Unlike level ten's pass this never asks the
     * hasher for anything, because every match is already in hand.
     */
    private void iterate(byte[] data, int mask, int blockStart, int howManyBytes,
            int furthestBack, int[] recentDistances, int[] howManyMatchesAt,
            BrotliMatches allMatches, BrotliDistances distances,
            BrotliCostModel model) {

        Starts starts = new Starts();
        BrotliMatches window = new BrotliMatches();
        int matchesSoFar = 0;
        for (int at = 0; at + 3 < howManyBytes; at++) {
            int howMany = howManyMatchesAt[at];
            for (int which = 0; which < howMany; which++) {
                window.copyOneOverFrom(which, allMatches, matchesSoFar + which);
            }
            int skip = offerEverythingAt(data, mask, blockStart, at, 11,
                    furthestBack, recentDistances, howMany, window, distances,
                    model, starts);
            if (skip < LONG_ENOUGH_TO_SKIP_AHEAD) {
                skip = 0;
            }
            matchesSoFar += howMany;
            if (howMany == 1 && allMatches.lengthAt(matchesSoFar - 1)
                    > LONG_ENOUGH_TO_STOP_COMPARING_AT_ELEVEN) {
                skip = Math.max(allMatches.lengthAt(matchesSoFar - 1), skip);
            }
            if (skip > 1) {
                skip--;
                while (skip != 0) {
                    at++;
                    if (at + 3 >= howManyBytes) {
                        break;
                    }
                    evaluate(blockStart, at, furthestBack, recentDistances,
                            model, starts);
                    matchesSoFar += howManyMatchesAt[at];
                    skip--;
                }
            }
        }
        readTheAnswerBack();
    }
}
