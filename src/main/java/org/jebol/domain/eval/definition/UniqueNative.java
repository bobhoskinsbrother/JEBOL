package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Combining;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.sets.SetOperation;

import java.util.List;
import java.util.Set;

public class UniqueNative extends DefaultNative {

    private static final int EVERY_MEMBER_ON_ITS_OWN = 1;

    @Override
    public String name() {
        return "unique";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("set1", Set.of(Datatype.BLOCK, Datatype.STRING,
                        Datatype.BITSET, Datatype.TYPESET, Datatype.MAP)),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("case", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int stride = argumentOf("skip", 0, arguments, refinements)
                    .filter(IntegerValue.class::isInstance)
                    .map(size -> (int) Math.max(EVERY_MEMBER_ON_ITS_OWN,
                            ((IntegerValue) size).magnitude()))
                    .orElse(EVERY_MEMBER_ON_ITS_OWN);
            return Combining.sets(arguments.getFirst(), arguments.getFirst(),
                    SetOperation.named("union").orElseThrow(),
                    refinements.contains("case"), stride);
        };
    }
}
