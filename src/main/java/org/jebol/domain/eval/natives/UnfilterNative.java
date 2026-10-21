package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class UnfilterNative extends PngFilterNative {

    private static final int EACH_LINE_NAMES_ITS_OWN_FILTER = -1;

    private static final int BYTE_NAMING_FILTER = 1;

    public UnfilterNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "unfilter";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("data", Set.of(BinaryValue.TYPE)),
                Parameter.of("width", acceptAWidth()),
                Parameter.belongTo("as", "type", acceptAFilterType()),
                Parameter.belongTo("skip", "bpp", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("as", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
            int width = readWidthIn(arguments.get(1));
            boolean filterGiven = refinements.contains("as");
            requireLinesFit(filterGiven ? width : width + BYTE_NAMING_FILTER,
                    data.length, arguments, refinements);
            int filter = refinementArgument("as", 0, arguments, refinements)
                    .map(this::readFilterNamedBy)
                    .orElse(EACH_LINE_NAMES_ITS_OWN_FILTER);
            return BinaryValue.ofBytes(encodings.pngUnfilter(
                    data, width, filter, readBytesPerPixelIn(arguments, refinements)));
        };
    }
}
