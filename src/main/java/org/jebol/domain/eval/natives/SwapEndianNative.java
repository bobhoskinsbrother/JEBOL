package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class SwapEndianNative extends EncodingNative {

    private static final Set<Long> WIDTHS_IT_SWAPS = Set.of(2L, 4L, 8L);

    private static final IntegerValue TWO_BYTES_UNLESS_ASKED = IntegerValue.of(2);

    public SwapEndianNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "swap-endian";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("value", Set.of(BinaryValue.TYPE)),
                Parameter.belongTo("width", "bytes", Set.of(IntegerValue.TYPE)),
                Parameter.belongTo("part", "range", acceptCountOrPosition()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("width", "part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BinaryValue bytes = (BinaryValue) arguments.getFirst();
            int width = readWidthItSwaps(refinementArgument("width", 0, arguments, refinements)
                    .orElse(TWO_BYTES_UNLESS_ASKED));
            byte[] octets = bytes.octetsFromHere();
            int reach = refinementArgument("part", 0, arguments, refinements)
                    .map(asked -> (int) Math.max(0, Math.min(octets.length, bytes.countUpTo(asked))))
                    .orElse(octets.length);
            encodings.swapEndian(octets, reach - reach % width, width);
            for (int at = 0; at < octets.length; at++) {
                bytes.storage().set(bytes.index() + at, octets[at] & 0xFF);
            }
            return bytes;
        };
    }

    private int readWidthItSwaps(Value asked) {
        long width = ((IntegerValue) asked).magnitude();
        if (!WIDTHS_IT_SWAPS.contains(width)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
        return (int) width;
    }
}
