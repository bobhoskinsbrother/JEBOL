package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ResolveNative extends DefaultNative {

    private static final int WHERE_ONLY_ARRIVES = 2;

    @Override
    public String name() {
        return "resolve";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("target", Typeset.ANY_OBJECT.members()),
                Parameter.required("source", Typeset.ANY_OBJECT.members()),
                Parameter.belongingTo("only", "from", Set.of(Datatype.BLOCK, Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("only", "all", "extend");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Context into = arguments.getFirst().fieldsAsAContext().orElseThrow();
            if (into.isClosedToNewNames()) {
                throw Raised.of(EvaluationFailure.PROTECTED, "resolve");
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
                    into.set(name, value);
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
        if (onlyThese instanceof BlockValue only) {
            return new WordsToResolve(1, only.remaining().stream()
                    .filter(word -> word instanceof WordValue spelled
                            && (spelled.datatype() == Datatype.WORD
                                    || spelled.datatype() == Datatype.SET_WORD))
                    .map(word -> ((WordValue) word).canonical())
                    .collect(Collectors.toUnmodifiableSet()),
                    true);
        }
        return WordsToResolve.EVERYTHING;
    }
}
