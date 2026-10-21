package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.UnicodeCases;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

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
            canonicalIndexFor(spelling);
        }
    }

    public void startCountingSessionReads() {
        countingWhatTheSessionReads = true;
    }

    public int canonicalIndexFor(String spelling) {
        Integer known = indexBySpelling.get(spelling);
        if (known != null) {
            return canonByIndex.get(known);
        }
        int index = canonByIndex.size();
        int canon = canonByFoldedSpelling.computeIfAbsent(fold(spelling), unseen -> index);
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
            case AnyWordValue word -> canonicalIndexFor(word.spelling());
            case AnyBlockValue block -> block.remaining().forEach(this::internEveryWordIn);
            case MapValue map -> map.listTheItems().forEach(this::internEveryWordIn);
            default -> addNoWord();
        }
    }

    private void addNoWord() {
    }

    private String fold(String spelling) {
        StringBuilder folded = new StringBuilder(spelling.length());
        spelling.codePoints().map(UnicodeCases.TABLES::toLower).forEach(folded::appendCodePoint);
        return folded.toString();
    }
}
