package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.UnicodeCases;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SymbolTable {

    private static final String ALL_THAT_SEPARATES_THE_SYMBOLS = "\\s+";

    private final List<Integer> canonByIndex = new ArrayList<>(List.of(0));

    private final Map<String, Integer> indexBySpelling = new HashMap<>();

    private final Map<String, Integer> canonByFoldedSpelling = new HashMap<>();

    private boolean countingWhatTheSessionReads;

    public void useBootSymbols(String source) {
        for (String spelling : source.strip().split(ALL_THAT_SEPARATES_THE_SYMBOLS)) {
            canonOf(spelling);
        }
    }

    public void startCountingWhatTheSessionReads() {
        countingWhatTheSessionReads = true;
    }

    public int canonOf(String spelling) {
        Integer known = indexBySpelling.get(spelling);
        if (known != null) {
            return canonByIndex.get(known);
        }
        int index = canonByIndex.size();
        int canon = canonByFoldedSpelling.computeIfAbsent(folded(spelling), unseen -> index);
        canonByIndex.add(canon);
        indexBySpelling.put(spelling, index);
        return canon;
    }

    public void internWhatWasRead(List<Value> read) {
        if (countingWhatTheSessionReads) {
            read.forEach(this::internEveryWordIn);
        }
    }

    private void internEveryWordIn(Value read) {
        switch (read) {
            case WordValue word -> canonOf(word.spelling());
            case BlockValue block -> block.remaining().forEach(this::internEveryWordIn);
            case MapValue map -> map.items().forEach(this::internEveryWordIn);
            default -> nothingInItIsAWord();
        }
    }

    private void nothingInItIsAWord() {
    }

    private String folded(String spelling) {
        StringBuilder folded = new StringBuilder(spelling.length());
        spelling.codePoints().map(UnicodeCases.TABLES::lower).forEach(folded::appendCodePoint);
        return folded.toString();
    }
}
