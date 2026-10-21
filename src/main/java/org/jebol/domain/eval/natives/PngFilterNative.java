package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class PngFilterNative extends EncodingNative {

    private static final int NO_FILTER = 0;

    private static final int FIRST_NAMED_FILTER = 1;

    private static final int ONE_BYTE_PER_PIXEL = 1;

    protected PngFilterNative(Encodings encodings) {
        super(encodings);
    }

    protected Set<Datatype> acceptWidth() {
        return TypesetValue.NUMBER.members();
    }

    protected Set<Datatype> acceptFilterType() {
        return Set.of(IntegerValue.TYPE, WordValue.TYPE);
    }

    protected int readFilterNamedBy(Value asked) {
        return switch (asked) {
            case IntegerValue(long magnitude) -> Math.clamp((int) magnitude, NO_FILTER, findLastFilter());
            case AnyWordValue word when Encodings.PNG_FILTERS.contains(word.canonical()) ->
                    Encodings.PNG_FILTERS.indexOf(word.canonical()) + FIRST_NAMED_FILTER;
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        };
    }

    private int findLastFilter() {
        return Encodings.PNG_FILTERS.size();
    }

    protected int readWidthIn(Value asked) {
        return (int) Comparison.asDouble(asked);
    }

    protected int readBytesPerPixelIn(List<Value> arguments, Set<String> refinements) {
        return findBytesPerPixelAsked(arguments, refinements)
                .map(asked -> (int) ((IntegerValue) asked).magnitude())
                .orElse(ONE_BYTE_PER_PIXEL);
    }

    private Optional<Value> findBytesPerPixelAsked(List<Value> arguments, Set<String> refinements) {
        return refinementArgument("skip", 0, arguments, refinements);
    }

    protected void requireLinesFit(int lineLength, int length, List<Value> arguments,
                                   Set<String> refinements) {

        if (lineLength <= 1 || lineLength > length) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(1));
        }
        int bytesPerPixel = readBytesPerPixelIn(arguments, refinements);
        if (bytesPerPixel < 1 || bytesPerPixel > lineLength) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    findBytesPerPixelAsked(arguments, refinements).orElseThrow());
        }
    }
}
