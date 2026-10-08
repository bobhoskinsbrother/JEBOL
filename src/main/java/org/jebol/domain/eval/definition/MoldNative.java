package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MoldNative extends DefaultNative {

    private static final int THE_BASE_WHEN_THE_SYSTEM_NAMES_NONE = 16;

    @Override
    public String nativeName() {
        return "mold";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only", "all", "flat", "part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Function<Value, String> how = flattenedWhenAsked(refinements,
                    inTheSystemBase(evaluator, moldedAsAsked(arguments.getFirst(), refinements)));
            return argumentOf("part", 0, arguments, refinements)
                    .map(limit -> StringValue.of(Molder.moldWithin(arguments.getFirst(),
                            charactersWithin(((IntegerValue) limit).magnitude()), how)))
                    .orElseGet(() -> StringValue.of(how.apply(arguments.getFirst())));
        };
    }

    private Function<Value, String> moldedAsAsked(Value subject, Set<String> refinements) {
        if (refinements.contains("only") && subject instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK) {
            return value -> Molder.moldOnly((BlockValue) value);
        }
        return refinements.contains("all") ? Molder::moldAll : Molder::mold;
    }

    private Function<Value, String> inTheSystemBase(Evaluator evaluator,
            Function<Value, String> written) {
        return value -> Molder.writingBinariesInBase(binaryBaseNamedBy(evaluator),
                () -> written.apply(value));
    }

    private Function<Value, String> flattenedWhenAsked(Set<String> refinements,
            Function<Value, String> how) {
        return refinements.contains("flat")
                ? value -> Molder.flattened(() -> how.apply(value))
                : how;
    }

    private int charactersWithin(long limit) {
        return Math.clamp(limit, 0, Integer.MAX_VALUE);
    }

    private int binaryBaseNamedBy(Evaluator evaluator) {
        return evaluator.systemContext().slotFor("system").value() instanceof ObjectValue(Context system)
                && system.slotFor("options").value() instanceof ObjectValue(Context options)
                && options.slotFor("binary-base").value() instanceof IntegerValue(long base)
                ? (int) base
                : THE_BASE_WHEN_THE_SYSTEM_NAMES_NONE;
    }
}
