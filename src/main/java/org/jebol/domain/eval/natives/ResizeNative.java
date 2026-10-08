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
        return List.of(Parameter.required("image", Set.of(Datatype.IMAGE)),
                Parameter.required("size",
                        Set.of(Datatype.PAIR, Datatype.PERCENT, Datatype.INTEGER)),
                Parameter.belongingTo("filter", "name", Set.of(Datatype.WORD, Datatype.INTEGER)),
                Parameter.belongingTo("blur", "factor", Typeset.NUMBER.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("filter", "blur");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            argumentOf("filter", 0, arguments, refinements)
                    .ifPresent(this::refuseAFilterTheCatalogueHasNot);
            return resized((ImageValue) arguments.getFirst(), arguments.get(1));
        };
    }

    private Value resized(ImageValue image, Value asked) {
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
                wide = scaledFrom(high, wasWide, wasHigh);
            }
            if (high == 0) {
                high = scaledFrom(wide, wasHigh, wasWide);
            }
        } else if (asked instanceof DecimalValue(double quantity, Datatype datatype)
                && datatype == Datatype.PERCENT) {
            wide = (int) Math.round(wasWide * quantity);
            high = (int) Math.round(wasHigh * quantity);
        } else {
            wide = (int) Math.round(Arithmetic.asMagnitude(asked));
            high = scaledFrom(wide, wasHigh, wasWide);
        }
        if (wide <= 0 || high <= 0) {
            throw Raised.of(EvaluationFailure.NO_CREATE, DatatypeValue.of(Datatype.IMAGE));
        }
        return ImageOperations.resized(image, wide, high);
    }

    private int scaledFrom(int given, int toKeep, int against) {
        return against == 0 ? 0 : (given * toKeep) / against;
    }

    private void refuseAFilterTheCatalogueHasNot(Value asked) {
        boolean known = asked instanceof WordValue word
                && THE_FILTERS.stream().anyMatch(word.canonical()::equalsIgnoreCase);
        if (!known) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
    }
}
