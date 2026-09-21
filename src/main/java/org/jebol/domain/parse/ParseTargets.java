package org.jebol.domain.parse;

import java.util.Set;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;
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

    public static WordValue refuseAnythingSetAndCopyCannotWriteInto(Value written) {
        WordValue word = refuseAnythingButAWordOrASetWord(written);
        if (THE_WORDS_THE_DIALECT_RESERVES.contains(word.canonical())) {
            throw Raised.of(EvaluationFailure.PARSE_COMMAND, word);
        }
        return word;
    }

    public static WordValue refuseAnythingButAWordOrASetWord(Value written) {
        if (written instanceof WordValue word
                && (word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.SET_WORD)) {
            return word;
        }
        throw somewhereThatIsNotAVariable(written);
    }

    public static WordValue refuseAnythingButAWordOrAGetWord(Value read) {
        if (read instanceof WordValue word
                && (word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.GET_WORD)) {
            return word;
        }
        throw somewhereThatIsNotAVariable(read);
    }

    public static void refuseAnInputThatIsNotASeries(Value rule, Value held) {
        if (!(held instanceof SeriesValue)) {
            throw Raised.of(EvaluationFailure.PARSE_SERIES, rule);
        }
    }

    private static Raised somewhereThatIsNotAVariable(Value written) {
        return written == null
                ? Raised.of(EvaluationFailure.PARSE_VARIABLE)
                : Raised.of(EvaluationFailure.PARSE_VARIABLE, written);
    }
}
