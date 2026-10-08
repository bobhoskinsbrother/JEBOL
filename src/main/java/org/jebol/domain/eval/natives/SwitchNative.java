package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class SwitchNative extends DefaultNative {

    private static final int WHERE_THE_FALLBACK_ARRIVES = 2;

    @Override
    public String nativeName() {
        return "switch";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value"),
                Parameter.required("choices", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("default", "fallback", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case", "default", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value wanted = arguments.get(0);
            List<Value> choices = ((AnyBlockValue) arguments.get(1)).remaining();
            boolean caseSensitive = refinements.contains("case");
            boolean runsThemAll = refinements.contains("all");
            boolean matchedSomething = false;
            Value lastBranchTaken = NoneValue.none();
            for (int at = 0; at < choices.size(); at++) {
                if (isExactlyABlock(choices.get(at))
                        || !matches(choices.get(at), wanted, caseSensitive)) {
                    continue;
                }
                int branchAt = theNextBlockFrom(choices, at);
                if (branchAt >= choices.size()) {
                    break;
                }
                matchedSomething = true;
                lastBranchTaken = evaluator.evaluateOrRaise(
                        (AnyBlockValue) choices.get(branchAt), context);
                if (!runsThemAll) {
                    return lastBranchTaken;
                }
                at = branchAt;
            }
            if (matchedSomething) {
                return lastBranchTaken;
            }
            return theFallbackAskedFor(arguments, refinements) instanceof AnyBlockValue fallback
                    ? evaluator.evaluateOrRaise(fallback, context)
                    : NoneValue.none();
        };
    }

    private boolean matches(Value choice, Value wanted, boolean caseSensitive) {
        return caseSensitive
                ? choice.equals(wanted)
                : Comparison.looselyEqual(choice, wanted);
    }

    private int theNextBlockFrom(List<Value> choices, int from) {
        int at = from;
        while (at < choices.size() && !isExactlyABlock(choices.get(at))) {
            at++;
        }
        return at;
    }

    private boolean isExactlyABlock(Value value) {
        return value instanceof BlockValue;
    }

    private Value theFallbackAskedFor(List<Value> arguments, Set<String> refinements) {
        return refinements.contains("default") && arguments.size() > WHERE_THE_FALLBACK_ARRIVES
                ? arguments.get(WHERE_THE_FALLBACK_ARRIVES)
                : NoneValue.none();
    }
}
