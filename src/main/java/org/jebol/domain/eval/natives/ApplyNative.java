package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ApplyNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "apply";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("func"),
                Parameter.required("block", Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue given = (AnyBlockValue) arguments.get(1);
            List<Value> supplied = refinements.contains("only")
                    ? new ArrayList<>(given.remaining())
                    : new ArrayList<>(evaluator.evaluateEachOrRaise(given, context));
            Value callee = arguments.getFirst();
            while (isDo(callee) && !supplied.isEmpty()
                    && supplied.getFirst() instanceof AnyFunctionValue) {
                callee = supplied.removeFirst();
            }
            if (callee instanceof NativeValue builtIn
                    && !builtIn.refinementsDeclaredApart().isEmpty()) {
                return appliedWithRefinements(builtIn, supplied, evaluator);
            }
            return evaluator.applyFunction(callee, exactlyAsManyAsItTakes(callee, supplied));
        };
    }

    private boolean isDo(Value callee) {
        return callee instanceof NativeValue builtIn && builtIn.nativeName().equals("do");
    }

    private List<Value> exactlyAsManyAsItTakes(Value callee, List<Value> supplied) {
        int wanted = (int) arityOf(callee);
        List<Value> exactly = new ArrayList<>(
                supplied.subList(0, Math.min(wanted, supplied.size())));
        while (exactly.size() < wanted) {
            exactly.add(NoneValue.none());
        }
        return exactly;
    }

    private long arityOf(Value callee) {
        return switch (callee) {
            case NativeValue built -> built.parameters().stream()
                    .filter(Parameter::consumesAnArgument)
                    .filter(parameter -> parameter.owningRefinement().isEmpty())
                    .count();
            case DefinedFunctionValue function -> function.parameters().size();
            case OperatorValue operator -> arityOf(operator.underlying());
            default -> 0;
        };
    }

    private Value appliedWithRefinements(
            NativeValue builtIn, List<Value> supplied, Evaluator evaluator) {

        Set<String> asked = new LinkedHashSet<>();
        List<Value> beforeAnyRefinement = new ArrayList<>();
        Map<String, List<Value>> belongingTo = new LinkedHashMap<>();
        Optional<String> reading = Optional.empty();
        int at = 0;
        for (Value word : theWordsItTakes(builtIn, evaluator)) {
            Value next = at < supplied.size() ? supplied.get(at) : NoneValue.none();
            at++;
            if (word instanceof RefinementValue marker) {
                reading = Optional.of(marker.canonical());
                belongingTo.putIfAbsent(marker.canonical(), new ArrayList<>());
                if (next.isTruthy()) {
                    asked.add(marker.canonical());
                }
            } else if (reading.isEmpty()) {
                beforeAnyRefinement.add(next);
            } else {
                belongingTo.get(reading.get()).add(next);
            }
        }
        NativeValue refined = builtIn.askedFor(asked);
        return evaluator.applyFunction(refined,
                argumentsInDeclaredOrder(refined, asked, beforeAnyRefinement, belongingTo));
    }

    private List<Value> theWordsItTakes(NativeValue builtIn, Evaluator evaluator) {
        Value reflect = evaluator.systemContext().valueAt("reflect");
        return evaluator.applyFunction(reflect, List.of(builtIn, WordValue.of("words")))
                instanceof AnyBlockValue words
                ? words.remaining()
                : List.of();
    }

    private List<Value> argumentsInDeclaredOrder(NativeValue refined, Set<String> asked,
            List<Value> beforeAnyRefinement, Map<String, List<Value>> belongingTo) {

        Deque<Value> plain = new ArrayDeque<>(beforeAnyRefinement);
        Map<String, Deque<Value>> refinementArguments = new LinkedHashMap<>();
        belongingTo.forEach((name, values) ->
                refinementArguments.put(name, new ArrayDeque<>(values)));
        List<Value> arguments = new ArrayList<>();
        for (Parameter parameter : refined.parameters()) {
            if (!parameter.consumesAnArgument()) {
                continue;
            }
            Optional<String> owner = parameter.owningRefinement();
            if (owner.isEmpty()) {
                arguments.add(plain.isEmpty() ? NoneValue.none() : plain.removeFirst());
            } else if (asked.contains(owner.get())) {
                Deque<Value> waiting = refinementArguments
                        .getOrDefault(owner.get(), new ArrayDeque<>());
                arguments.add(waiting.isEmpty() ? NoneValue.none() : waiting.removeFirst());
            }
        }
        return arguments;
    }
}
