package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class StatsNative extends DefaultNative {

    private static final List<String> COUNTS_JEBOL_DOES_NOT_KEEP = List.of(
            "series-made", "series-freed", "series-expanded", "series-recycled",
            "made-blocks", "made-objects", "recycles", "collisions");

    private final long startedAt = System.nanoTime();

    @Override
    public String nativeName() {
        return "stats";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongTo("dump-series", "pool-id", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
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
                return fillInProfile(evaluator);
            }
            return IntegerValue.of(SeriesMemory.countBytesHeld());
        };
    }

    private Value timeSinceStarting() {
        return TimeValue.ofNanoseconds(System.nanoTime() - startedAt);
    }

    private Value fillInProfile(Evaluator evaluator) {
        Value standing = evaluator.systemContext().readValueAt("system", "standard", "stats");
        if (!(standing instanceof ObjectValue profile)) {
            return NoneValue.none();
        }
        Context fields = profile.context();
        setIfPresent(fields, "timer", timeSinceStarting());
        setIfPresent(fields, "evals", IntegerValue.of(evaluator.valuesWalked()));
        setIfPresent(fields, "eval-natives", IntegerValue.of(evaluator.nativesCalled()));
        setIfPresent(fields, "eval-functions", IntegerValue.of(evaluator.functionsCalled()));
        setIfPresent(fields, "series-bytes", IntegerValue.of(SeriesMemory.countBytesHeld()));
        for (String field : COUNTS_JEBOL_DOES_NOT_KEEP) {
            setIfPresent(fields, field, IntegerValue.of(0));
        }
        return profile;
    }

    private void setIfPresent(Context fields, String field, Value written) {
        if (fields.holds(field)) {
            fields.register(field, written);
        }
    }
}
