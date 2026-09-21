package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ParseKeyword {

    String spelling();

    int slotsBeforeTheRule();

    boolean ownsTheRuleAfterIt();

    WhenNothingFollowsIt whenNothingFollowsIt();

    int applyToBlock(ParseWalk walk, List<Value> rules, int at);

    int applyToString(ParseWalk walk, List<Value> rules, int at);

    default int slotsBeforeTheRuleSpan() {
        return 1 + slotsBeforeTheRule();
    }

    default boolean needsSomethingAfterIt() {
        return ownsTheRuleAfterIt() || slotsBeforeTheRule() > 0;
    }

    Map<String, ParseKeyword> BY_SPELLING = TheDialectsWords.bySpelling();

    static Optional<ParseKeyword> named(String canonical) {
        return Optional.ofNullable(BY_SPELLING.get(canonical));
    }
}
