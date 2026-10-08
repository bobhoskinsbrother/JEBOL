package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

public class EnhexNative extends PercentEncodingNative {

    public EnhexNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "enhex";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value", anyStringOr(Datatype.BINARY)),
                Parameter.belongingTo("escape", "char", Set.of(Datatype.CHAR)),
                Parameter.belongingTo("except", "unescaped", Set.of(Datatype.BITSET)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("escape", "except", "uri");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.getFirst();
            return asTheSameKindAs(value, encodings.percentEncoded(
                    value.asOctets(),
                    leftUnescaped(value, arguments, refinements),
                    escapeCharacterIn(arguments, refinements),
                    refinements.contains("uri")));
        };
    }

    private IntPredicate leftUnescaped(Value value, List<Value> arguments, Set<String> refinements) {
        return argumentOf("except", 0, arguments, refinements)
                .<IntPredicate>map(asked -> octet -> encodings.setHolds((BitsetValue) asked, octet))
                .orElseGet(() -> aLocationKeepsItsSeparators(value)
                        ? encodings::uriKeeps
                        : encodings::uriComponentKeeps);
    }

    private boolean aLocationKeepsItsSeparators(Value value) {
        return value.datatype() == Datatype.FILE || value.datatype() == Datatype.URL;
    }
}
