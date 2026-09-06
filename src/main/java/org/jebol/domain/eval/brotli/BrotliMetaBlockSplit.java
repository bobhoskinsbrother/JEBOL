package org.jebol.domain.eval.brotli;

/**
 * Everything a meta-block needs written in its header: how each of the three
 * alphabets is divided into stretches, the histogram behind each stretch, and
 * which histogram a given context maps to.
 *
 * <p>{@code MetaBlockSplit} in {@code metablock.h}. An empty context map means
 * every context of a block type shares that type's one histogram, which the
 * writer has a shorter form for.
 *
 * <p>Only literals get a context map here. Distances can carry one in the
 * format, but nothing below quality ten builds one, so there is no field for it.
 */
final class BrotliMetaBlockSplit {

    final BrotliBlockSplit literals = new BrotliBlockSplit();
    final BrotliBlockSplit commands = new BrotliBlockSplit();
    final BrotliBlockSplit distances = new BrotliBlockSplit();

    BrotliHistogram[] literalHistograms = new BrotliHistogram[0];
    int howManyLiteralHistograms;

    BrotliHistogram[] commandHistograms = new BrotliHistogram[0];
    int howManyCommandHistograms;

    BrotliHistogram[] distanceHistograms = new BrotliHistogram[0];
    int howManyDistanceHistograms;

    int[] literalContextMap = new int[0];

    /**
     * Which histogram each of a block type's four distance contexts uses.
     *
     * <p>Empty below quality ten, where every context of a type shares one.
     */
    int[] distanceContextMap = new int[0];
}
