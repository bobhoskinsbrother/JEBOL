package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliClustering {

    private static final int HOW_MANY_AT_A_TIME = 64;
    private static final int MOST_HISTOGRAMS_ALLOWED = 256;

    private BrotliClustering() {
    }

    private static final class Pair {
        int first;
        int second;
        double costOfTheMerged;
        double whatMergingSaves;

        void copyFrom(Pair other) {
            first = other.first;
            second = other.second;
            costOfTheMerged = other.costOfTheMerged;
            whatMergingSaves = other.whatMergingSaves;
        }

        boolean isWorseThan(Pair other) {
            if (whatMergingSaves != other.whatMergingSaves) {
                return whatMergingSaves > other.whatMergingSaves;
            }
            return (second - first) > (other.second - other.first);
        }
    }

    private static double whatTheMapSaves(int firstSize, int secondSize) {
        int together = firstSize + secondSize;
        return firstSize * BrotliCodes.fastLog2(firstSize)
                + secondSize * BrotliCodes.fastLog2(secondSize)
                - together * BrotliCodes.fastLog2(together);
    }

    static final class Queue {

        private final Pair[] pairs;
        private final Pair candidate = new Pair();
        private int howMany;

        Queue(int room) {
            pairs = new Pair[room + 1];
            for (int each = 0; each < pairs.length; each++) {
                pairs[each] = new Pair();
            }
        }

        void forgetEverything() {
            howMany = 0;
        }

        void offer(BrotliHistogram[] histograms, BrotliHistogram scratch,
                   int[] clusterSize, int firstGiven, int secondGiven,
                   int room, int alphabetSize) {

            if (firstGiven == secondGiven) {
                return;
            }
            int first = Math.min(firstGiven, secondGiven);
            int second = Math.max(firstGiven, secondGiven);
            candidate.first = first;
            candidate.second = second;
            candidate.costOfTheMerged = 0;
            candidate.whatMergingSaves =
                    0.5 * whatTheMapSaves(clusterSize[first], clusterSize[second])
                            - costOf(histograms[first])
                            - costOf(histograms[second]);

            boolean worthKeeping;
            if (histograms[first].total() == 0) {
                candidate.costOfTheMerged = costOf(histograms[second]);
                worthKeeping = true;
            } else if (histograms[second].total() == 0) {
                candidate.costOfTheMerged = costOf(histograms[first]);
                worthKeeping = true;
            } else {
                double threshold = howMany == 0
                        ? 1e99 : Math.max(0.0, pairs[0].whatMergingSaves);
                scratch.copyFrom(histograms[first]);
                scratch.addAll(histograms[second]);
                double merged = BrotliHistogramCost.of(scratch, alphabetSize);
                worthKeeping = merged < threshold - candidate.whatMergingSaves;
                if (worthKeeping) {
                    candidate.costOfTheMerged = merged;
                }
            }
            if (!worthKeeping) {
                return;
            }
            candidate.whatMergingSaves += candidate.costOfTheMerged;
            if (howMany > 0 && pairs[0].isWorseThan(candidate)) {
                if (howMany < room) {
                    pairs[howMany].copyFrom(pairs[0]);
                    howMany++;
                }
                pairs[0].copyFrom(candidate);
            } else if (howMany < room) {
                pairs[howMany].copyFrom(candidate);
                howMany++;
            }
        }

        void forgetPairsTouching(int first, int second) {
            int keptSoFar = 0;
            for (int each = 0; each < howMany; each++) {
                Pair pair = pairs[each];
                if (pair.first == first || pair.second == first
                        || pair.first == second || pair.second == second) {
                    continue;
                }
                if (pairs[0].isWorseThan(pair)) {
                    Pair wasFirst = new Pair();
                    wasFirst.copyFrom(pairs[0]);
                    pairs[0].copyFrom(pair);
                    pairs[keptSoFar].copyFrom(wasFirst);
                } else {
                    pairs[keptSoFar].copyFrom(pair);
                }
                keptSoFar++;
            }
            howMany = keptSoFar;
        }
    }

    private static double costOf(BrotliHistogram histogram) {
        return histogram.cost();
    }

    static int combine(BrotliHistogram[] histograms,
            BrotliHistogram scratch, int[] clusterSize, int[] belongsTo,
            int belongsToAt, int[] clusters, int clustersAt, Queue queue,
            int howManyClusters, int howManySymbols, int mostClusters,
            int room, int alphabetSize) {

        double stopWhenSavingIsBelow = 0.0;
        int fewestWorthHaving = 1;
        queue.forgetEverything();

        for (int one = 0; one < howManyClusters; one++) {
            for (int other = one + 1; other < howManyClusters; other++) {
                queue.offer(histograms, scratch, clusterSize,
                        clusters[clustersAt + one], clusters[clustersAt + other],
                        room, alphabetSize);
            }
        }

        int left = howManyClusters;
        while (left > fewestWorthHaving) {
            if (queue.pairs[0].whatMergingSaves >= stopWhenSavingIsBelow) {
                stopWhenSavingIsBelow = 1e99;
                fewestWorthHaving = mostClusters;
                continue;
            }
            int keep = queue.pairs[0].first;
            int drop = queue.pairs[0].second;
            histograms[keep].addAll(histograms[drop]);
            histograms[keep].costIs(queue.pairs[0].costOfTheMerged);
            clusterSize[keep] += clusterSize[drop];
            for (int each = 0; each < howManySymbols; each++) {
                if (belongsTo[belongsToAt + each] == drop) {
                    belongsTo[belongsToAt + each] = keep;
                }
            }
            for (int each = 0; each < left; each++) {
                if (clusters[clustersAt + each] == drop) {
                    System.arraycopy(clusters, clustersAt + each + 1,
                            clusters, clustersAt + each, left - each - 1);
                    break;
                }
            }
            left--;
            queue.forgetPairsTouching(keep, drop);
            for (int each = 0; each < left; each++) {
                queue.offer(histograms, scratch, clusterSize, keep,
                        clusters[clustersAt + each], room, alphabetSize);
            }
        }
        return left;
    }

    static double costOfMoving(BrotliHistogram histogram,
            BrotliHistogram candidate, BrotliHistogram scratch, int alphabetSize) {

        if (histogram.total() == 0) {
            return 0.0;
        }
        scratch.copyFrom(histogram);
        scratch.addAll(candidate);
        return BrotliHistogramCost.of(scratch, alphabetSize) - candidate.cost();
    }

    static int clusterInto(BrotliHistogram[] given, int howManyGiven,
            BrotliHistogram[] into, int[] whichOneEachUses, int alphabetSize) {

        int[] clusterSize = new int[howManyGiven];
        Arrays.fill(clusterSize, 1);
        int[] clusters = new int[howManyGiven];
        BrotliHistogram scratch = new BrotliHistogram(alphabetSize);

        for (int each = 0; each < howManyGiven; each++) {
            into[each].copyFrom(given[each]);
            into[each].costIs(BrotliHistogramCost.of(given[each], alphabetSize));
            whichOneEachUses[each] = each;
        }

        int roomForPairs = HOW_MANY_AT_A_TIME * HOW_MANY_AT_A_TIME / 2;
        Queue queue = new Queue(roomForPairs);
        int howManyClusters = 0;
        for (int at = 0; at < howManyGiven; at += HOW_MANY_AT_A_TIME) {
            int howManyHere = Math.min(howManyGiven - at, HOW_MANY_AT_A_TIME);
            for (int each = 0; each < howManyHere; each++) {
                clusters[howManyClusters + each] = at + each;
            }
            howManyClusters += combine(into, scratch, clusterSize,
                    whichOneEachUses, at, clusters, howManyClusters, queue,
                    howManyHere, howManyHere, MOST_HISTOGRAMS_ALLOWED,
                    roomForPairs, alphabetSize);
        }

        int roomForMorePairs = Math.min(64 * howManyClusters,
                (howManyClusters / 2) * howManyClusters);
        Queue wider = new Queue(Math.max(roomForMorePairs, roomForPairs));
        howManyClusters = combine(into, scratch, clusterSize, whichOneEachUses,
                0, clusters, 0, wider, howManyClusters, howManyGiven,
                MOST_HISTOGRAMS_ALLOWED, roomForMorePairs, alphabetSize);

        remap(given, howManyGiven, clusters, howManyClusters, into, scratch,
                whichOneEachUses, alphabetSize);
        return renumberNoughtUpwardInTheOrderTheyAreFirstUsed(
                into, whichOneEachUses, howManyGiven, alphabetSize);
    }

    private static void remap(BrotliHistogram[] given, int howManyGiven,
            int[] clusters, int howManyClusters, BrotliHistogram[] into,
            BrotliHistogram scratch, int[] whichOneEachUses, int alphabetSize) {

        for (int each = 0; each < howManyGiven; each++) {
            int best = each == 0 ? whichOneEachUses[0] : whichOneEachUses[each - 1];
            double bestCost = costOfMoving(given[each], into[best], scratch,
                    alphabetSize);
            for (int which = 0; which < howManyClusters; which++) {
                double cost = costOfMoving(given[each], into[clusters[which]],
                        scratch, alphabetSize);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = clusters[which];
                }
            }
            whichOneEachUses[each] = best;
        }
        for (int which = 0; which < howManyClusters; which++) {
            into[clusters[which]].clear();
        }
        for (int each = 0; each < howManyGiven; each++) {
            into[whichOneEachUses[each]].addAll(given[each]);
        }
    }

    private static int renumberNoughtUpwardInTheOrderTheyAreFirstUsed(BrotliHistogram[] into, int[] whichOneEachUses,
            int howMany, int alphabetSize) {

        int[] newName = new int[howMany];
        Arrays.fill(newName, -1);
        int nextName = 0;
        for (int each = 0; each < howMany; each++) {
            if (newName[whichOneEachUses[each]] == -1) {
                newName[whichOneEachUses[each]] = nextName;
                nextName++;
            }
        }
        BrotliHistogram[] shuffled = BrotliHistogram.freshRow(nextName, alphabetSize);
        nextName = 0;
        for (int each = 0; each < howMany; each++) {
            if (newName[whichOneEachUses[each]] == nextName) {
                shuffled[nextName].copyFrom(into[whichOneEachUses[each]]);
                nextName++;
            }
            whichOneEachUses[each] = newName[whichOneEachUses[each]];
        }
        for (int each = 0; each < nextName; each++) {
            into[each].copyFrom(shuffled[each]);
        }
        return nextName;
    }
}
