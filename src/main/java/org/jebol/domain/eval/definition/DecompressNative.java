package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DecompressNative extends CompressionNative {

    private static final int AS_MUCH_AS_IT_HOLDS = 0;

    private static final int DEFLATE_BAD_DATA = 1;

    private static final int DEFLATE_SHORT_OUTPUT = 2;

    private static final int DEFLATE_INSUFFICIENT_SPACE = 3;

    private static final int BROTLI_NEEDS_MORE_INPUT = 2;

    private static final int LZW_BAD_DATA = 1;

    private static final int LZMA_INPUT_ENDS_EARLY = 6;

    private static final int CRUSH_COPIES_FROM_BEFORE_THE_START = 0;

    private static final Map<String, Integer> WHAT_EACH_METHOD_REPORTS_OF_DATA_IT_CANNOT_READ = Map.of(
            "zlib", DEFLATE_BAD_DATA,
            "deflate", DEFLATE_BAD_DATA,
            "gzip", DEFLATE_BAD_DATA,
            "br", BROTLI_NEEDS_MORE_INPUT,
            "lzw", LZW_BAD_DATA,
            "lzma", LZMA_INPUT_ENDS_EARLY,
            "crush", CRUSH_COPIES_FROM_BEFORE_THE_START);

    private static final int CRUSH_LENGTH_FIELD = 4;

    public DecompressNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String name() {
        return "decompress";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.required("method", Set.of(Datatype.WORD)),
                Parameter.belongingTo("part", "length", aCountOrPosition()),
                Parameter.belongingTo("size", "bytes", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("part", "size");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String method = aKnownCompression(arguments.get(1));
            Value data = arguments.getFirst();
            int wanted = argumentOf("size", 0, arguments, refinements)
                    .map(this::aSizeAboveNothing)
                    .orElse(AS_MUCH_AS_IT_HOLDS);
            byte[] compressed = octetsWithinAnyPart(data, arguments, refinements);
            if ("crush".equals(method) && compressed.length < CRUSH_LENGTH_FIELD) {
                throw Raised.of(EvaluationFailure.BAD_PRESS, arguments.get(1));
            }
            return BinaryValue.ofBytes(ofTheSizeAsked(
                    decompressedOrRefused(compressed, method, wanted), method, wanted));
        };
    }

    private int aSizeAboveNothing(Value asked) {
        long size = ((IntegerValue) asked).magnitude();
        if (size < 1 || size > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked);
        }
        return (int) size;
    }

    private byte[] decompressedOrRefused(byte[] compressed, String method, int wanted) {
        try {
            return encodings.decompressed(compressed, method, wanted);
        } catch (IllegalArgumentException unreadable) {
            throw Raised.of(EvaluationFailure.BAD_PRESS,
                    IntegerValue.of(WHAT_EACH_METHOD_REPORTS_OF_DATA_IT_CANNOT_READ.get(method)));
        }
    }

    private byte[] ofTheSizeAsked(byte[] whole, String method, int wanted) {
        if (wanted == AS_MUCH_AS_IT_HOLDS || encodings.cutsShortToTheSizeAsked(method)
                || whole.length == wanted) {
            return whole;
        }
        throw Raised.of(EvaluationFailure.BAD_PRESS, IntegerValue.of(whole.length > wanted
                ? DEFLATE_INSUFFICIENT_SPACE
                : DEFLATE_SHORT_OUTPUT));
    }
}
