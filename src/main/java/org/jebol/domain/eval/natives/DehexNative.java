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
                Parameter.of("value", acceptAnyStringOr(BinaryValue.TYPE)),
                Parameter.belongTo("escape", "char", Set.of(CharacterValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("escape", "uri");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.getFirst();
            return asSameKindAs(value, encodings.percentDecode(
                    textOf(value),
                    escapeCharacterIn(arguments, refinements),
                    refinements.contains("uri")));
        };
    }
}
