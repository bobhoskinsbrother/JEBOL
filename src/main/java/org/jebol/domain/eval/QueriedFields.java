package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class QueriedFields {

    private static final String NAMES_THEMSELVES = "words";

    protected abstract List<String> names();

    protected abstract Optional<Value> findFieldNamed(String asked);

    protected abstract Value answerUnknownAlone(AnyWordValue asked);

    protected Value answerUnknownInBlock(AnyWordValue asked) {
        throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
    }

    protected boolean answersItsNames() {
        return false;
    }

    protected String findFieldItAsksFor(AnyWordValue asked) {
        return asked.canonical();
    }

    public Value answer(Value field, Evaluator evaluator) {
        return switch (field) {
            case AnyWordValue asked when asked.canonical().equals(NAMES_THEMSELVES)
                    && answersItsNames() -> listNames();
            case AnyWordValue asked -> findFieldNamed(findFieldItAsksFor(asked)).orElseGet(() -> answerUnknownAlone(asked));
            case AnyBlockValue several -> answerEachOf(several);
            case NoneValue nothing -> listNames();
            default -> gatherEveryFieldIntoObject(evaluator);
        };
    }

    private Value listNames() {
        return BlockValue.of(names().stream().<Value>map(WordValue::of).toList());
    }

    private Value answerEachOf(AnyBlockValue several) {
        List<Value> answer = new ArrayList<>();
        List<Integer> whereEachLabelStartsALine = new ArrayList<>();
        for (Value item : several.remaining()) {
            if (!(item instanceof AnyWordValue asked)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, item);
            }
            if (!(asked instanceof GetWordValue)) {
                answer.add(asked.asSetWord());
                whereEachLabelStartsALine.add(answer.size());
            }
            answer.add(findFieldNamed(findFieldItAsksFor(asked)).orElseGet(() -> answerUnknownInBlock(asked)));
        }
        BlockValue answered = BlockValue.of(answer);
        whereEachLabelStartsALine.forEach(position -> answered.storage().setLineBreakAt(position, true));
        return answered;
    }

    protected Value gatherEveryFieldIntoObject(Evaluator evaluator) {
        ObjectValue described = new ObjectValue(Context.makeChildOf(evaluator.systemContext()));
        described.context().pointItsOwnSelfAt(described);
        return withEveryFieldIn(described);
    }

    protected ObjectValue withEveryFieldIn(ObjectValue described) {
        for (String part : names()) {
            described.context().register(part, readFieldInObject(part));
        }
        return described;
    }

    protected Value readFieldInObject(String part) {
        return findFieldNamed(part).orElseGet(NoneValue::none);
    }

    protected Raised cannotUse(AnyWordValue asked, Datatype datatype) {
        return Raised.of(EvaluationFailure.CANNOT_USE, asked, datatype);
    }
}
