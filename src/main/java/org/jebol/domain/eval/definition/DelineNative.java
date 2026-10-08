package org.jebol.domain.eval.definition;

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
        return List.of(Parameter.required("string", Typeset.ANY_STRING.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("lines");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            StringValue text = (StringValue) arguments.getFirst();
            return refinements.contains("lines")
                    ? BlockValue.block(text.linesDroppingOneTrailingEmptyLine())
                    : text.withOneLineFeedPerEnding();
        };
    }
}
