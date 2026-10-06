package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.SeriesMemory;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class StatsNative extends DefaultNative {

    private final long startedAt = System.nanoTime();

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.belongingTo("dump-series", "pool-id", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("show", "profile", "timer", "evals", "dump-series");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("dump-series")) {
                return NoneValue.none();
            }
            if (refinements.contains("timer")) {
                return timeSinceStarting();
            }
            if (refinements.contains("evals")) {
                return IntegerValue.of(evaluator.valuesWalked());
            }
            if (refinements.contains("profile")) {
                return filledInProfile(evaluator);
            }
            return IntegerValue.of(SeriesMemory.bytesHeld());
        };
    }

    private Value timeSinceStarting() {
        return TimeValue.ofNanoseconds(System.nanoTime() - startedAt);
    }

    private Value filledInProfile(Evaluator evaluator) {
        Value standing = evaluator.systemContext().valueAt("system", "standard", "stats");
        if (!(standing instanceof ObjectValue profile)) {
            return NoneValue.none();
        }
        Context fields = profile.context();
        setIfPresent(fields, "timer", timeSinceStarting());
        setIfPresent(fields, "evals", IntegerValue.of(evaluator.valuesWalked()));
        setIfPresent(fields, "eval-natives", IntegerValue.of(evaluator.nativesCalled()));
        setIfPresent(fields, "eval-functions", IntegerValue.of(evaluator.functionsCalled()));
        return profile;
    }

    private void setIfPresent(Context fields, String field, Value written) {
        if (fields.holds(field)) {
            fields.set(field, written);
        }
    }
}
