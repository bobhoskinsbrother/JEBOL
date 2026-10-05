package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;

import java.util.List;
import java.util.Set;

public class DelineNative extends DefaultNative {

    @Override
    public String name() {
        return "deline";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("string", Typeset.ANY_STRING.members()));
    }

    @Override
    public Set<String> refinements() {
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
