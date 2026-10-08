package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class UnfilterNative extends PngFilterNative {

    private static final int EACH_LINE_NAMES_ITS_OWN_FILTER = -1;

    private static final int THE_BYTE_NAMING_THE_FILTER = 1;

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
                Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.required("width", aWidth()),
                Parameter.belongingTo("as", "type", aFilterType()),
                Parameter.belongingTo("skip", "bpp", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("as", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
            int width = theWidthIn(arguments.get(1));
            boolean filterGiven = refinements.contains("as");
            requireTheLinesFit(filterGiven ? width : width + THE_BYTE_NAMING_THE_FILTER,
                    data.length, arguments, refinements);
            int filter = argumentOf("as", 0, arguments, refinements)
                    .map(this::theFilterNamedBy)
                    .orElse(EACH_LINE_NAMES_ITS_OWN_FILTER);
            return BinaryValue.ofBytes(encodings.pngUnfiltered(
                    data, width, filter, bytesPerPixelIn(arguments, refinements)));
        };
    }
}
