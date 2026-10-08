package org.jebol.domain.parse;

import java.util.Set;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.WordValue;

public final class ParseTargets {

    public static final Set<String> THE_WORDS_THE_DIALECT_RESERVES = Set.of(
            "|", "set", "copy", "some", "any", "opt", "not", "and", "ahead",
            "then", "remove", "insert", "change", "if", "fail", "reject",
            "while", "collect", "keep", "return", "limit", "??", "case",
            "no-case", "accept", "break", "skip", "to", "thru", "quote",
            "do", "into", "only", "end");

    public static final Set<String> THE_WORDS_THAT_NAME_WHERE_COLLECT_PUTS_IT =
            Set.of("set", "into", "after");

    private ParseTargets() {
    }

    public static AnyWordValue refuseAnythingSetAndCopyCannotWriteInto(Value written) {
        AnyWordValue word = refuseAnythingButAWordOrASetWord(written);
        if (THE_WORDS_THE_DIALECT_RESERVES.contains(word.canonical())) {
            throw Raised.of(EvaluationFailure.PARSE_COMMAND, word);
        }
        return word;
    }

    public static AnyWordValue refuseAnythingButAWordOrASetWord(Value written) {
        if (written instanceof AnyWordValue word
                && (word instanceof WordValue || word instanceof SetWordValue)) {
            return word;
        }
        throw somewhereThatIsNotAVariable(written);
    }

    public static AnyWordValue refuseAnythingButAWordOrAGetWord(Value read) {
        if (read instanceof AnyWordValue word && word.fetchesItsValue()) {
            return word;
        }
        throw somewhereThatIsNotAVariable(read);
    }

    public static void refuseAnInputThatIsNotASeries(Value rule, Value held) {
        if (!(held instanceof RebolSeries)) {
            throw Raised.of(EvaluationFailure.PARSE_SERIES, rule);
        }
    }

    private static Raised somewhereThatIsNotAVariable(Value written) {
        return written == null
                ? Raised.of(EvaluationFailure.PARSE_VARIABLE)
                : Raised.of(EvaluationFailure.PARSE_VARIABLE, written);
    }
}
