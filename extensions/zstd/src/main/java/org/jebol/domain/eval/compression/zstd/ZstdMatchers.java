package org.jebol.domain.eval.compression.zstd;

final class ZstdMatchers {

    private static final int ROW_SIZE = 16;
    private static final int UNSORTED_MARK = 1;

    private ZstdMatchers() {
    }

    static int compressBlock(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from, int size,
                             boolean isRowMatchFinderUsed) {
        boolean isExtended = state.hasExtendedDictionary();
        int strategy = state.parameters.strategy;
        switch (strategy) {
            case ZstdCompressionParameters.FAST -> {
                return isExtended ? ZstdFastMatcher.compressBlockWithDictionary(state, store, repeats, from, size)
                        : ZstdFastMatcher.compressBlock(state, store, repeats, from, size);
            }
            case ZstdCompressionParameters.DFAST -> {
                return isExtended
                        ? ZstdDoubleFastMatcher.compressBlockWithDictionary(state, store, repeats, from, size)
                        : ZstdDoubleFastMatcher.compressBlock(state, store, repeats, from, size);
            }
            case ZstdCompressionParameters.GREEDY, ZstdCompressionParameters.LAZY,
                 ZstdCompressionParameters.LAZY2, ZstdCompressionParameters.BTLAZY2 -> {
                int method = strategy == ZstdCompressionParameters.BTLAZY2 ? ZstdLazyMatcher.BINARY_TREE
                        : isRowMatchFinderUsed ? ZstdLazyMatcher.ROW_HASH : ZstdLazyMatcher.HASH_CHAIN;
                int depth = switch (strategy) {
                    case ZstdCompressionParameters.GREEDY -> 0;
                    case ZstdCompressionParameters.LAZY -> 1;
                    default -> 2;
                };
                ZstdLazyMatcher lazy = new ZstdLazyMatcher(state);
                return isExtended ? lazy.compressBlockWithDictionary(store, repeats, from, size, method, depth)
                        : lazy.compressBlock(store, repeats, from, size, method, depth);
            }
            default -> {
                ZstdOptimalMatcher optimal = new ZstdOptimalMatcher(state);
                if (strategy == ZstdCompressionParameters.BTOPT) {
                    return optimal.compressBlock(store, repeats, from, size, 0, isExtended);
                }
                if (strategy == ZstdCompressionParameters.BTULTRA2 && !isExtended) {
                    return optimal.compressBlockUltra2(store, repeats, from, size);
                }
                return optimal.compressBlock(store, repeats, from, size, 2, isExtended);
            }
        }
    }

    private static void reduceTable(ZstdIndexTable table, int size, long reducer, boolean isMarkPreserved) {
        int rows = size / ROW_SIZE;
        long threshold = reducer + ZstdMatchState.WINDOW_START_INDEX;
        int cell = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < ROW_SIZE; column++) {
                long value = table.getIndex(cell);
                long reduced;
                if (isMarkPreserved && value == UNSORTED_MARK) {
                    reduced = UNSORTED_MARK;
                } else if (value < threshold) {
                    reduced = 0;
                } else {
                    reduced = value - reducer;
                }
                table.setIndex(cell, reduced);
                cell++;
            }
        }
    }

    static void reduceIndex(ZstdMatchState state, ZstdCompressionParameters parameters, int reducer,
                            boolean isRowMatchFinderUsed) {
        long reducerValue = reducer & 0xFFFFFFFFL;
        reduceTable(state.hashTable, 1 << parameters.hashLog, reducerValue, false);
        boolean isChainAllocated = parameters.strategy != ZstdCompressionParameters.FAST
                && !(isRowMatchFinderUsed && ZstdCompressionParameters.isRowMatchFinderSupported(parameters.strategy));
        if (isChainAllocated) {
            reduceTable(state.chainTable, 1 << parameters.chainLog, reducerValue,
                    parameters.strategy == ZstdCompressionParameters.BTLAZY2);
        }
        if (state.hashLog3 != 0) {
            reduceTable(state.hashTable3, 1 << state.hashLog3, reducerValue, false);
        }
    }
}
