package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MoldNative extends DefaultNative {

    private static final int BASE_WHEN_SYSTEM_NAMES_NONE = 16;

    @Override
    public String nativeName() {
        return "mold";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("value", TypesetValue.ANY_TYPE.members()),
                Parameter.belongTo("part", "limit", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only", "all", "flat", "part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Function<Value, String> how = flattenWhenAsked(refinements,
                    writeInSystemBase(evaluator, moldAsAsked(arguments.getFirst(), refinements)));
            return readTheArgumentOf("part", 0, arguments, refinements)
                    .map(limit -> StringValue.of(Molder.moldWithin(arguments.getFirst(),
                            countCharactersWithin(((IntegerValue) limit).magnitude()), how)))
                    .orElseGet(() -> StringValue.of(how.apply(arguments.getFirst())));
        };
    }

    private Function<Value, String> moldAsAsked(Value subject, Set<String> refinements) {
        if (refinements.contains("only") && subject instanceof BlockValue) {
            return value -> Molder.moldOnly((AnyBlockValue) value);
        }
        return refinements.contains("all") ? Molder::moldAll : Molder::mold;
    }

    private Function<Value, String> writeInSystemBase(Evaluator evaluator,
                                                      Function<Value, String> written) {
        return value -> Molder.writeBinariesInBase(readBinaryBaseNamedBy(evaluator),
                () -> written.apply(value));
    }

    private Function<Value, String> flattenWhenAsked(Set<String> refinements,
                                                     Function<Value, String> how) {
        return refinements.contains("flat")
                ? value -> Molder.flatten(() -> how.apply(value))
                : how;
    }

    private int countCharactersWithin(long limit) {
        return Math.clamp(limit, 0, Integer.MAX_VALUE);
    }

    private int readBinaryBaseNamedBy(Evaluator evaluator) {
        return evaluator.systemContext().findSlotFor("system").value() instanceof ObjectValue(Context system)
                && system.findSlotFor("options").value() instanceof ObjectValue(Context options)
                && options.findSlotFor("binary-base").value() instanceof IntegerValue(long base)
                ? (int) base
                : BASE_WHEN_SYSTEM_NAMES_NONE;
    }
}
