package org.jebol.domain.parse.keyword;

import java.util.LinkedHashMap;
import java.util.Map;

final class TheDialectsWords {

    private TheDialectsWords() {
    }

    static Map<String, ParseKeyword> bySpelling() {
        Map<String, ParseKeyword> words = new LinkedHashMap<>();
        for (ParseKeyword keyword : new ParseKeyword[]{
                new Repeat("any", 0),
                new Repeat("while", 0),
                new Repeat("some", 1),
                new Opt(),
                new Seek("to", false),
                new Seek("thru", true),
                new End(),
                new Skip(),
                new Into(),
                new Quote(),
                new Capture("set", false),
                new Capture("copy", true),
                new Collect(),
                new Keep(),
                new Ahead("and"),
                new Ahead("ahead"),
                new Not(),
                new Reject(),
                new Break("break"),
                new Break("accept"),
                new Return(),
                new Then(),
                new If(),
                new Remove(),
                new Change(),
                new Insert(),
                new MindCase("case", true),
                new MindCase("no-case", false),
                new Fail(),
                new Limit()}) {
                    words.put(keyword.spelling(), keyword);
            }
        return Map.copyOf(words);
    }
}
