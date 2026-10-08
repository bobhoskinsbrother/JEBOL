package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class TypeOfNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "type?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("word");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> refinements.contains("word")
                ? WordValue.of(arguments.getFirst().datatype().literalSpelling())
                : DatatypeValue.of(arguments.getFirst().datatype());
    }
}
