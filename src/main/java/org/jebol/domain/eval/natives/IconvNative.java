package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class IconvNative extends EncodingNative {

    public IconvNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "iconv";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.required("codepage", aCodepage()),
                Parameter.belongingTo("to", "target", aCodepage()));
    }

    private Set<Datatype> aCodepage() {
        return Set.of(Datatype.WORD, Datatype.INTEGER, Datatype.TAG, Datatype.STRING);
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("to");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] octets = ((BinaryValue) arguments.getFirst()).octetsFromHere();
            String text = encodings.textDecodedAs(octets, characterSetFor(arguments.get(1)));
            return argumentOf("to", 0, arguments, refinements)
                    .map(this::characterSetFor)
                    .<Value>map(into -> StandardCharsets.UTF_8.equals(into)
                            ? StringValue.of(text)
                            : BinaryValue.ofBytes(text.getBytes(into)))
                    .orElseGet(() -> StringValue.of(text));
        };
    }

    private Charset characterSetFor(Value asked) {
        String spelling = switch (asked) {
            case AnyWordValue word -> word.canonical();
            case AnyStringValue text -> text.text();
            default -> Molder.form(asked);
        };
        return encodings.charsetNamed(spelling)
                .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, asked));
    }
}
