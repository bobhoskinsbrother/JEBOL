package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
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
                Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.required("width", aWidth()),
                Parameter.required("type", aFilterType()),
                Parameter.belongingTo("skip", "bpp", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
            int width = theWidthIn(arguments.get(1));
            int filter = theFilterNamedBy(arguments.get(2));
            requireTheLinesFit(width, data.length, arguments, refinements);
            return BinaryValue.ofBytes(encodings.pngFiltered(
                    data, width, filter, bytesPerPixelIn(arguments, refinements)));
        };
    }
}
