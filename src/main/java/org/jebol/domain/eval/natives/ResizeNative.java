package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ResizeNative extends DefaultNative {

    public static final List<String> THE_FILTERS = List.of(
            "Point", "Box", "Triangle", "Hermite", "Hanning", "Hamming",
            "Blackman", "Gaussian", "Quadratic", "Cubic", "Catrom",
            "Mitchell", "Lanczos", "Bessel", "Sinc");

    @Override
    public String nativeName() {
        return "resize";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("image", Set.of(ImageValue.TYPE)),
                Parameter.of("size",
                        Set.of(PairValue.TYPE, PercentValue.TYPE, IntegerValue.TYPE)),
                Parameter.belongTo("filter", "name", Set.of(WordValue.TYPE, IntegerValue.TYPE)),
                Parameter.belongTo("blur", "factor", TypesetValue.NUMBER.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("filter", "blur");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            readTheArgumentOf("filter", 0, arguments, refinements)
                    .ifPresent(this::refuseAFilterTheCatalogueHasNot);
            return resize((ImageValue) arguments.getFirst(), arguments.get(1));
        };
    }

    private Value resize(ImageValue image, Value asked) {
        int wasWide = image.storage().wide();
        int wasHigh = image.storage().high();
        int wide;
        int high;
        if (asked instanceof PairValue(double x, double y)) {
            wide = (int) x;
            high = (int) y;
            if (wide == 0 && high == 0) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
            }
            if (wide == 0) {
                wide = scaleFrom(high, wasWide, wasHigh);
            }
            if (high == 0) {
                high = scaleFrom(wide, wasHigh, wasWide);
            }
        } else if (asked instanceof PercentValue scale) {
            wide = (int) Math.round(wasWide * scale.quantity());
            high = (int) Math.round(wasHigh * scale.quantity());
        } else {
            wide = (int) Math.round(Arithmetic.asMagnitude(asked));
            high = scaleFrom(wide, wasHigh, wasWide);
        }
        if (wide <= 0 || high <= 0) {
            throw Raised.of(EvaluationFailure.NO_CREATE, ImageValue.TYPE);
        }
        return ImageOperations.resize(image, wide, high);
    }

    private int scaleFrom(int given, int toKeep, int against) {
        return against == 0 ? 0 : (given * toKeep) / against;
    }

    private void refuseAFilterTheCatalogueHasNot(Value asked) {
        boolean known = asked instanceof AnyWordValue word
                && THE_FILTERS.stream().anyMatch(word.canonical()::equalsIgnoreCase);
        if (!known) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
    }
}
