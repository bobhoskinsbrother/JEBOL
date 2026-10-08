package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DehexNative extends PercentEncodingNative {

    public DehexNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "dehex";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value", anyStringOr(BinaryValue.TYPE)),
                Parameter.belongingTo("escape", "char", Set.of(CharacterValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
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
