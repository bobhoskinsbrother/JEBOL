package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.MapActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.AnyWordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RemoveEachNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "remove-each";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteSoftly("word"),
                Parameter.of("series", Set.of(BlockValue.TYPE, BinaryValue.TYPE,
                        StringValue.TYPE, MapValue.TYPE, VectorValue.TYPE)),
                Parameter.of("body", BLOCK));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("count");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
            case MapValue map ->
                    removeEachPairFrom(map, arguments, refinements, evaluator, context);
            case AnyBlockValue block ->
                    removeEachFromBlock(block, arguments, refinements, evaluator, context);
            case RebolSeries other -> removeEachByDecidingForwardsThenRewriting(
                    other, arguments, refinements, evaluator, context);
            default -> refuseDatatype(arguments.get(1));
        };
    }

    private Value removeEachFromBlock(AnyBlockValue series, List<Value> arguments,
                                      Set<String> refinements, Evaluator evaluator, Context within) {

        Context locals = Context.loopFrameOf(within);
        List<AnyWordValue> names = loopNamesIn(arguments.get(0));
        names.forEach(name -> locals.register(name.spelling()));
        AnyBlockValue bound = Binder.bindCopyOfWhatTargetHoldsItself((AnyBlockValue) arguments.get(2), locals);
        List<Value> items = series.remaining();
        List<Boolean> marks = readLineBreaksFromHere(series);
        List<Value> kept = new ArrayList<>();
        List<Boolean> keptMarks = new ArrayList<>();
        int taken = 0;
        int at = 0;
        Optional<Value> stoppedWith = Optional.empty();
        while (at < items.size()) {
            int reached = setLoopNamesFillingWithNonePastEnd(
                    locals, names, items, at, series);
            int through = Math.min(reached, items.size());
            boolean drop;
            try {
                drop = evaluator.evaluateOrRaise(bound, locals).isTruthy();
            } catch (LoopSignal stopped) {
                kept.addAll(items.subList(at, items.size()));
                keptMarks.addAll(marks.subList(at, items.size()));
                stoppedWith = Optional.of(stopped.answer());
                break;
            }
            if (drop) {
                taken += through - at;
            } else {
                kept.addAll(items.subList(at, through));
                keptMarks.addAll(marks.subList(at, through));
            }
            at = reached;
        }
        replaceRestWith(series, kept, keptMarks);
        if (stoppedWith.isPresent() && !(stoppedWith.get() instanceof UnsetValue)) {
            return stoppedWith.get();
        }
        return refinements.contains("count") ? IntegerValue.of(taken) : series;
    }

    private List<Boolean> readLineBreaksFromHere(AnyBlockValue series) {
        List<Boolean> marks = new ArrayList<>();
        for (int at = series.index(); at <= series.storageLength(); at++) {
            marks.add(series.storage().breaksLineAt(at));
        }
        return marks;
    }

    private void replaceRestWith(AnyBlockValue series, List<Value> kept, List<Boolean> keptMarks) {
        int had = series.lengthFromHere();
        for (int removed = 0; removed < had; removed++) {
            series.storage().removeAt(series.index());
        }
        for (int back = kept.size(); back > 0; back--) {
            series.storage().insertAt(series.index(), kept.get(back - 1));
            series.storage().setLineBreakAt(series.index(), keptMarks.get(back - 1));
        }
    }

    private Value removeEachByDecidingForwardsThenRewriting(
            RebolSeries series, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context within) {

        series.refuseChangeIfProtected();
        Context locals = Context.loopFrameOf(within);
        AnyWordValue word = (AnyWordValue) arguments.getFirst();
        locals.register(word.spelling());
        AnyBlockValue body = Binder.bindCopyOfWhatTargetHoldsItself((AnyBlockValue) arguments.get(2), locals);
        List<Value> kept = new ArrayList<>();
        int taken = 0;
        for (int at = series.index(); at <= series.storageLength(); at++) {
            Value item = readItemAt(series, at);
            locals.register(word.spelling(), item);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                taken++;
            } else {
                kept.add(item);
            }
        }
        for (int at = series.storageLength(); at >= series.index(); at--) {
            Actions.of(series).orElseThrow().takeOutFrom(at, 1);
        }
        for (int at = 0; at < kept.size(); at++) {
            insertOneInto(series, series.index() + at, kept.get(at));
        }
        return refinements.contains("count") ? IntegerValue.of(taken) : series;
    }

    private Value readItemAt(RebolSeries series, int at) {
        return switch (series) {
            case BinaryValue bytes -> IntegerValue.of(bytes.storage().at(at));
            case VectorValue numbers -> numbers.readItemAt(at);
            default -> CharacterValue.of(((AnyStringValue) series).storage().at(at));
        };
    }

    private void insertOneInto(RebolSeries series, int at, Value item) {
        switch (series) {
            case BinaryValue bytes ->
                    bytes.storage().insertAt(at, (int) ((IntegerValue) item).magnitude());
            case VectorValue numbers ->
                    numbers.storage().insertAt(at, ((IntegerValue) item).magnitude());
            case AnyStringValue text ->
                    text.storage().insertAt(at, ((CharacterValue) item).codepoint());
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE, "remove-each");
        }
    }

    private Value removeEachPairFrom(MapValue map, List<Value> arguments,
                                     Set<String> refinements, Evaluator evaluator, Context within) {

        map.requireChangeable();
        List<AnyWordValue> names = loopNamesIn(arguments.getFirst());
        MapActions.refuseMoreNamesThanPairHas(map, keepNamesThatTakeValue(names));
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.register(name.spelling()));
        AnyBlockValue body = Binder.bindCopyOfWhatTargetHoldsItself((AnyBlockValue) arguments.get(2), locals);
        List<Value> pairs = map.listItems();
        List<Value> takeOut = new ArrayList<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            setLoopNamesFillingWithNonePastEnd(locals, names, pairs, at, map);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                takeOut.add(pairs.get(at));
            }
        }
        takeOut.forEach(map::remove);
        return refinements.contains("count") ? IntegerValue.of(takeOut.size()) : map;
    }
}
