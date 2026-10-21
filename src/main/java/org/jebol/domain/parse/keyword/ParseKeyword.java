package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ParseKeyword {

    String spelling();

    int slotsBeforeRule();

    boolean ownsRuleAfterIt();

    WhenNothingFollowsIt describeWhenNothingFollows();

    int applyToBlock(ParseWalk walk, List<Value> rules, int at);

    int applyToString(ParseWalk walk, List<Value> rules, int at);

    default int slotsBeforeRuleSpan() {
        return 1 + slotsBeforeRule();
    }

    default boolean needsSomethingAfterIt() {
        return ownsRuleAfterIt() || slotsBeforeRule() > 0;
    }

    Map<String, ParseKeyword> BY_SPELLING = DialectsWords.indexBySpelling();

    static Optional<ParseKeyword> findTheKeywordNamed(String canonical) {
        return Optional.ofNullable(BY_SPELLING.get(canonical));
    }
}
