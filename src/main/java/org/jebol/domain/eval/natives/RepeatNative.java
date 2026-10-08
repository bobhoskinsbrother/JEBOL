package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

public class RepeatNative extends LoopingNative {

    private static final Set<Datatype> WHAT_REPEAT_COUNTS_BY = Typeset.NUMBER.membersAnd(
            Typeset.SERIES.membersAnd(Datatype.PAIR, Datatype.NONE)
                    .toArray(Datatype[]::new));

    @Override
    public String nativeName() {
        return "repeat";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.softQuoted("counter"),
                Parameter.required("count", WHAT_REPEAT_COUNTS_BY),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyWordValue counter = (AnyWordValue) arguments.get(0);
            BlockValue body = (BlockValue) arguments.get(2);
            return switch (arguments.get(1)) {
                case PairValue grid -> repeatedOverGrid(evaluator, context, counter, grid, body);
                case NoneValue nothing -> nothing;
                case RebolSeries walked -> countedLoop(evaluator, context, counter, body,
                        index -> walked.atIndex(walked.index() + (int) index),
                        walked.lengthFromHere());
                case Value count -> countedLoop(evaluator, context, counter, body,
                        index -> IntegerValue.of(index + 1),
                        (long) Arithmetic.asMagnitude(count));
            };
        };
    }

    private Value countedLoop(Evaluator evaluator, Context within, AnyWordValue counter,
            BlockValue body, LongFunction<Value> valueAt, long passes) {

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            for (long pass = 0; pass < passes; pass++) {
                locals.register(counter.spelling(), valueAt.apply(pass));
                last = oneRoundCatchingContinue(evaluator, bound, locals);
            }
            return last;
        });
    }

    private Value repeatedOverGrid(Evaluator evaluator, Context within, AnyWordValue counter,
            PairValue grid, BlockValue body) {

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        long across = (long) grid.x();
        long down = (long) grid.y();
        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            for (long onDown = 1; onDown <= down; onDown++) {
                for (long onAcross = 1; onAcross <= across; onAcross++) {
                    locals.register(counter.spelling(), PairValue.of(onAcross, onDown));
                    last = oneRoundCatchingContinue(evaluator, bound, locals);
                }
            }
            return last;
        });
    }
}
