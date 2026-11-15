package org.jebol.domain.eval.compression.lz4;

import static org.jebol.domain.eval.compression.lz4.Lz4Block.MINIMUM_MATCH;
import static org.jebol.domain.eval.compression.lz4.Lz4Block.SHORTEST_COMPRESSED_INPUT;

final class Lz4HashChainCompressor {

    private static final int OPTIMAL_MATCH_LENGTH = 18;
    private static final int ATTEMPTS_ABOVE_WHICH_PATTERNS_ARE_ANALYSED = 128;

    private final Lz4Block block;
    private final Lz4HashChain chain;
    private final int attempts;
    private final boolean analysePatterns;
    private int start0;
    private int start2;
    private int start3;
    private Lz4Match match0;
    private Lz4Match match1;
    private Lz4Match match2;
    private Lz4Match match3;

    private Lz4HashChainCompressor(byte[] input, int attempts) {
        block = new Lz4Block(input);
        chain = new Lz4HashChain(input);
        this.attempts = attempts;
        analysePatterns = attempts > ATTEMPTS_ABOVE_WHICH_PATTERNS_ARE_ANALYSED;
    }

    static byte[] compress(byte[] input, int attempts) {
        return new Lz4HashChainCompressor(input, attempts).compress();
    }

    private enum Next { SEARCH_SECOND, SEARCH_THIRD, DONE }

    private byte[] compress() {
        if (block.inputEnd < SHORTEST_COMPRESSED_INPUT) {
            return block.finishWithLastLiterals();
        }
        while (block.at <= block.matchFindLimit) {
            match1 = chain.findBestMatch(block.at, block.matchLimit, attempts, analysePatterns);
            if (match1.length() < MINIMUM_MATCH) {
                block.at++;
                continue;
            }
            start0 = block.at;
            match0 = match1;
            Next next = Next.SEARCH_SECOND;
            while (next != Next.DONE) {
                next = next == Next.SEARCH_SECOND ? searchSecond() : searchThird();
            }
        }
        return block.finishWithLastLiterals();
    }

    private Next searchSecond() {
        int at = block.at;
        if (at + match1.length() <= block.matchFindLimit) {
            start2 = at + match1.length() - 2;
            match2 = chain.findWiderMatch(start2, at, block.matchLimit, match1.length(),
                    attempts, analysePatterns, false);
            start2 += match2.back();
        } else {
            match2 = Lz4Match.NONE;
        }
        if (match2.length() <= match1.length()) {
            block.writeSequence(match1.length(), match1.offset());
            return Next.DONE;
        }
        if (start0 < block.at && start2 < block.at + match0.length()) {
            block.at = start0;
            match1 = match0;
        }
        if (start2 - block.at < 3) {
            block.at = start2;
            match1 = match2;
            return Next.SEARCH_SECOND;
        }
        return Next.SEARCH_THIRD;
    }

    private Next searchThird() {
        if (start2 - block.at < OPTIMAL_MATCH_LENGTH) {
            int newLength = Math.min(match1.length(), OPTIMAL_MATCH_LENGTH);
            if (block.at + newLength > start2 + match2.length() - MINIMUM_MATCH) {
                newLength = start2 - block.at + match2.length() - MINIMUM_MATCH;
            }
            shortenSecondBy(newLength - (start2 - block.at));
        }
        if (start2 + match2.length() <= block.matchFindLimit) {
            start3 = start2 + match2.length() - 3;
            match3 = chain.findWiderMatch(start3, start2, block.matchLimit, match2.length(),
                    attempts, analysePatterns, false);
            start3 += match3.back();
        } else {
            match3 = Lz4Match.NONE;
        }
        if (match3.length() <= match2.length()) {
            if (start2 < block.at + match1.length()) {
                match1 = match1.withLength(start2 - block.at);
            }
            block.writeSequence(match1.length(), match1.offset());
            block.at = start2;
            block.writeSequence(match2.length(), match2.offset());
            return Next.DONE;
        }
        if (start3 < block.at + match1.length() + 3) {
            if (start3 >= block.at + match1.length()) {
                return dropSecondMatch();
            }
            start2 = start3;
            match2 = match3;
            return Next.SEARCH_THIRD;
        }
        fitFirstBeforeSecond();
        block.writeSequence(match1.length(), match1.offset());
        block.at = start2;
        match1 = match2;
        start2 = start3;
        match2 = match3;
        return Next.SEARCH_THIRD;
    }

    private Next dropSecondMatch() {
        if (start2 < block.at + match1.length()) {
            shortenSecondBy(block.at + match1.length() - start2);
            if (match2.length() < MINIMUM_MATCH) {
                start2 = start3;
                match2 = match3;
            }
        }
        block.writeSequence(match1.length(), match1.offset());
        block.at = start3;
        match1 = match3;
        start0 = start2;
        match0 = match2;
        return Next.SEARCH_SECOND;
    }

    private void fitFirstBeforeSecond() {
        if (start2 >= block.at + match1.length()) {
            return;
        }
        if (start2 - block.at < OPTIMAL_MATCH_LENGTH) {
            int newLength = Math.min(match1.length(), OPTIMAL_MATCH_LENGTH);
            if (block.at + newLength > start2 + match2.length() - MINIMUM_MATCH) {
                newLength = start2 - block.at + match2.length() - MINIMUM_MATCH;
            }
            match1 = match1.withLength(newLength);
            shortenSecondBy(newLength - (start2 - block.at));
        } else {
            match1 = match1.withLength(start2 - block.at);
        }
    }

    private void shortenSecondBy(int correction) {
        if (correction > 0) {
            start2 += correction;
            match2 = match2.withLength(match2.length() - correction);
        }
    }
}
