package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.Trace;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class TraceNative extends DefaultNative {

    @Override
    public String name() {
        return "trace";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("mode", Set.of(Datatype.INTEGER, Datatype.LOGIC)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("back", "function");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value mode = arguments.getFirst();
            Trace tracing = evaluator.tracing();
            tracing.writeTo(evaluator.output());
            if (refinements.contains("back") && mode instanceof IntegerValue(long lines)) {
                tracing.showTheLastAndStopTracing((int) lines);
                return UnsetValue.unset();
            }
            tracing.keepRatherThanPrint(refinements.contains("back") && mode.isTruthy());
            tracing.level(levelAskedFor(mode), refinements.contains("function"));
            return UnsetValue.unset();
        };
    }

    private int levelAskedFor(Value mode) {
        if (mode instanceof IntegerValue(long level)) {
            return (int) level;
        }
        return mode.isTruthy() ? Trace.EVERYTHING : 0;
    }
}
