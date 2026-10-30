package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

public class RepeatNative extends LoopingNative {

    private static final Set<Datatype> WHAT_REPEAT_COUNTS_BY = TypesetValue.NUMBER.membersAnd(
            TypesetValue.SERIES.membersAnd(PairValue.TYPE, NoneValue.TYPE)
                    .toArray(Datatype[]::new));

    @Override
    public String nativeName() {
        return "repeat";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteSoftly("counter"),
                Parameter.of("count", WHAT_REPEAT_COUNTS_BY),
                Parameter.of("body", BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyWordValue counter = (AnyWordValue) arguments.get(0);
            AnyBlockValue body = (AnyBlockValue) arguments.get(2);
            return switch (arguments.get(1)) {
                case PairValue grid -> repeatOverGrid(evaluator, context, counter, grid, body);
                case NoneValue nothing -> nothing;
                case RebolSeries walked -> loopCountedNumberOfTimes(evaluator, context, counter, body,
                        index -> walked.atIndex(walked.index() + (int) index),
                        walked.lengthFromHere());
                case Value count -> loopCountedNumberOfTimes(evaluator, context, counter, body,
                        index -> IntegerValue.of(index + 1),
                        (long) Arithmetic.asMagnitude(count));
            };
        };
    }

    private Value loopCountedNumberOfTimes(Evaluator evaluator, Context within, AnyWordValue counter,
                                           AnyBlockValue body, LongFunction<Value> valueAt, long passes) {

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        AnyBlockValue bound = Binder.bindCopyOfWhatTargetHoldsItself(body, locals);
        return runLoop(() -> {
            Value last = NoneValue.none();
            for (long pass = 0; pass < passes; pass++) {
                locals.register(counter.spelling(), valueAt.apply(pass));
                last = runOneRoundCatchingContinue(evaluator, bound, locals);
            }
            return last;
        });
    }

    private Value repeatOverGrid(Evaluator evaluator, Context within, AnyWordValue counter,
                                 PairValue grid, AnyBlockValue body) {

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        AnyBlockValue bound = Binder.bindCopyOfWhatTargetHoldsItself(body, locals);
        long across = (long) grid.x();
        long down = (long) grid.y();
        return runLoop(() -> {
            Value last = NoneValue.none();
            for (long onDown = 1; onDown <= down; onDown++) {
                for (long onAcross = 1; onAcross <= across; onAcross++) {
                    locals.register(counter.spelling(), PairValue.of(onAcross, onDown));
                    last = runOneRoundCatchingContinue(evaluator, bound, locals);
                }
            }
            return last;
        });
    }
}
