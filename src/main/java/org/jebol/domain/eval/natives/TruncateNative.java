package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TruncateNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "truncate";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("series"),
                Parameter.belongTo("part", "count", listThePartLimitTypes()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case RebolSeries series -> truncate(series,
                    readTheArgumentOf("part", 0, arguments, refinements)
                            .map(count -> ((IntegerValue) count).magnitude()));
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }

    private RebolSeries truncate(RebolSeries series, Optional<Long> keeping) {
        Actions.of(series).orElseThrow().takeOutFrom(1, series.index() - 1);
        RebolSeries kept = series.atIndex(1);
        keeping.ifPresent(wanted -> Actions.of(kept).orElseThrow()
                .takeOutFrom((int) (wanted + 1), (int) (kept.lengthFromHere() - wanted)));
        return kept;
    }
}
