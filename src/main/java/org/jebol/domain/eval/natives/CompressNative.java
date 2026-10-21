package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
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
                Parameter.belongTo("part", "length", listThePartLimitTypes()),
                Parameter.belongTo("level", "lvl", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "level");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String method = readAKnownCompression(arguments.get(1));
            Value data = arguments.getFirst();
            int level = readTheArgumentOf("level", 0, arguments, refinements)
                    .map(asked -> (int) ((IntegerValue) asked).magnitude())
                    .orElse(Deflater.DEFAULT_COMPRESSION);
            return BinaryValue.ofBytes(encodings.compress(
                    readOctetsWithinAnyPart(data, arguments, refinements),
                    method, level));
        };
    }
}
