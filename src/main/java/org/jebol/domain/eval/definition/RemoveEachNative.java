package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.MapActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RemoveEachNative extends LoopingNative {

    @Override
    public String name() {
        return "remove-each";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.softQuoted("word"),
                Parameter.required("series", Set.of(Datatype.BLOCK, Datatype.BINARY,
                        Datatype.STRING, Datatype.MAP, Datatype.VECTOR)),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("count");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
            case MapValue map ->
                    removedEachPairFrom(map, arguments, refinements, evaluator, context);
            case BlockValue block ->
                    removedEachFromABlock(block, arguments, refinements, evaluator, context);
            case RebolSeries other -> removedEachFromDecidingForwardsThenRewriting(
                    other, arguments, refinements, evaluator, context);
            default -> refuseTheDatatype(arguments.get(1));
        };
    }

    private Value removedEachFromABlock(BlockValue series, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator, Context within) {

        Context locals = Context.loopFrameOf(within);
        List<WordValue> names = loopNamesIn(arguments.get(0));
        names.forEach(name -> locals.register(name.spelling()));
        BlockValue bound = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> items = series.remaining();
        List<Value> kept = new ArrayList<>();
        int taken = 0;
        int at = 0;
        Optional<Value> stoppedWith = Optional.empty();
        while (at < items.size()) {
            int reached = setLoopNamesFillingWithNonePastTheEnd(
                    locals, names, items, at, series);
            int through = Math.min(reached, items.size());
            boolean drop;
            try {
                drop = evaluator.evaluateOrRaise(bound, locals).isTruthy();
            } catch (LoopSignal stopped) {
                kept.addAll(items.subList(at, items.size()));
                stoppedWith = Optional.of(stopped.answer());
                break;
            }
            if (drop) {
                taken += through - at;
            } else {
                kept.addAll(items.subList(at, through));
            }
            at = reached;
        }
        replaceTheRestWith(series, kept);
        if (stoppedWith.isPresent() && !(stoppedWith.get() instanceof UnsetValue)) {
            return stoppedWith.get();
        }
        return refinements.contains("count") ? IntegerValue.of(taken) : series;
    }

    private void replaceTheRestWith(BlockValue series, List<Value> kept) {
        int had = series.lengthFromHere();
        for (int removed = 0; removed < had; removed++) {
            series.storage().removeAt(series.index());
        }
        for (int back = kept.size(); back > 0; back--) {
            series.storage().insertAt(series.index(), kept.get(back - 1));
        }
    }

    private Value removedEachFromDecidingForwardsThenRewriting(
            RebolSeries series, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context within) {

        series.refuseChangeIfProtected();
        Context locals = Context.loopFrameOf(within);
        WordValue word = (WordValue) arguments.getFirst();
        locals.register(word.spelling());
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> kept = new ArrayList<>();
        int taken = 0;
        for (int at = series.index(); at <= series.storageLength(); at++) {
            Value item = itemAt(series, at);
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

    private Value itemAt(RebolSeries series, int at) {
        return switch (series) {
            case BinaryValue bytes -> IntegerValue.of(bytes.storage().at(at));
            case VectorValue numbers -> numbers.itemAt(at);
            default -> CharacterValue.of(((StringValue) series).storage().at(at));
        };
    }

    private void insertOneInto(RebolSeries series, int at, Value item) {
        switch (series) {
            case BinaryValue bytes ->
                    bytes.storage().insertAt(at, (int) ((IntegerValue) item).magnitude());
            case VectorValue numbers ->
                    numbers.storage().insertAt(at, ((IntegerValue) item).magnitude());
            case StringValue text ->
                    text.storage().insertAt(at, ((CharacterValue) item).codepoint());
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE, "remove-each");
        }
    }

    private Value removedEachPairFrom(MapValue map, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator, Context within) {

        map.requireChangeable();
        List<WordValue> names = loopNamesIn(arguments.getFirst());
        MapActions.refuseMoreNamesThanAPairHas(map, namesThatTakeAValue(names));
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.register(name.spelling()));
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> pairs = map.items();
        List<Value> takeOut = new ArrayList<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            setLoopNamesFillingWithNonePastTheEnd(locals, names, pairs, at, map);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                takeOut.add(pairs.get(at));
            }
        }
        takeOut.forEach(map::remove);
        return refinements.contains("count") ? IntegerValue.of(takeOut.size()) : map;
    }
}
