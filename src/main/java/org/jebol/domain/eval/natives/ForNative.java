package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;

public class ForNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "for";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteSoftly("counter"),
                Parameter.of("start"),
                Parameter.of("end"),
                Parameter.of("step"),
                Parameter.of("body", BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> loopInSteps(
                evaluator,
                context,
                (AnyWordValue) arguments.get(0),
                arguments.get(1),
                arguments.get(2),
                arguments.get(3),
                (AnyBlockValue) arguments.get(4));
    }

    private Value loopInSteps(Evaluator evaluator, Context within, AnyWordValue counter,
                              Value start, Value end, Value step, AnyBlockValue body) {

        if (Comparison.asDouble(step) == 0.0) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "a for loop with a step of zero would never end");
        }
        rejectCharacterBound(start);
        rejectCharacterBound(end);

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        AnyBlockValue bound = Binder.bind(body, locals);

        if (start instanceof RebolSeries series) {
            return stepOverSeries(evaluator, locals, counter, series, end, step, bound);
        }
        if (start instanceof IntegerValue(long from)
                && end instanceof IntegerValue(long to)
                && step instanceof IntegerValue(long stepBy)) {
            return stepOverWholeNumbers(evaluator, locals, counter, from, to, stepBy, bound);
        }
        return stepOverRealNumbers(evaluator, locals, counter,
                Comparison.asDouble(start), Comparison.asDouble(end),
                Comparison.asDouble(step), bound);
    }

    private Value stepOverWholeNumbers(Evaluator evaluator, Context locals,
                                       AnyWordValue counter, long from, long to, long stepBy, AnyBlockValue body) {

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            long at = from;
            while (stepBy > 0 ? at <= to : at >= to) {
                locals.register(counter.spelling(), IntegerValue.of(at));
                last = runOneRoundCatchingContinue(evaluator, body, locals);
                at = stepOrOverflow(at, stepBy);
            }
            return last;
        });
    }

    private long stepOrOverflow(long at, long stepBy) {
        try {
            return Math.addExact(at, stepBy);
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a for loop counter stepped past the integer range");
        }
    }

    private Value stepOverRealNumbers(Evaluator evaluator, Context locals,
                                      AnyWordValue counter, double from, double to, double stepBy, AnyBlockValue body) {

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            for (double at = from; stepBy > 0 ? at <= to : at >= to; at += stepBy) {
                locals.register(counter.spelling(), DecimalValue.of(at));
                last = runOneRoundCatchingContinue(evaluator, body, locals);
            }
            return last;
        });
    }

    private Value stepOverSeries(Evaluator evaluator, Context locals, AnyWordValue counter,
                                 RebolSeries series, Value end, Value step, AnyBlockValue body) {

        int tail = series.storageLength() + 1;
        int endIndex = Math.max(0, Math.min(endIndexOf(end), tail));
        long stepBy = (long) Arithmetic.asMagnitude(step);
        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            int at = series.index();
            while (stepBy > 0 ? at <= endIndex : at >= endIndex) {
                locals.register(counter.spelling(), series.atIndex(at));
                last = runOneRoundCatchingContinue(evaluator, body, locals);
                int landedAt = locals.findSlotFor(counter.canonical()).value()
                        instanceof RebolSeries moved ? moved.index() : at;
                at = (int) (landedAt + stepBy);
            }
            return last;
        });
    }

    private int endIndexOf(Value end) {
        return end instanceof RebolSeries other
                ? other.index()
                : (int) Arithmetic.asMagnitude(end);
    }

    private void rejectCharacterBound(Value bound) {
        if (bound instanceof CharacterValue character) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "for does not step a character range, and " + character
                            + " is a character");
        }
    }
}
