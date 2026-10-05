package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Combining;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.sets.SetOperation;

import java.util.List;
import java.util.Set;

public class DifferenceNative extends DefaultNative {

    private static final Set<Datatype> TAKES_A_DIFFERENCE = Set.of(
            Datatype.BITSET, Datatype.TYPESET, Datatype.STRING, Datatype.MAP,
            Datatype.BLOCK, Datatype.DATE);

    private static final int EVERY_MEMBER_ON_ITS_OWN = 1;

    @Override
    public String name() {
        return "difference";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("first", TAKES_A_DIFFERENCE),
                Parameter.required("second", TAKES_A_DIFFERENCE),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("case", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value first = arguments.getFirst();
            Value second = arguments.get(1);
            if (first instanceof DateValue from && second instanceof DateValue to) {
                return from.spanTo(to);
            }
            int stride = hasNoRecords(first)
                    ? EVERY_MEMBER_ON_ITS_OWN
                    : recordWidth(arguments, refinements);
            return Combining.sets(first, second,
                    SetOperation.named(name()).orElseThrow(),
                    refinements.contains("case"), stride);
        };
    }

    private boolean hasNoRecords(Value first) {
        return first instanceof TypesetValue
                || first instanceof BitsetValue
                || first instanceof MapValue;
    }

    private int recordWidth(List<Value> arguments, Set<String> refinements) {
        return argumentOf("skip", 0, arguments, refinements)
                .filter(IntegerValue.class::isInstance)
                .map(size -> (int) Math.max(EVERY_MEMBER_ON_ITS_OWN,
                        ((IntegerValue) size).magnitude()))
                .orElse(EVERY_MEMBER_ON_ITS_OWN);
    }
}
