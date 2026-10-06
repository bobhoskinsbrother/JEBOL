package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DumpNative extends DefaultNative {

    @Override
    public String name() {
        return "dump";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public Set<String> refinements() {
        return Set.of("fmt");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.getFirst();
    }
}
