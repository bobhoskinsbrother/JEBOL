package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DumpNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "dump";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("fmt");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.getFirst();
    }
}
