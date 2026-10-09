package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ResolveNative extends DefaultNative {

    private static final int WHERE_ONLY_ARRIVES = 2;

    @Override
    public String nativeName() {
        return "resolve";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("target", TypesetValue.ANY_OBJECT.members()),
                Parameter.required("source", TypesetValue.ANY_OBJECT.members()),
                Parameter.belongingTo("only", "from", Set.of(BlockValue.TYPE, IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only", "all", "extend");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Context into = arguments.getFirst().fieldsAsAContext().orElseThrow();
            if (into.isClosedToNewNames()) {
                throw Raised.of(EvaluationFailure.PROTECTED);
            }
            Value onlyThese = refinements.contains("only") && arguments.size() > WHERE_ONLY_ARRIVES
                    ? arguments.get(WHERE_ONLY_ARRIVES)
                    : NoneValue.none();
            resolveInto(into, arguments.get(1).fieldsAsAContext().orElseThrow(),
                    refinements, onlyThese);
            return arguments.getFirst();
        };
    }

    private record WordsToResolve(int startAt, Set<String> spellings, boolean limited) {

        static final WordsToResolve EVERYTHING = new WordsToResolve(1, Set.of(), false);

        boolean allows(String canonical) {
            return !limited || spellings.contains(canonical);
        }
    }

    private void resolveInto(
            Context into, Context from, Set<String> refinements, Value onlyThese) {

        List<ContextSlot> targetSlots = into.slots();
        WordsToResolve writable = wordsToResolve(targetSlots, refinements, onlyThese);
        Map<String, Value> available = theFieldsOtherThanSelfIn(from);
        for (int at = writable.startAt(); at <= targetSlots.size(); at++) {
            ContextSlot slot = targetSlots.get(at - 1);
            if (slot.canonical().equals("self") || slot.isProtected()) {
                continue;
            }
            boolean known = available.containsKey(slot.canonical());
            if (!writable.allows(slot.canonical()) || (!known && !writable.limited())) {
                continue;
            }
            if (!refinements.contains("all") && !(slot.value() instanceof UnsetValue)) {
                continue;
            }
            slot.setValue(known ? available.get(slot.canonical()) : UnsetValue.unset());
        }
        if (refinements.contains("extend")) {
            available.forEach((name, value) -> {
                if (!into.holds(name) && writable.allows(name)) {
                    into.register(name, value);
                }
            });
        }
    }

    private Map<String, Value> theFieldsOtherThanSelfIn(Context from) {
        Map<String, Value> available = new LinkedHashMap<>();
        for (ContextSlot slot : from.slots()) {
            if (!slot.canonical().equals("self")) {
                available.put(slot.canonical(), slot.value());
            }
        }
        return available;
    }

    private WordsToResolve wordsToResolve(
            List<ContextSlot> targetSlots, Set<String> refinements, Value onlyThese) {

        if (!refinements.contains("only")) {
            return WordsToResolve.EVERYTHING;
        }
        if (onlyThese instanceof IntegerValue(long magnitude)) {
            int startAt = Math.max(1, (int) magnitude);
            if (startAt > targetSlots.size()) {
                return new WordsToResolve(startAt, Set.of(), true);
            }
            return new WordsToResolve(startAt,
                    targetSlots.subList(startAt - 1, targetSlots.size()).stream()
                            .map(ContextSlot::canonical)
                            .collect(Collectors.toUnmodifiableSet()),
                    true);
        }
        if (onlyThese instanceof AnyBlockValue only) {
            return new WordsToResolve(1, only.remaining().stream()
                    .filter(word -> word instanceof WordValue || word instanceof SetWordValue)
                    .map(word -> ((AnyWordValue) word).canonical())
                    .collect(Collectors.toUnmodifiableSet()),
                    true);
        }
        return WordsToResolve.EVERYTHING;
    }
}
