package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class EvokeNative extends DefaultNative {

    private static final Set<String> DEBUG_ONLY_CHANTS = Set.of(
            "crash-dump", "watch-recycle", "watch-alloc",
            "watch-obj-copy", "watch-expand", "crash");

    private static final String A_CHANT_THAT_TAKES_A_SIZE = "stack-size";

    private static final String A_CHANT_THAT_DOES_NOTHING_HERE = "delect";

    private static final long THE_FIRST_NUMBERED_CHECK = 0;

    private static final long THE_LAST_NUMBERED_CHECK = 2;

    private static final String EVOKE_HELP = """
            Evoke values:
            [stack-size n]

            1: check memory pools
            2: check bind table
            """;

    @Override
    public String name() {
        return "evoke";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("chant",
                Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.INTEGER)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<Value> chants = arguments.getFirst() instanceof BlockValue several
                    ? several.remaining()
                    : List.of(arguments.getFirst());
            for (int at = 0; at < chants.size(); at++) {
                at += obeyAnsweringHowManyValuesItTook(chants.get(at), evaluator);
            }
            return UnsetValue.unset();
        };
    }

    private int obeyAnsweringHowManyValuesItTook(Value chant, Evaluator evaluator) {
        if (chant instanceof WordValue word) {
            return obeyAWord(word, evaluator);
        }
        if (chant instanceof IntegerValue(long magnitude)
                && (magnitude < THE_FIRST_NUMBERED_CHECK || magnitude > THE_LAST_NUMBERED_CHECK)) {
            evaluator.output().write(EVOKE_HELP);
        }
        return 0;
    }

    private int obeyAWord(WordValue word, Evaluator evaluator) {
        String asked = word.canonical();
        if (DEBUG_ONLY_CHANTS.contains(asked)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        if (A_CHANT_THAT_TAKES_A_SIZE.equals(asked)) {
            return 1;
        }
        if (!A_CHANT_THAT_DOES_NOTHING_HERE.equals(asked)) {
            evaluator.output().write(EVOKE_HELP);
        }
        return 0;
    }
}
