package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
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
    public String name() {
        return "compress";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("data", Set.of(Datatype.BINARY, Datatype.STRING)),
                Parameter.required("method", Set.of(Datatype.WORD)),
                Parameter.belongingTo("part", "length", aPartLimit()),
                Parameter.belongingTo("level", "lvl", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("part", "level");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String method = aKnownCompression(arguments.get(1));
            Value data = arguments.getFirst();
            int level = argumentOf("level", 0, arguments, refinements)
                    .map(asked -> (int) ((IntegerValue) asked).magnitude())
                    .orElse(Deflater.DEFAULT_COMPRESSION);
            return BinaryValue.ofBytes(encodings.compressed(
                    octetsWithinAnyPart(data, arguments, refinements),
                    method, level));
        };
    }
}
