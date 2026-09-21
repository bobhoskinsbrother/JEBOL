package org.jebol.domain.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

enum ParseKeyword {
    ANY("any", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    WHILE("while", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    SOME("some", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    OPT("opt", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    TO("to", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    THRU("thru", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    END("end", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    SKIP("skip", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    INTO("into", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    QUOTE("quote", 1, false, WhenNothingFollowsIt.RAISES_PARSE_END),
    SET("set", 1, true, WhenNothingFollowsIt.RAISES_PARSE_VARIABLE),
    COPY("copy", 1, true, WhenNothingFollowsIt.RAISES_PARSE_VARIABLE),
    COLLECT("collect", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    KEEP("keep", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    AND("and", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    AHEAD("ahead", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    NOT("not", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    REJECT("reject", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    BREAK("break", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    ACCEPT("accept", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    RETURN("return", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    THEN("then", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    IF("if", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    REMOVE("remove", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    CHANGE("change", 1, true, WhenNothingFollowsIt.DOES_NOT_MATCH),
    INSERT("insert", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END),
    CASE("case", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    NO_CASE("no-case", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    FAIL("fail", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH),
    LIMIT("limit", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);

    enum WhenNothingFollowsIt {
        DOES_NOT_MATCH,
        RAISES_PARSE_END,
        RAISES_PARSE_VARIABLE
    }

    private final String spelling;
    private final int slotsBeforeTheRule;
    private final boolean ownsTheRuleAfterIt;
    private final WhenNothingFollowsIt whenNothingFollowsIt;

    ParseKeyword(String spelling, int slotsBeforeTheRule, boolean ownsTheRuleAfterIt,
            WhenNothingFollowsIt whenNothingFollowsIt) {

        this.spelling = spelling;
        this.slotsBeforeTheRule = slotsBeforeTheRule;
        this.ownsTheRuleAfterIt = ownsTheRuleAfterIt;
        this.whenNothingFollowsIt = whenNothingFollowsIt;
    }

    WhenNothingFollowsIt whenNothingFollowsIt() {
        return whenNothingFollowsIt;
    }

    boolean needsSomethingAfterIt() {
        return ownsTheRuleAfterIt || slotsBeforeTheRule > 0;
    }

    private static final Map<String, ParseKeyword> BY_SPELLING = bySpelling();

    private static Map<String, ParseKeyword> bySpelling() {
        Map<String, ParseKeyword> found = new HashMap<>();
        for (ParseKeyword keyword : values()) {
            found.put(keyword.spelling, keyword);
        }
        return Map.copyOf(found);
    }

    static Optional<ParseKeyword> named(String canonical) {
        return Optional.ofNullable(BY_SPELLING.get(canonical));
    }

    String spelling() {
        return spelling;
    }

    int slotsBeforeTheRule() {
        return slotsBeforeTheRule;
    }

    boolean ownsTheRuleAfterIt() {
        return ownsTheRuleAfterIt;
    }

    int slotsBeforeTheRuleSpan() {
        return 1 + slotsBeforeTheRule;
    }
}
