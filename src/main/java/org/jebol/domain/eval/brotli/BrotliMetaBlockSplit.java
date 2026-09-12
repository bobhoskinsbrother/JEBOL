package org.jebol.domain.eval.brotli;

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

    int[] distanceContextMap = new int[0];
}
