package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.Deflater;

public class CompressNative extends CompressionNative {

    public CompressNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "compress";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("data", Set.of(BinaryValue.TYPE, StringValue.TYPE)),
                Parameter.of("method", Set.of(WordValue.TYPE)),
                Parameter.belongTo("part", "length", listPartLimitTypes()),
                Parameter.belongTo("level", "lvl", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "level");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String method = readKnownCompression(arguments.get(1), evaluator);
            Value data = arguments.getFirst();
            Optional<Value> levelAsked = refinementArgument("level", 0, arguments, refinements);
            byte[] octets = readOctetsWithinAnyPart(data, arguments, refinements);
            Optional<CompressionMethod> registered = evaluator.compressionMethods().find(method);
            if (registered.isPresent()) {
                long level = levelAsked
                        .map(asked -> ((IntegerValue) asked).magnitude() & CompressionMethod.NOT_ASKED)
                        .orElse(CompressionMethod.NOT_ASKED);
                try {
                    return BinaryValue.ofBytes(registered.get().compress(octets, level));
                } catch (CompressionRefused refused) {
                    throw refuseAsBadPress(refused);
                }
            }
            int level = levelAsked
                    .map(asked -> (int) ((IntegerValue) asked).magnitude())
                    .orElse(Deflater.DEFAULT_COMPRESSION);
            return BinaryValue.ofBytes(encodings.compress(octets, method, level));
        };
    }
}
