package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class FilterNative extends PngFilterNative {

    public FilterNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "filter";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("data", Set.of(BinaryValue.TYPE)),
                Parameter.of("width", acceptAWidth()),
                Parameter.of("type", acceptAFilterType()),
                Parameter.belongTo("skip", "bpp", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
            int width = readWidthIn(arguments.get(1));
            int filter = readFilterNamedBy(arguments.get(2));
            requireLinesFit(width, data.length, arguments, refinements);
            return BinaryValue.ofBytes(encodings.pngFilter(
                    data, width, filter, readBytesPerPixelIn(arguments, refinements)));
        };
    }
}
