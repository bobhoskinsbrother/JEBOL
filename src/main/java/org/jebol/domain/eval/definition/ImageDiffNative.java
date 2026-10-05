package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ImageDiffNative extends DefaultNative {

    @Override
    public String name() {
        return "image-diff";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("a", Set.of(Datatype.IMAGE)),
                Parameter.required("b", Set.of(Datatype.IMAGE)),
                Parameter.belongingTo("part", "offset", Set.of(Datatype.PAIR)),
                Parameter.belongingTo("part", "size", Set.of(Datatype.PAIR)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ImageValue first = (ImageValue) arguments.get(0);
            ImageValue second = (ImageValue) arguments.get(1);
            if (!refinements.contains("part")) {
                return DecimalValue.percent(ImageOperations.differenceBetween(first, second));
            }
            PairValue corner = (PairValue) arguments.get(2);
            PairValue size = (PairValue) arguments.get(3);
            return DecimalValue.percent(ImageOperations.differenceOverTheRectangle(
                    first, second, (int) corner.x(), (int) corner.y(),
                    (int) size.x(), (int) size.y()));
        };
    }
}
