package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DehexNative extends PercentEncodingNative {

    public DehexNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String name() {
        return "dehex";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("value", anyStringOr(Datatype.BINARY)),
                Parameter.belongingTo("escape", "char", Set.of(Datatype.CHAR)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("escape", "uri");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.getFirst();
            return asTheSameKindAs(value, encodings.percentDecoded(
                    textOf(value),
                    escapeCharacterIn(arguments, refinements),
                    refinements.contains("uri")));
        };
    }
}
