package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseTargets;
import org.jebol.domain.value.EndValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

record ValueToPutIn(int slot, boolean whole) {

    private static final String WHOLE = "only";

    static ValueToPutIn readFrom(List<Value> rules, int from) {
        requireSlot(rules, from);
        if (rules.get(from) instanceof WordValue word
                && ParseTargets.WORDS_DIALECT_RESERVES.contains(word.canonical())) {
            if (!word.canonical().equals(WHOLE)) {
                throw Raised.of(EvaluationFailure.PARSE_RULE, word);
            }
            requireSlot(rules, from + 1);
            return new ValueToPutIn(from + 1, true);
        }
        return new ValueToPutIn(from, false);
    }

    private static void requireSlot(List<Value> rules, int slot) {
        if (slot >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, EndValue.end());
        }
    }
}
