package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class DelineNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "deline";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("string", TypesetValue.ANY_STRING.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("lines");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyStringValue text = (AnyStringValue) arguments.getFirst();
            return refinements.contains("lines")
                    ? BlockValue.of(text.linesDroppingOneTrailingEmptyLine())
                    : text.withOneLineFeedPerEnding();
        };
    }
}
