package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public abstract class LoopingNative extends DefaultNative {

    protected static final Set<Datatype> A_BLOCK = Set.of(Datatype.BLOCK);

    protected Value answerOfTheLoop(Supplier<Value> rounds) {
        try {
            return rounds.get();
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
    }

    protected Value oneRoundCatchingContinue(
            Evaluator evaluator, BlockValue body, Context where) {
        try {
            return evaluator.evaluateOrRaise(body, where);
        } catch (ContinueSignal skipped) {
            return NoneValue.none();
        }
    }

    protected boolean theTruthInWhatALoopTests(Value tested) {
        if (tested instanceof UnsetValue) {
            throw Raised.of(EvaluationFailure.NO_RETURN);
        }
        return tested.isTruthy();
    }

    protected List<WordValue> loopNamesIn(Value target) {
        if (!(target instanceof BlockValue block)) {
            if (target instanceof WordValue single) {
                return List.of(single);
            }
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    nativeName() + " walks with a word or a block of words, not a "
                            + target.datatype().literalSpelling());
        }
        if (block.lengthFromHere() == 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, block);
        }
        List<WordValue> names = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            if (!(item instanceof WordValue name)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        nativeName() + " walks with words, and " + Molder.mold(item)
                                + " is not one");
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    protected List<WordValue> namesThatTakeAValue(List<WordValue> names) {
        return names.stream()
                .filter(name -> name.datatype() != Datatype.SET_WORD)
                .toList();
    }

    protected int setLoopNamesFillingWithNonePastTheEnd(
            Context locals, List<WordValue> names, List<Value> items,
            int at, Value walked) {

        int reached = at;
        for (WordValue name : names) {
            if (name.datatype() == Datatype.SET_WORD) {
                locals.register(name.spelling(), positionWithin(walked, reached));
                continue;
            }
            locals.register(name.spelling(),
                    reached < items.size() ? items.get(reached) : NoneValue.none());
            reached++;
        }
        return reached == at ? at + 1 : reached;
    }

    private Value positionWithin(Value walked, int reached) {
        if (!(walked instanceof RebolSeries series)) {
            return walked;
        }
        return series.atIndex(Math.min(
                series.index() + reached, series.storageLength() + 1));
    }
}
