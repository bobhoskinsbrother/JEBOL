package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
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
import org.jebol.domain.value.WordValue;

import java.util.List;

public class ForNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "for";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.softQuoted("counter"),
                Parameter.required("start"),
                Parameter.required("end"),
                Parameter.required("step"),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> steppedLoop(
                evaluator,
                context,
                (WordValue) arguments.get(0),
                arguments.get(1),
                arguments.get(2),
                arguments.get(3),
                (BlockValue) arguments.get(4));
    }

    private Value steppedLoop(Evaluator evaluator, Context within, WordValue counter,
            Value start, Value end, Value step, BlockValue body) {

        if (Comparison.asDouble(step) == 0.0) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "a for loop with a step of zero would never end");
        }
        rejectCharacterBound(start);
        rejectCharacterBound(end);

        Context locals = Context.loopFrameOf(within);
        locals.register(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);

        if (start instanceof RebolSeries series) {
            return steppedOverSeries(evaluator, locals, counter, series, end, step, bound);
        }
        if (start instanceof IntegerValue(long from)
                && end instanceof IntegerValue(long to)
                && step instanceof IntegerValue(long stepBy)) {
            return steppedOverWholeNumbers(evaluator, locals, counter, from, to, stepBy, bound);
        }
        return steppedOverRealNumbers(evaluator, locals, counter,
                Comparison.asDouble(start), Comparison.asDouble(end),
                Comparison.asDouble(step), bound);
    }

    private Value steppedOverWholeNumbers(Evaluator evaluator, Context locals,
            WordValue counter, long from, long to, long stepBy, BlockValue body) {

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            long at = from;
            while (stepBy > 0 ? at <= to : at >= to) {
                locals.register(counter.spelling(), IntegerValue.of(at));
                last = oneRoundCatchingContinue(evaluator, body, locals);
                at = steppedOrOverflowed(at, stepBy);
            }
            return last;
        });
    }

    private long steppedOrOverflowed(long at, long stepBy) {
        try {
            return Math.addExact(at, stepBy);
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a for loop counter stepped past the integer range");
        }
    }

    private Value steppedOverRealNumbers(Evaluator evaluator, Context locals,
            WordValue counter, double from, double to, double stepBy, BlockValue body) {

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            for (double at = from; stepBy > 0 ? at <= to : at >= to; at += stepBy) {
                locals.register(counter.spelling(), DecimalValue.of(at));
                last = oneRoundCatchingContinue(evaluator, body, locals);
            }
            return last;
        });
    }

    private Value steppedOverSeries(Evaluator evaluator, Context locals, WordValue counter,
            RebolSeries series, Value end, Value step, BlockValue body) {

        int tail = series.storageLength() + 1;
        int endIndex = Math.max(0, Math.min(endIndexOf(end), tail));
        long stepBy = (long) Arithmetic.asMagnitude(step);
        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            int at = series.index();
            while (stepBy > 0 ? at <= endIndex : at >= endIndex) {
                locals.register(counter.spelling(), series.atIndex(at));
                last = oneRoundCatchingContinue(evaluator, body, locals);
                int landedAt = locals.slotFor(counter.canonical()).value()
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
