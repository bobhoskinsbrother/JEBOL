package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.UsageLimit;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LimitUsageNative extends DefaultNative {

    private static final Map<String, UsageLimit> THE_LIMITS_A_FIELD_NAMES = Map.of(
            "eval", UsageLimit.EVALUATIONS,
            "memory", UsageLimit.MEMORY_BYTES);

    @Override
    public String nativeName() {
        return "limit-usage";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("field", Set.of(Datatype.WORD)),
                Parameter.required("limit", Typeset.NUMBER.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Optional.ofNullable(THE_LIMITS_A_FIELD_NAMES.get(((WordValue) arguments.getFirst()).canonical()))
                    .ifPresent(limit -> evaluator.recordLimitAskedFor(limit,
                            (long) Comparison.asDouble(arguments.get(1))));
            return UnsetValue.unset();
        };
    }
}
