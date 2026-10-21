package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
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
                Parameter.of("value", acceptAnyStringOr(BinaryValue.TYPE)),
                Parameter.belongTo("escape", "char", Set.of(CharacterValue.TYPE)),
                Parameter.belongTo("except", "unescaped", Set.of(BitsetValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("escape", "except", "uri");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.getFirst();
            return asSameKindAs(value, encodings.percentEncode(
                    value.asOctets(),
                    chooseWhatIsLeftUnescaped(value, arguments, refinements),
                    escapeCharacterIn(arguments, refinements),
                    refinements.contains("uri")));
        };
    }

    private IntPredicate chooseWhatIsLeftUnescaped(Value value, List<Value> arguments, Set<String> refinements) {
        return refinementArgument("except", 0, arguments, refinements)
                .<IntPredicate>map(asked -> octet -> encodings.setHolds((BitsetValue) asked, octet))
                .orElseGet(() -> keepsItsSeparators(value)
                        ? encodings::isKeptByUri
                        : encodings::isKeptByUriComponent);
    }

    private boolean keepsItsSeparators(Value value) {
        return value instanceof AnyStringValue text && text.isALocation();
    }
}
