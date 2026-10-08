package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class EnbaseNative extends BinaryBaseNative {

    public EnbaseNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "enbase";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value", anyStringOr(Datatype.BINARY, Datatype.INTEGER)),
                Parameter.required("base", Set.of(Datatype.INTEGER)),
                Parameter.belongingTo("part", "limit", anyStringOr(Datatype.BINARY, Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("url", "part", "flat");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int base = aKnownBase(arguments);
            Value value = arguments.getFirst();
            byte[] octets = theOctetsToEncode(value, arguments, refinements);
            String encoded = encodedOrRefused(value, octets, base, refinements.contains("url"));
            return StringValue.of(refinements.contains("flat")
                    ? encoded
                    : encodings.brokenIntoLines(encoded, base, octets.length));
        };
    }

    @Override
    Raised refusalOfAnUnknownBase(List<Value> arguments) {
        return Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(1));
    }

    private byte[] theOctetsToEncode(Value value, List<Value> arguments, Set<String> refinements) {
        if (value instanceof IntegerValue number) {
            byte[] octets = number.asFewOctetsAsHoldIt();
            return howManyWanted(value, arguments, refinements)
                    .map(count -> Arrays.copyOf(octets, Math.clamp(count, 0, octets.length)))
                    .orElse(octets);
        }
        return theUnitsAskedFor(value, arguments, refinements);
    }

    private String encodedOrRefused(Value value, byte[] octets, int base, boolean urlSafe) {
        try {
            return encodings.enbase(octets, base, urlSafe);
        } catch (ArithmeticException tooWideForANumber) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, value);
        }
    }
}
