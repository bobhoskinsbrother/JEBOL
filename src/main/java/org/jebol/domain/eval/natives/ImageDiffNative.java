package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ImageDiffNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "image-diff";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("a", Set.of(ImageValue.TYPE)),
                Parameter.of("b", Set.of(ImageValue.TYPE)),
                Parameter.belongTo("part", "offset", Set.of(PairValue.TYPE)),
                Parameter.belongTo("part", "size", Set.of(PairValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ImageValue first = (ImageValue) arguments.get(0);
            ImageValue second = (ImageValue) arguments.get(1);
            if (!refinements.contains("part")) {
                return PercentValue.of(ImageOperations.measureDifferenceBetween(first, second));
            }
            PairValue corner = (PairValue) arguments.get(2);
            PairValue size = (PairValue) arguments.get(3);
            return PercentValue.of(ImageOperations.measureDifferenceOverRectangle(
                    first, second, (int) corner.x(), (int) corner.y(),
                    (int) size.x(), (int) size.y()));
        };
    }
}
