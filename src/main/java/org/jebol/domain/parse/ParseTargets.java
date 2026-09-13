package org.jebol.domain.parse;

import java.util.Set;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

final class ParseTargets {

    static final Set<String> THE_WORDS_THE_DIALECT_RESERVES = Set.of(
            "|", "set", "copy", "some", "any", "opt", "not", "and", "ahead",
            "then", "remove", "insert", "change", "if", "fail", "reject",
            "while", "collect", "keep", "return", "limit", "??", "case",
            "no-case", "accept", "break", "skip", "to", "thru", "quote",
            "do", "into", "only", "end");

    static final Set<String> THE_WORDS_THAT_NAME_WHERE_COLLECT_PUTS_IT =
            Set.of("set", "into", "after");

    private ParseTargets() {
    }

    static WordValue refuseAnythingSetAndCopyCannotWriteInto(Value written) {
        WordValue named = refuseAnythingButAWordOrASetWord(written);
        if (THE_WORDS_THE_DIALECT_RESERVES.contains(named.canonical())) {
            throw Raised.of(EvaluationFailure.PARSE_COMMAND, named);
        }
        return named;
    }

    static WordValue refuseAnythingButAWordOrASetWord(Value written) {
        if (written instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.SET_WORD)) {
            return named;
        }
        throw somewhereThatIsNotAVariable(written);
    }

    static WordValue refuseAnythingButAWordOrAGetWord(Value read) {
        if (read instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.GET_WORD)) {
            return named;
        }
        throw somewhereThatIsNotAVariable(read);
    }

    static void refuseAnInputThatIsNotASeries(Value rule, Value held) {
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
