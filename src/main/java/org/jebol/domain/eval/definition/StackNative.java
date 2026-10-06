package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class StackNative extends DefaultNative {

    static final int FRAME_VALUE_UNITS = 8;

    @Override
    public String name() {
        return "stack";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("offset", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("block", "word", "func", "args", "size", "depth", "limit");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int offset = (int) ((IntegerValue) arguments.getFirst()).magnitude();
            List<String> callsOpen = evaluator.everyCallOpenNamedInnermostFirst();
            if (offset < 0 || offset >= callsOpen.size()) {
                return NoneValue.none();
            }
            if (refinements.contains("word")) {
                return WordValue.of(callsOpen.get(offset));
            }
            if (refinements.contains("depth")) {
                return IntegerValue.of(callsOpen.size());
            }
            if (refinements.contains("limit")) {
                return IntegerValue.of(Evaluator.DEFAULT_MAXIMUM_DEPTH);
            }
            if (refinements.contains("size")) {
                return IntegerValue.of((long) callsOpen.size() * FRAME_VALUE_UNITS);
            }
            return BlockValue.block(callsOpen.subList(offset, callsOpen.size()).stream()
                    .<Value>map(WordValue::of)
                    .toList());
        };
    }
}
