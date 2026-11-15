package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DecompressNative extends CompressionNative {

    private static final int AS_MUCH_AS_IT_HOLDS = 0;

    private static final int DEFLATE_BAD_DATA = 1;

    private static final int DEFLATE_SHORT_OUTPUT = 2;

    private static final int DEFLATE_INSUFFICIENT_SPACE = 3;

    public DecompressNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "decompress";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("data", Set.of(BinaryValue.TYPE)),
                Parameter.of("method", Set.of(WordValue.TYPE)),
                Parameter.belongTo("part", "length", acceptCountOrPosition()),
                Parameter.belongTo("size", "bytes", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "size");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String method = readKnownCompression(arguments.get(1), evaluator);
            Value data = arguments.getFirst();
            int wanted = refinementArgument("size", 0, arguments, refinements)
                    .map(this::readSizeAboveNothing)
                    .orElse(AS_MUCH_AS_IT_HOLDS);
            byte[] compressed = readOctetsWithinAnyPart(data, arguments, refinements);
            Optional<CompressionMethod> registered = evaluator.compressionMethods().find(method);
            if (registered.isPresent()) {
                long limit = wanted == AS_MUCH_AS_IT_HOLDS ? CompressionMethod.NOT_ASKED : wanted;
                try {
                    return BinaryValue.ofBytes(registered.get().decompress(compressed, limit));
                } catch (CompressionRefused refused) {
                    throw refuseAsBadPress(refused, arguments.get(1));
                }
            }
            return BinaryValue.ofBytes(ofSizeAsked(decompressOrRefuse(compressed, method), wanted));
        };
    }

    private int readSizeAboveNothing(Value asked) {
        long size = ((IntegerValue) asked).magnitude();
        if (size < 1 || size > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked);
        }
        return (int) size;
    }

    private byte[] decompressOrRefuse(byte[] compressed, String method) {
        try {
            return encodings.decompress(compressed, method);
        } catch (IllegalArgumentException unreadable) {
            throw Raised.of(EvaluationFailure.BAD_PRESS, IntegerValue.of(DEFLATE_BAD_DATA));
        }
    }

    private byte[] ofSizeAsked(byte[] whole, int wanted) {
        if (wanted == AS_MUCH_AS_IT_HOLDS || whole.length == wanted) {
            return whole;
        }
        throw Raised.of(EvaluationFailure.BAD_PRESS, IntegerValue.of(whole.length > wanted
                ? DEFLATE_INSUFFICIENT_SPACE
                : DEFLATE_SHORT_OUTPUT));
    }
}
